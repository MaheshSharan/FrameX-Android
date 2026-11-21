package com.framex.app.metrics.monitors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.framex.app.metrics.MetricReadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class BatteryState(
        val tempC: Float = 0f,
        val level: Int = -1,
        val voltageMv: Int = 0,
        val currentMa: Float = 0f,
        val powerWatts: Float = 0f,
        val readStatus: MetricReadStatus = MetricReadStatus.Ok
    )

    val batteryState: Flow<BatteryState> = flow {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        while (true) {
            emit(readBatterySnapshot(context, batteryManager))
            delay(BATTERY_POLL_INTERVAL_MS)
        }
    }

    private fun readBatterySnapshot(
        context: Context,
        batteryManager: BatteryManager?
    ): BatteryState {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = rawTemp / 10.0f

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val normalizedLevel = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            -1
        }

        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        // Some OEMs report voltage in mV (e.g., 3850) and a few report in uV or V.
        val voltageMv = when {
            rawVoltage > 100_000 -> rawVoltage / 1000
            rawVoltage in 1..20 -> rawVoltage * 1000
            else -> rawVoltage
        }

        val rawCurrent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val currentMa = if (rawCurrent != Int.MIN_VALUE && rawCurrent != 0) {
            val absVal = abs(rawCurrent.toLong()).toFloat()
            // Normalize uA (> 20,000) vs mA
            if (absVal > 20_000f) absVal / 1000f else absVal
        } else {
            0f
        }

        val powerWatts = if (voltageMv > 0 && currentMa > 0f) {
            ((voltageMv.toFloat() * currentMa) / 1_000_000f).coerceIn(0f, 35f)
        } else {
            0f
        }

        return BatteryState(
            tempC = tempC,
            level = normalizedLevel,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerWatts = powerWatts,
            readStatus = if (normalizedLevel >= 0 || tempC > 0f) MetricReadStatus.Ok else MetricReadStatus.NoData
        )
    }

    companion object {
        private const val BATTERY_POLL_INTERVAL_MS = 2500L
    }
}
