package com.trucdecomptable.minebench.net

import com.trucdecomptable.minebench.core.Hex
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class StratumJob(
    val jobId: String,
    val prevHashHex: String,
    val coinb1: String,
    val coinb2: String,
    val merkleBranches: List<String>,
    val versionHex: String,
    val nbitsHex: String,
    val ntimeHex: String,
    val cleanJobs: Boolean
)

/**
 * Client Stratum minimal : subscribe, authorize, notify, set_difficulty, submit.
 * Un thread lecteur, des écritures synchronisées.
 */
class StratumClient(
    private val host: String,
    private val port: Int,
    private val worker: String,
    private val password: String,
    private val listener: Listener
) {

    interface Listener {
        fun onState(state: String)
        fun onExtranonce(extranonce1: String, extranonce2Size: Int)
        fun onJob(job: StratumJob)
        fun onDifficulty(difficulty: Double)
        fun onShareResult(accepted: Boolean, error: String?)
        fun onLog(line: String)
    }

    private val ids = AtomicLong(1)
    private val pendingShareIds = ConcurrentHashMap<Long, String>()
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var running = false
    private var readerThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        readerThread = Thread { loop() }.apply { name = "stratum"; isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        runCatching { readerThread?.join(2000) }
    }

    private fun loop() {
        var backoff = 5000L
        while (running) {
            try {
                connectAndServe()
                backoff = 5000L
            } catch (t: Throwable) {
                if (!running) return
                listener.onState("déconnecté (${t.message ?: t.javaClass.simpleName})")
                listener.onLog("Erreur: ${t.message ?: t.javaClass.simpleName}")
                runCatching { Thread.sleep(backoff) }
                backoff = (backoff * 2).coerceAtMost(60000L)
            }
        }
    }

    private fun connectAndServe() {
        listener.onState("connexion à $host:$port")
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), 15000)
        socket = s
        val out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
        writer = out
        val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        listener.onState("connecté")
        send(JSONObject().put("id", ids.getAndIncrement())
            .put("method", "mining.subscribe")
            .put("params", JSONArray().put("MineBench/1.0")))
        send(JSONObject().put("id", ids.getAndIncrement())
            .put("method", "mining.authorize")
            .put("params", JSONArray().put(worker).put(password)))
        while (running) {
            val line = input.readLine() ?: throw IllegalStateException("flux fermé par le pool")
            if (line.isBlank()) continue
            handle(line)
        }
    }

    @Synchronized
    private fun send(obj: JSONObject) {
        val w = writer ?: return
        w.write(obj.toString())
        w.write("\n")
        w.flush()
    }

    private fun handle(line: String) {
        val msg = runCatching { JSONObject(line) }.getOrNull() ?: return
        if (msg.has("method")) {
            when (msg.getString("method")) {
                "mining.notify" -> listener.onJob(parseJob(msg.getJSONArray("params")))
                "mining.set_difficulty" -> listener.onDifficulty(msg.getJSONArray("params").getDouble(0))
                "mining.set_extranonce" -> listener.onExtranonce(
                    msg.getJSONArray("params").getString(0),
                    msg.getJSONArray("params").optInt(1, 4)
                )
                "client.get_version" -> send(JSONObject().put("id", msg.opt("id"))
                    .put("result", "MineBench/1.0").put("error", JSONObject.NULL))
                "mining.ping" -> send(JSONObject().put("id", msg.opt("id"))
                    .put("result", "pong").put("error", JSONObject.NULL))
                else -> listener.onLog("méthode inconnue: ${msg.getString("method")}")
            }
            return
        }
        if (msg.has("id") && !msg.isNull("id")) {
            val id = msg.optLong("id", -1L)
            val error = if (msg.isNull("error")) null else msg.opt("error")?.toString()
            val result = msg.opt("result")
            when {
                pendingShareIds.containsKey(id) -> {
                    pendingShareIds.remove(id)
                    listener.onShareResult(msg.optBoolean("result", false), error)
                }
                result is JSONArray && result.length() >= 3 -> {
                    val xn1 = result.optString(1, "")
                    val xn2Size = result.optInt(2, 4)
                    if (xn1.isNotEmpty()) listener.onExtranonce(xn1, xn2Size)
                }
                error != null -> listener.onLog("pool: $error")
            }
        }
    }

    private fun parseJob(params: JSONArray): StratumJob {
        val branches = buildList {
            val arr = params.optJSONArray(4)
            if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
        }
        return StratumJob(
            jobId = params.getString(0),
            prevHashHex = params.getString(1),
            coinb1 = params.getString(2),
            coinb2 = params.getString(3),
            merkleBranches = branches,
            versionHex = params.getString(5),
            nbitsHex = params.getString(6),
            ntimeHex = params.getString(7),
            cleanJobs = params.optBoolean(8, true)
        )
    }

    /** Soumet une share. Le nonce est envoyé en hexadécimal de sa valeur (format standard). */
    fun submit(jobId: String, xn2Hex: String, ntimeHex: String, nonce: Long) {
        val id = ids.getAndIncrement()
        pendingShareIds[id] = jobId
        val nonceHex = String.format("%08x", nonce and 0xffffffffL)
        send(
            JSONObject().put("id", id)
                .put("method", "mining.submit")
                .put("params", JSONArray().put(worker).put(jobId).put(xn2Hex).put(ntimeHex).put(nonceHex))
        )
        listener.onLog("share soumise nonce=$nonceHex job=$jobId")
    }

    companion object {
        fun nonceHexFromDisplay(nonceDisplay: String): String = Hex.encode(Hex.reverse(Hex.decode(nonceDisplay)))
    }
}
