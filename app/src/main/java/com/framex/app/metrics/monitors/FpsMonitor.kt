package com.framex.app.metrics.monitors

import android.content.Context
import com.framex.app.metrics.MetricReadStatus
import com.framex.app.privilege.PrivilegeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class FpsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privilegeManager: PrivilegeManager
) {
    data class FpsState(
        val fps: Int = 0,
        val jankyFrames: Int = 0,
        val totalFrames: Int = 0,
        val maxFrameTimeMs: Int = 0,
        val readStatus: MetricReadStatus = MetricReadStatus.Loading
    )

    val fpsState: Flow<FpsState> = flow {
        var initialized = false
        var lastKnownFps = 0

        while (true) {
            val shizukuReady = privilegeManager.isReady.value

            if (shizukuReady) {
                try {
                    if (!initialized) {
                        privilegeManager.execText(
                            "dumpsys SurfaceFlinger --timestats -clear -enable"
                        )
                        initialized = true
                        emit(FpsState(fps = lastKnownFps, readStatus = MetricReadStatus.Loading))
                    } else {
                        val output = privilegeManager.execText(
                            "dumpsys SurfaceFlinger --timestats -dump"
                        )

                        val parsed = FPS_REGEX
                            .find(output)
                            ?.groupValues?.get(1)
                            ?.toFloatOrNull()
                            ?.roundToInt()
                            ?: 0

                        val janky = JANKY_REGEX
                            .find(output)
                            ?.groupValues?.get(1)
                            ?.toIntOrNull()
                            ?: 0

                        val total = TOTAL_FRAMES_REGEX
                            .find(output)
                            ?.groupValues?.get(1)
                            ?.toIntOrNull()
                            ?: 0

                        if (parsed > 0) lastKnownFps = parsed
                        val frameTimeMs = estimateWorstFrameTimeMs(lastKnownFps, janky)
                        val status = if (output.isBlank()) MetricReadStatus.EmptyOutput else MetricReadStatus.Ok

                        emit(
                            FpsState(
                                fps = lastKnownFps,
                                jankyFrames = janky,
                                totalFrames = total,
                                maxFrameTimeMs = frameTimeMs,
                                readStatus = status
                            )
                        )

                        if (System.currentTimeMillis() % 3000L < 1000L) {
                            privilegeManager.execText(
                                "dumpsys SurfaceFlinger --timestats -clear -enable"
                            )
                        }
                    }
                } catch (e: Exception) {
                    emit(FpsState(fps = lastKnownFps, readStatus = MetricReadStatus.ParseFailed))
                }
            } else {
                initialized = false
                lastKnownFps = 0
                emit(FpsState(fps = 0, readStatus = MetricReadStatus.NoShizuku))
            }
            delay(1000)
        }
    }

    private fun estimateWorstFrameTimeMs(fps: Int, jankyFrames: Int): Int {
        if (fps <= 0) return 0
        val baseMs = 1000f / fps.coerceAtLeast(1)
        return if (jankyFrames > 0) {
            (baseMs * (1f + (jankyFrames.coerceAtMost(10) * 0.35f))).roundToInt().coerceAtMost(250)
        } else {
            baseMs.roundToInt()
        }
    }

    companion object {
        private val FPS_REGEX = Regex("averageFPS\\s*=\\s*([0-9.]+)")
        private val JANKY_REGEX = Regex("missedFrames\\s*=\\s*([0-9]+)")
        private val TOTAL_FRAMES_REGEX = Regex("totalFrames\\s*=\\s*([0-9]+)")
    }
}
