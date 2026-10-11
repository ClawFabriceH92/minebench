package com.trucdecomptable.minebench.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {

    fun hashrate(h: Double): String {
        val units = listOf("H/s", "kH/s", "MH/s", "GH/s", "TH/s", "PH/s")
        var v = h
        var i = 0
        while (v >= 1000.0 && i < units.size - 1) {
            v /= 1000.0
            i++
        }
        return String.format(Locale.FRANCE, "%.2f %s", v, units[i])
    }

    fun eur(v: Double): String = when {
        v == 0.0 -> "0 €"
        v.isInfinite() -> "infini"
        v < 0.01 -> String.format(Locale.FRANCE, "%.8f €", v)
        else -> String.format(Locale.FRANCE, "%.2f €", v)
    }

    fun duration(days: Double): String {
        if (days.isInfinite()) return "jamais"
        return when {
            days < 1 -> String.format(Locale.FRANCE, "%.1f heures", days * 24)
            days < 400 -> String.format(Locale.FRANCE, "%.1f jours", days)
            days < 36500 -> String.format(Locale.FRANCE, "%.0f ans", days / 365.25)
            else -> String.format(Locale.FRANCE, "%.2e ans", days / 365.25)
        }
    }

    fun temp(v: Double?): String = v?.let { String.format(Locale.FRANCE, "%.1f °C", it) } ?: "n/d"

    fun time(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(ms))

    fun share(v: Double): String = String.format(Locale.FRANCE, "%.3e", v)

    /** Taille en octets -> o / Ko / Mo / Go (base 1000). */
    fun size(bytes: Long): String {
        val units = listOf("o", "Ko", "Mo", "Go")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1000.0 && i < units.size - 1) {
            v /= 1000.0
            i++
        }
        return String.format(Locale.FRANCE, if (i == 0) "%.0f %s" else "%.2f %s", v, units[i])
    }

    /** Durée écoulée (en secondes) -> "il y a X s/min/h/j". */
    fun ago(elapsedSec: Long): String {
        val s = if (elapsedSec < 0) 0 else elapsedSec
        return when {
            s < 60 -> "il y a $s s"
            s < 3600 -> "il y a ${s / 60} min"
            s < 86400 -> "il y a ${s / 3600} h"
            else -> "il y a ${s / 86400} j"
        }
    }
}
