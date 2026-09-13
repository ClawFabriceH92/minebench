package com.trucdecomptable.minebench.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

/** Moteur de hachage comparé dans le benchmark. */
enum class HashEngine(val label: String) {
    MD("MessageDigest (natif)"),
    PURE("Kotlin pur (JIT)")
}

data class PalierSpec(
    val engine: HashEngine,
    val threads: Int,
    val dutyPercent: Int,
    val seconds: Int
)

data class PalierMeasure(
    val spec: PalierSpec,
    val hashes: Long,
    val elapsedMs: Long,
    val hashrate: Double,
    val tempStartC: Double?,
    val tempEndC: Double?,
    val thermalStart: Int,
    val thermalEnd: Int
)

/**
 * Boucle de hachage partagée : découpe le travail en petits lots puis dort
 * le temps nécessaire pour respecter le duty cycle demandé (limitation CPU).
 */
class HashLoop(
    private val engine: HashEngine,
    private val dutyProvider: () -> Int
) {
    private val chunk = 1024

    fun run(
        threadIndex: Int,
        durationMs: Long,
        counter: AtomicLong,
        onHash: ((nonce: Long, hash: ByteArray) -> Unit)? = null,
        headerProvider: () -> ByteArray?,
        nonceBase: () -> Long = { 0L }
    ) {
        val md = if (engine == HashEngine.MD) Sha256Md() else null
        val pure = if (engine == HashEngine.PURE) Sha256Pure() else null
        val out = ByteArray(32)
        val deadline = System.nanoTime() + durationMs * 1_000_000L
        var header = headerProvider() ?: return
        var nonce = nonceBase() + threadIndex.toLong()
        var step = 0L
        while (System.nanoTime() < deadline) {
            val t0 = System.nanoTime()
            var i = 0
            while (i < chunk) {
                Hex.writeUInt32LE(header, 76, nonce)
                if (md != null) md.doubleHash(header, 0, Block.HEADER_SIZE, out)
                else pure!!.doubleHash(header, 0, Block.HEADER_SIZE, out)
                onHash?.invoke(nonce, out)
                nonce += 1
                i += 1
            }
            counter.addAndGet(chunk.toLong())
            step += chunk
            val duty = dutyProvider()
            if (duty < 100) {
                val workNs = System.nanoTime() - t0
                val sleepNs = workNs * (100 - duty) / duty
                if (sleepNs > 0) LockSupport.parkNanos(sleepNs)
            }
            if (step >= 8192) {
                val next = headerProvider()
                if (next == null) return
                if (next !== header) {
                    header = next
                    nonce = nonceBase() + threadIndex.toLong()
                }
                step = 0
            }
        }
    }
}

/** Exécute un palier de benchmark et mesure débit + températures. */
class BenchmarkRunner(private val sensors: Sensors) {

    fun run(spec: PalierSpec, onTick: (elapsedMs: Long) -> Unit = {}): PalierMeasure {
        val counter = AtomicLong(0)
        val durationMs = spec.seconds * 1000L
        val loop = HashLoop(spec.engine) { spec.dutyPercent }
        val placeholder = Block.header(0x20000000L, ByteArray(32), ByteArray(32), 0L, 0x1d00ffffL, 0L)
        val headerRef = AtomicReference(placeholder)
        val tempStart = sensors.batteryTempC()
        val thermalStart = sensors.thermalStatus()
        val started = System.nanoTime()
        val threads = (0 until spec.threads).map { idx ->
            Thread { loop.run(idx, durationMs, counter, headerProvider = { headerRef.get() }) }
                .apply { name = "bench-$idx"; start() }
        }
        val watcher = Thread {
            try {
                while (threads.any { it.isAlive }) {
                    onTick((System.nanoTime() - started) / 1_000_000L)
                    Thread.sleep(250)
                }
            } catch (e: InterruptedException) {
                // fin normale du moniteur
            } catch (t: Throwable) {
                // une exception ne doit jamais tuer le process
            }
        }
        watcher.isDaemon = true
        watcher.start()
        threads.forEach { it.join() }
        watcher.interrupt()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        val hashes = counter.get()
        return PalierMeasure(
            spec = spec,
            hashes = hashes,
            elapsedMs = elapsedMs,
            hashrate = if (elapsedMs > 0) hashes * 1000.0 / elapsedMs else 0.0,
            tempStartC = tempStart,
            tempEndC = sensors.batteryTempC(),
            thermalStart = thermalStart,
            thermalEnd = sensors.thermalStatus()
        )
    }
}

data class MiningWork(
    val jobId: String,
    val header: ByteArray,
    val shareTarget: Target,
    val blockTarget: Target,
    val xn2Hex: String,
    val ntimeHex: String
)

data class ShareFound(
    val jobId: String,
    val xn2Hex: String,
    val ntimeHex: String,
    val nonce: Long,
    val hashDisplay: String,
    val isBlock: Boolean
)

/** Moteur de minage réel : threads, duty cycle, détection de share et de bloc. */
class Miner(
    private val engine: HashEngine,
    private val threads: Int,
    dutyPercent: Int
) {
    @Volatile private var duty: Int = dutyPercent

    fun setDuty(percent: Int) {
        duty = percent.coerceIn(5, 100)
    }

    private val running = AtomicBoolean(false)
    private val counter = AtomicLong(0)
    private val workRef = AtomicReference<MiningWork?>(null)
    private var workers: List<Thread> = emptyList()

    fun isRunning(): Boolean = running.get()

    fun updateWork(work: MiningWork) {
        workRef.set(work)
    }

    fun start(onShare: (ShareFound) -> Unit, onBlock: (ShareFound) -> Unit) {
        if (running.getAndSet(true)) return
        counter.set(0)
        val loop = HashLoop(engine) { duty }
        workers = (0 until threads).map { idx ->
            Thread {
              try {
                var localWork: MiningWork? = null
                var iteration = 0L
                while (running.get()) {
                    val work = workRef.get()
                    if (work == null) {
                        Thread.sleep(100)
                        continue
                    }
                    if (localWork !== work) {
                        localWork = work
                        iteration = 0
                    }
                    loop.run(
                        threadIndex = idx,
                        durationMs = 500,
                        counter = counter,
                        headerProvider = { workRef.get()?.header },
                        nonceBase = { iteration * 0x08000000L },
                        onHash = { nonce: Long, hash: ByteArray ->
                        if (work.shareTarget.meets(hash)) {
                            val isBlock = work.blockTarget.meets(hash)
                            val found = ShareFound(
                                jobId = work.jobId,
                                xn2Hex = work.xn2Hex,
                                ntimeHex = work.ntimeHex,
                                nonce = nonce,
                                hashDisplay = Block.displayHash(hash),
                                isBlock = isBlock
                            )
                            if (isBlock) onBlock(found) else onShare(found)
                        }
                        }
                    )
                    iteration += 1
                }
              } catch (e: InterruptedException) {
                // arrêt demandé
              } catch (t: Throwable) {
                // on ne laisse pas une erreur tuer l'application
              }
            }.apply { name = "mine-$idx"; start() }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        workers.forEach { it.interrupt() }
        workers.forEach { runCatching { it.join(2000) } }
        workers = emptyList()
    }

    fun hashes(): Long = counter.get()

    fun resetCounter() = counter.set(0)
}
