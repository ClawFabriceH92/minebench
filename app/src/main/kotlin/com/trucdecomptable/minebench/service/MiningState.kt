package com.trucdecomptable.minebench.service

import com.trucdecomptable.minebench.core.Format
import kotlinx.coroutines.flow.MutableStateFlow

data class MiningConfig(
    val host: String = "solo.ckpool.org",
    val port: Int = 3333,
    val worker: String = "",
    val password: String = "x",
    val threads: Int = 2,
    val dutyPercent: Int = 50,
    val maxTempC: Double = 42.0,
    val minBatteryPercent: Int = 20,
    val requireCharging: Boolean = true,
    val kwhPriceEur: Double = 0.21
)

/** État partagé entre le service de minage et l'interface. */
object MiningState {

    val running = MutableStateFlow(false)
    val status = MutableStateFlow("arrêté")
    val pool = MutableStateFlow("")
    val difficulty = MutableStateFlow(1.0)
    val jobId = MutableStateFlow("-")
    val hashrate = MutableStateFlow(0.0)
    val sharesAccepted = MutableStateFlow(0)
    val sharesRejected = MutableStateFlow(0)
    val blockFound = MutableStateFlow(false)
    val uptimeMs = MutableStateFlow(0L)
    val batteryTempC = MutableStateFlow<Double?>(null)
    val batteryPercent = MutableStateFlow<Int?>(null)
    val thermalStatus = MutableStateFlow(0)
    val dutyEffective = MutableStateFlow(100)
    val log = MutableStateFlow<List<String>>(emptyList())

    fun reset() {
        sharesAccepted.value = 0
        sharesRejected.value = 0
        blockFound.value = false
        uptimeMs.value = 0L
        log.value = emptyList()
    }

    fun addLog(message: String) {
        val line = Format.time(System.currentTimeMillis()) + "  " + message
        log.value = (listOf(line) + log.value).take(80)
    }
}
