package com.trucdecomptable.minebench.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trucdecomptable.minebench.MainActivity
import com.trucdecomptable.minebench.R
import com.trucdecomptable.minebench.android.AndroidSensors
import com.trucdecomptable.minebench.core.Format
import com.trucdecomptable.minebench.core.HashEngine
import com.trucdecomptable.minebench.core.Miner
import com.trucdecomptable.minebench.core.ShareFound
import com.trucdecomptable.minebench.core.ThermalStatus
import com.trucdecomptable.minebench.net.JobBuilder
import com.trucdecomptable.minebench.net.StratumClient
import com.trucdecomptable.minebench.net.StratumJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service de minage : client Stratum + moteur de hachage + garde-fous
 * (température batterie, statut thermique, niveau de charge).
 */
class MiningService : Service() {

    companion object {
        const val ACTION_START = "com.trucdecomptable.minebench.action.START"
        const val ACTION_STOP = "com.trucdecomptable.minebench.action.STOP"
        private const val CHANNEL_ID = "minage"
        private const val NOTIFICATION_ID = 4210

        fun start(context: Context, config: MiningConfig) {
            val intent = Intent(context, MiningService::class.java).apply {
                action = ACTION_START
                putExtra("host", config.host)
                putExtra("port", config.port)
                putExtra("worker", config.worker)
                putExtra("password", config.password)
                putExtra("threads", config.threads)
                putExtra("duty", config.dutyPercent)
                putExtra("maxTemp", config.maxTempC)
                putExtra("minBattery", config.minBatteryPercent)
                putExtra("requireCharging", config.requireCharging)
                putExtra("kwhPrice", config.kwhPriceEur)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MiningService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: Job? = null
    private var client: StratumClient? = null
    private var miner: Miner? = null
    private var sensors: AndroidSensors? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var config = MiningConfig()
    private var extranonce1 = ""
    private var extranonce2Size = 4
    private var shareDifficulty = 1.0
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession("arrêt demandé")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                config = MiningConfig(
                    host = intent.getStringExtra("host") ?: "solo.ckpool.org",
                    port = intent.getIntExtra("port", 3333),
                    worker = intent.getStringExtra("worker") ?: "",
                    password = intent.getStringExtra("password") ?: "x",
                    threads = intent.getIntExtra("threads", 2),
                    dutyPercent = intent.getIntExtra("duty", 50),
                    maxTempC = intent.getDoubleExtra("maxTemp", 42.0),
                    minBatteryPercent = intent.getIntExtra("minBattery", 20),
                    requireCharging = intent.getBooleanExtra("requireCharging", true),
                    kwhPriceEur = intent.getDoubleExtra("kwhPrice", 0.21)
                )
                startForeground(NOTIFICATION_ID, buildNotification("démarrage…"))
                startSession()
            }
        }
        return START_STICKY
    }

    private fun startSession() {
        if (session?.isActive == true) return
        MiningState.reset()
        MiningState.running.value = true
        MiningState.pool.value = "${config.host}:${config.port}"
        MiningState.dutyEffective.value = config.dutyPercent
        startedAt = System.currentTimeMillis()
        sensors = AndroidSensors(this)

        if (config.worker.isBlank()) {
            MiningState.addLog("Refusé : renseigne une adresse BTC en worker (adresse.nom)")
            MiningState.status.value = "worker manquant"
            MiningState.running.value = false
            stopSelf()
            return
        }
        if (config.requireCharging && sensors?.isCharging() == false) {
            MiningState.addLog("Refusé : l'appareil n'est pas en charge")
            MiningState.status.value = "non branché"
            MiningState.running.value = false
            stopSelf()
            return
        }

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "minebench:mining")
            .also { it.acquire(6 * 60 * 60 * 1000L) }

        val m = Miner(HashEngine.MD, config.threads, config.dutyPercent)
        miner = m
        m.start(
            onShare = { share ->
                MiningState.addLog("share trouvée nonce=${String.format("%08x", share.nonce)}")
                client?.submit(share.jobId, share.xn2Hex, share.ntimeHex, share.nonce)
            },
            onBlock = { share: ShareFound ->
                MiningState.blockFound.value = true
                MiningState.addLog("BLOC TROUVÉ ! hash=${share.hashDisplay}")
                client?.submit(share.jobId, share.xn2Hex, share.ntimeHex, share.nonce)
            }
        )

        val c = StratumClient(
            host = config.host,
            port = config.port,
            worker = config.worker,
            password = config.password,
            listener = object : StratumClient.Listener {
                override fun onState(state: String) {
                    MiningState.status.value = state
                    MiningState.addLog(state)
                }

                override fun onExtranonce(extranonce1Hex: String, extranonce2SizeBytes: Int) {
                    extranonce1 = extranonce1Hex
                    extranonce2Size = extranonce2SizeBytes
                    MiningState.addLog("extranonce1=$extranonce1Hex (xn2 sur $extranonce2SizeBytes octets)")
                }

                override fun onJob(job: StratumJob) {
                    MiningState.jobId.value = job.jobId
                    val xn2 = "0".repeat(extranonce2Size * 2)
                    runCatching {
                        miner?.updateWork(JobBuilder.build(job, extranonce1, xn2, shareDifficulty))
                    }.onFailure { MiningState.addLog("job invalide: ${it.message}") }
                }

                override fun onDifficulty(difficulty: Double) {
                    shareDifficulty = difficulty
                    MiningState.difficulty.value = difficulty
                    MiningState.addLog("difficulté pool = $difficulty")
                }

                override fun onShareResult(accepted: Boolean, error: String?) {
                    if (accepted) {
                        MiningState.sharesAccepted.value += 1
                        MiningState.addLog("share ACCEPTÉE")
                    } else {
                        MiningState.sharesRejected.value += 1
                        MiningState.addLog("share REFUSÉE ${error ?: ""}")
                    }
                }

                override fun onLog(line: String) {
                    MiningState.addLog(line)
                }
            }
        )
        client = c
        c.start()

        session = scope.launch {
            var lastHashes = 0L
            while (isActive && MiningState.running.value) {
                delay(1000)
                val hashes = m.hashes()
                MiningState.hashrate.value = (hashes - lastHashes).toDouble()
                lastHashes = hashes
                MiningState.uptimeMs.value = System.currentTimeMillis() - startedAt
                applyGuards(m)
                updateNotification()
            }
        }
    }

    /** Garde-fous : température, statut thermique, batterie. */
    private fun applyGuards(m: Miner) {
        val s = sensors ?: return
        val temp = s.batteryTempC()
        val thermal = s.thermalStatus()
        val battery = s.batteryPercent()
        MiningState.batteryTempC.value = temp
        MiningState.batteryPercent.value = battery
        MiningState.thermalStatus.value = thermal

        if (temp != null && temp >= config.maxTempC + 3.0) {
            MiningState.addLog("ARRÊT : batterie à ${Format.temp(temp)} (limite ${Format.temp(config.maxTempC)})")
            stopSession("arrêt thermique")
            return
        }
        if (thermal >= 3) {
            MiningState.addLog("ARRÊT : statut thermique ${ThermalStatus.label(thermal)}")
            stopSession("arrêt thermique système")
            return
        }
        if (battery != null && battery <= config.minBatteryPercent) {
            MiningState.addLog("ARRÊT : batterie à $battery %")
            stopSession("batterie faible")
            return
        }
        if (config.requireCharging && !s.isCharging()) {
            MiningState.addLog("ARRÊT : appareil débranché")
            stopSession("débranché")
            return
        }
        val target = when {
            thermal == 2 || (temp != null && temp >= config.maxTempC) -> maxOf(10, MiningState.dutyEffective.value / 2)
            else -> config.dutyPercent
        }
        if (target != MiningState.dutyEffective.value) {
            MiningState.dutyEffective.value = target
            m.setDuty(target)
            MiningState.addLog("charge réduite à $target % (temp ${Format.temp(temp)})")
        }
    }

    private fun stopSession(reason: String) {
        session?.cancel()
        session = null
        client?.stop()
        client = null
        miner?.stop()
        miner = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        MiningState.running.value = false
        MiningState.status.value = reason
        MiningState.addLog("session terminée: $reason")
        with(NotificationManagerCompatShim(this)) { cancel() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        client?.stop()
        miner?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, MiningService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MineBench : minage en cours")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Arrêter", stop)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = "hashrate ${Format.hashrate(MiningState.hashrate.value)} · " +
            "shares ${MiningState.sharesAccepted.value}/${MiningState.sharesRejected.value} · " +
            "charge ${MiningState.dutyEffective.value} % · ${Format.temp(MiningState.batteryTempC.value)}"
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    /** Petit shim pour annuler la notification sans dépendre de NotificationManagerCompat. */
    private class NotificationManagerCompatShim(private val service: Service) {
        fun cancel() {
            val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        }
    }
}
