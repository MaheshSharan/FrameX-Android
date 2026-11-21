package com.framex.app.metrics.monitors

import android.net.TrafficStats
import com.framex.app.metrics.MetricReadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor() {
    data class NetworkState(
        val rxSpeedKbps: Float,
        val txSpeedKbps: Float,
        val readStatus: MetricReadStatus = MetricReadStatus.Ok
    )

    val networkSpeed: Flow<NetworkState> = flow {
        if (TrafficStats.getTotalRxBytes() == TrafficStats.UNSUPPORTED.toLong()) {
            while (true) {
                emit(NetworkState(0f, 0f, MetricReadStatus.HardwareUnsupported))
                delay(2000)
            }
        }
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var previousTime = System.currentTimeMillis()

        while (true) {
            delay(1000)
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()
            val currentTime = System.currentTimeMillis()

            val timeDiffSec = (currentTime - previousTime) / 1000.0f
            if (timeDiffSec > 0 && currentRx >= 0 && currentTx >= 0) {
                val rxSpeedKbps = ((currentRx - previousRx) / 1024.0f) / timeDiffSec
                val txSpeedKbps = ((currentTx - previousTx) / 1024.0f) / timeDiffSec
                emit(
                    NetworkState(
                        rxSpeedKbps = rxSpeedKbps.coerceAtLeast(0f),
                        txSpeedKbps = txSpeedKbps.coerceAtLeast(0f),
                        readStatus = MetricReadStatus.Ok
                    )
                )
            } else {
                emit(NetworkState(0f, 0f, MetricReadStatus.NoData))
            }

            previousRx = currentRx
            previousTx = currentTx
            previousTime = currentTime
        }
    }
}
