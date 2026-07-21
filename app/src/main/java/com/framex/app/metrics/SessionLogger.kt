package com.framex.app.metrics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records MetricsEngine's snapshot history to a CSV file that the user can share —
 * e.g. to attach to a bug report, or to visually correlate exactly which metric
 * (thermal status, a CPU frequency dip, RAM pressure, etc.) lines up with an FPS
 * drop at a specific second during a gaming session.
 *
 * This is intentionally decoupled from MetricsEngine's *display* state (the overlay
 * toggle set) — recording captures the FULL snapshot history regardless of which
 * modules the user has visible on-screen, since the cause of a drop might be a
 * metric they didn't bother enabling in the overlay.
 */
@Singleton
class SessionLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metricsEngine: MetricsEngine
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Snapshot index (into metricsEngine.snapshotHistory) marking where this
    // recording session started, so exporting doesn't include earlier history
    // that predates the user pressing "Start".
    private var _recordingStartIndex = 0
    val recordingStartIndex: Int get() = _recordingStartIndex

    private val _lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportedFile: StateFlow<File?> = _lastExportedFile.asStateFlow()

    // Recording needs every diagnostic signal on the timeline, not just whatever the
    // user happened to enable on their overlay — so it force-enables the full set via
    // MetricsEngine's screen-override mechanism (additive, never touches the user's
    // saved overlay toggles) for the duration of the recording only.
    private val recordingModules = setOf("thermal", "temp", "cpu", "cpu_cluster", "ram", "top_process")

    fun startRecording() {
        _recordingStartIndex = metricsEngine.snapshotHistory.value.size
        metricsEngine.setScreenOverrideModules(recordingModules, requesterKey = "session_logger")
        _isRecording.value = true
    }

    fun stopRecording() {
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "session_logger")
        _isRecording.value = false
    }

    /** Writes the recorded window (start → now) to a CSV in app-private storage
     *  and returns a content:// URI ready to hand to a share Intent. Returns null
     *  if there's nothing to export. */
    suspend fun exportToFile(): File? = withContext(Dispatchers.IO) {
        val all = metricsEngine.snapshotHistory.value
        val window = if (_recordingStartIndex in all.indices) all.subList(_recordingStartIndex, all.size) else all
        if (window.isEmpty()) return@withContext null

        val dir = File(context.filesDir, "session_logs").apply { mkdirs() }
        val name = "framex_session_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.csv"
        val file = File(dir, name)

        file.bufferedWriter().use { writer ->
            writer.write(
                "timestamp,fps,janky_frames,cpu_mhz,cpu_percent,cpu_cluster_eff_mhz,cpu_cluster_perf_mhz,cpu_cluster_ultra_mhz," +
                    "ram_used_gb,ram_total_gb,battery_temp_c,network_rx_kbps,network_tx_kbps,ping_ms," +
                    "thermal_cpu_c,thermal_gpu_c,thermal_npu_c,thermal_skin_c,thermal_status," +
                    "top_process,top_process_cpu_percent\n"
            )
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            for (snap in window) {
                val s = snap.state
                writer.write(
                    "${fmt.format(snap.timestampMs)},${s.fps},${s.jankyFrames},${s.cpuMhz},${s.cpuPercentage ?: ""}," +
                        "${s.cpuClusterEffMhz},${s.cpuClusterPerfMhz},${s.cpuClusterUltraMhz}," +
                        "${s.ramUsedGb},${s.ramTotalGb},${s.batteryTempC},${s.networkRxKbps},${s.networkTxKbps},${s.pingMs}," +
                        "${s.thermalCpuC},${s.thermalGpuC},${s.thermalNpuC},${s.thermalSkinC},${s.thermalStatus}," +
                        "${s.topProcessName ?: ""},${s.topProcessCpuPercent}\n"
                )
            }
        }

        _lastExportedFile.value = file
        file
    }

    /** Builds a share Intent for the given file via FileProvider. Caller starts it
     *  with startActivity(Intent.createChooser(...)). */
    fun buildShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "FrameX session log — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Number of samples currently held in the active recording window — lets the UI
     *  show a live "N seconds recorded" counter without exporting. */
    fun currentRecordingCount(): Int {
        if (!_isRecording.value) return 0
        val all = metricsEngine.snapshotHistory.value
        return (all.size - _recordingStartIndex).coerceAtLeast(0)
    }
}
