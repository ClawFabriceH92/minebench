package com.trucdecomptable.minebench.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.trucdecomptable.minebench.core.Sensors

/** Implémentation Android : batterie, charge, température, statut thermique. */
class AndroidSensors(private val context: Context) : Sensors {

    private fun batteryIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    override fun batteryTempC(): Double? {
        val i = batteryIntent() ?: return null
        val tenths = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == Int.MIN_VALUE) null else tenths / 10.0
    }

    override fun thermalStatus(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return 0
        return pm.currentThermalStatus
    }

    override fun isCharging(): Boolean {
        val i = batteryIntent() ?: return false
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun batteryPercent(): Int? {
        val i = batteryIntent() ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return level * 100 / scale
    }

    override fun powerWatts(): Double? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val microAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val i = batteryIntent() ?: return null
        val millivolts = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (microAmps == Int.MIN_VALUE || microAmps == 0 || millivolts <= 0) return null
        return Math.abs(microAmps) / 1_000_000.0 * (millivolts / 1000.0)
    }
}
