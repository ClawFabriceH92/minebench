package com.trucdecomptable.minebench.core

/** Accès capteurs / état système, abstrait pour rester testable hors Android. */
interface Sensors {
    fun batteryTempC(): Double?
    fun thermalStatus(): Int
    fun isCharging(): Boolean
    fun batteryPercent(): Int?
    fun powerWatts(): Double?
}

class NullSensors : Sensors {
    override fun batteryTempC(): Double? = null
    override fun thermalStatus(): Int = 0
    override fun isCharging(): Boolean = false
    override fun batteryPercent(): Int? = null
    override fun powerWatts(): Double? = null
}

object ThermalStatus {
    fun label(status: Int): String = when (status) {
        0 -> "nominal"
        1 -> "léger"
        2 -> "modéré"
        3 -> "sévère"
        4 -> "critique"
        5 -> "urgence"
        6 -> "arrêt"
        else -> "inconnu ($status)"
    }
}
