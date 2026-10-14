package com.framex.app.metrics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import com.framex.app.device.DeviceDiagnosticManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selectable telemetry metrics for per-app session profiling and graphing.
 */
enum class RecordableMetric(
    val id: String,
    val label: String,
    val engineModules: Set<String>
) {
    FPS("fps", "FPS & Stability", setOf("fps")),
    FRAME_TIME("frame_time", "Frame Time (ms)", setOf("fps")),
    TEMPERATURES("temperatures", "Temperatures (°C)", setOf("thermal", "temp")),
    CPU_USAGE("cpu_usage", "CPU Usage (%)", setOf("cpu", "cpu_cluster")),
    CPU_FREQUENCY("cpu_frequency", "CPU Frequency (MHz)", setOf("cpu", "cpu_cluster")),
    POWER("power", "Power (W) & Battery %", setOf("power", "battery_level", "temp")),
    RAM("ram", "RAM Usage (GB)", setOf("ram"));

    companion object {
        val DEFAULT_SET: Set<RecordableMetric> = entries.toSet()
    }
}

@Immutable
data class SessionRecordingConfig(
    val targetPackageName: String? = null,
    val targetAppLabel: String = "System-Wide",
    val targetAppVersion: String = "",
    val selectedMetrics: Set<RecordableMetric> = RecordableMetric.DEFAULT_SET
)

@Immutable
data class SessionMetadataHeader(
    val deviceModel: String,
    val socIdentifier: String,
    val androidVersion: String,
    val kernelVersion: String,
    val maxRefreshRateHz: Int,
    val targetAppLabel: String,
    val targetPackageName: String,
    val targetAppVersion: String,
    val startTimeFormatted: String,
    val selectedMetrics: Set<RecordableMetric>
)

/**
 * Production-grade, non-blocking Session Logger.
 *
 * Uses a bounded Coroutine [Channel] (capacity = 512, [BufferOverflow.DROP_OLDEST])
 * and streams snapshots to disk in 5-second background chunks on [Dispatchers.IO] with
 * crash-safe hardware/app metadata headers. Zero disk I/O ever touches the main thread.
 */
@Singleton
class SessionLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metricsEngine: MetricsEngine,
    private val deviceDiagnosticManager: DeviceDiagnosticManager
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _config = MutableStateFlow(SessionRecordingConfig())
    val config: StateFlow<SessionRecordingConfig> = _config.asStateFlow()

    private val _lastMetadata = MutableStateFlow<SessionMetadataHeader?>(null)
    val lastMetadata: StateFlow<SessionMetadataHeader?> = _lastMetadata.asStateFlow()

    private val _recordedSnapshots = MutableStateFlow<List<MetricsEngine.MetricsSnapshot>>(emptyList())
    val recordedSnapshots: StateFlow<List<MetricsEngine.MetricsSnapshot>> = _recordedSnapshots.asStateFlow()

    private var _recordingStartTimestampMs: Long = 0L
    val recordingStartTimestampMs: Long get() = _recordingStartTimestampMs

    private val _lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportedFile: StateFlow<File?> = _lastExportedFile.asStateFlow()

    private var snapshotChannel: Channel<MetricsEngine.MetricsSnapshot>? = null
    private var collectorJob: Job? = null
    private var writerJob: Job? = null
    private var activeSessionFile: File? = null
    private val recordedBuffer = ArrayList<MetricsEngine.MetricsSnapshot>(1800)
    private val bufferLock = Any()

    fun updateConfig(newConfig: SessionRecordingConfig) {
        _config.value = newConfig
        if (_isRecording.value) {
            val requiredModules = newConfig.selectedMetrics.flatMap { it.engineModules }.toSet() + "top_process"
            metricsEngine.setScreenOverrideModules(requiredModules, requesterKey = REQUESTER_KEY)
        }
    }

    fun startRecording(customConfig: SessionRecordingConfig = _config.value) {
        if (_isRecording.value) return
        _config.value = customConfig

        val startMs = System.currentTimeMillis()
        _recordingStartTimestampMs = startMs

        synchronized(bufferLock) {
            recordedBuffer.clear()
            _recordedSnapshots.value = emptyList()
        }

        val metadata = buildMetadataHeader(customConfig, startMs)
        _lastMetadata.value = metadata

        val requiredModules = customConfig.selectedMetrics
            .flatMap { it.engineModules }
            .toSet() + "top_process"
        metricsEngine.setScreenOverrideModules(requiredModules, requesterKey = REQUESTER_KEY)

        val dir = File(context.filesDir, "session_logs").apply { mkdirs() }
        val fileTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(startMs)
        val file = File(dir, "framex_session_$fileTag.csv")
        activeSessionFile = file
        _lastExportedFile.value = file

        val channel = Channel<MetricsEngine.MetricsSnapshot>(
            capacity = CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        snapshotChannel = channel
        _isRecording.value = true

        // Producer: non-blocking O(1) offer into ring-buffered channel on each new snapshot
        collectorJob = ioScope.launch {
            var lastSeenTimestamp = startMs - 1L
            metricsEngine.snapshotHistory.collect { history ->
                val latest = history.lastOrNull() ?: return@collect
                if (latest.timestampMs > lastSeenTimestamp) {
                    lastSeenTimestamp = latest.timestampMs
                    channel.trySend(latest)
                    val snapshotCopy = synchronized(bufferLock) {
                        if (recordedBuffer.size >= MAX_SESSION_SAMPLES) {
                            recordedBuffer.removeAt(0)
                        }
                        recordedBuffer.add(latest)
                        recordedBuffer.toList()
                    }
                    _recordedSnapshots.value = snapshotCopy
                }
            }
        }

        // Consumer: writes crash-safe header immediately and flushes chunks every 5 seconds
        writerJob = ioScope.launch {
            writeSessionStream(file, metadata, channel)
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = REQUESTER_KEY)
        collectorJob?.cancel()
        collectorJob = null
        snapshotChannel?.close()
        snapshotChannel = null
    }

    private suspend fun writeSessionStream(
        file: File,
        metadata: SessionMetadataHeader,
        channel: Channel<MetricsEngine.MetricsSnapshot>
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val chunkBuffer = ArrayList<MetricsEngine.MetricsSnapshot>(16)

        runCatching {
            BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), Charsets.UTF_8), 8192).use { writer ->
                writeCrashSafeHeader(writer, metadata)
                writer.flush()

                while (kotlin.coroutines.coroutineContext.isActive) {
                    delay(CHUNK_FLUSH_INTERVAL_MS)
                    chunkBuffer.clear()
                    while (true) {
                        val item = channel.tryReceive().getOrNull() ?: break
                        chunkBuffer.add(item)
                    }
                    if (chunkBuffer.isNotEmpty()) {
                        for (snap in chunkBuffer) {
                            writer.write(formatCsvRow(dateFormat, snap))
                        }
                        writer.flush()
                    }
                    if (channel.isClosedForReceive) break
                }

                // Drain any remaining items after stopRecording() closes the channel
                while (true) {
                    val remaining = channel.tryReceive().getOrNull() ?: break
                    writer.write(formatCsvRow(dateFormat, remaining))
                }
                writer.flush()
            }
        }
    }

    private fun writeCrashSafeHeader(writer: BufferedWriter, meta: SessionMetadataHeader) {
        writer.write("# FrameX Performance & Thermal Telemetry Session\n")
        writer.write("# Device Model: ${meta.deviceModel}\n")
        writer.write("# SoC Platform: ${meta.socIdentifier}\n")
        writer.write("# Android OS: ${meta.androidVersion} | Kernel: ${meta.kernelVersion}\n")
        writer.write("# Display Max Refresh Rate: ${meta.maxRefreshRateHz} Hz\n")
        writer.write("# Target App: ${meta.targetAppLabel} (${meta.targetPackageName.ifEmpty { "system" }}) ${meta.targetAppVersion}\n")
        writer.write("# Session Start: ${meta.startTimeFormatted}\n")
        writer.write("# Recorded Metrics: ${meta.selectedMetrics.joinToString(",") { it.id }}\n")
        writer.write(
            "timestamp,fps,frame_time_ms,janky_frames,cpu_mhz,cpu_percent," +
                "cpu_cluster_eff_mhz,cpu_cluster_perf_mhz,cpu_cluster_ultra_mhz," +
                "ram_used_gb,ram_total_gb,battery_temp_c,battery_level_pct,power_watts," +
                "network_rx_kbps,network_tx_kbps,ping_ms," +
                "thermal_cpu_c,thermal_gpu_c,thermal_npu_c,thermal_skin_c,thermal_status,is_throttling," +
                "top_process,top_process_cpu_percent\n"
        )
    }

    private fun formatCsvRow(
        fmt: SimpleDateFormat,
        snap: MetricsEngine.MetricsSnapshot
    ): String {
        val s = snap.state
        return buildString(192) {
            append(fmt.format(snap.timestampMs)).append(',')
            append(s.fps).append(',')
            append(s.frameTimeMs).append(',')
            append(s.jankyFrames).append(',')
            append(s.cpuMhz).append(',')
            append(s.cpuPercentage ?: "").append(',')
            append(s.cpuClusterEffMhz).append(',')
            append(s.cpuClusterPerfMhz).append(',')
            append(s.cpuClusterUltraMhz).append(',')
            append(String.format(Locale.US, "%.2f", s.ramUsedGb)).append(',')
            append(String.format(Locale.US, "%.2f", s.ramTotalGb)).append(',')
            append(String.format(Locale.US, "%.1f", s.batteryTempC)).append(',')
            append(s.batteryLevel).append(',')
            append(String.format(Locale.US, "%.2f", s.powerWatts)).append(',')
            append(String.format(Locale.US, "%.1f", s.networkRxKbps)).append(',')
            append(String.format(Locale.US, "%.1f", s.networkTxKbps)).append(',')
            append(s.pingMs).append(',')
            append(String.format(Locale.US, "%.1f", s.thermalCpuC)).append(',')
            append(String.format(Locale.US, "%.1f", s.thermalGpuC)).append(',')
            append(String.format(Locale.US, "%.1f", s.thermalNpuC)).append(',')
            append(String.format(Locale.US, "%.1f", s.thermalSkinC)).append(',')
            append(s.thermalStatus).append(',')
            append(s.isThrottling).append(',')
            append(s.topProcessName?.replace(',', ';').orEmpty()).append(',')
            append(String.format(Locale.US, "%.1f", s.topProcessCpuPercent)).append('\n')
        }
    }

    private fun buildMetadataHeader(
        cfg: SessionRecordingConfig,
        startMs: Long
    ): SessionMetadataHeader {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
                ?: deviceDiagnosticManager.getProperty("ro.board.platform")
                ?: Build.HARDWARE
        } else {
            deviceDiagnosticManager.getProperty("ro.board.platform") ?: Build.HARDWARE
        }
        val kernel = System.getProperty("os.version") ?: "Unknown"
        val startFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(startMs)
        val maxHz = deviceDiagnosticManager.getMaxHardwareRefreshRate().toInt().coerceAtLeast(60)

        return SessionMetadataHeader(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            socIdentifier = soc,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            kernelVersion = kernel,
            maxRefreshRateHz = maxHz,
            targetAppLabel = cfg.targetAppLabel,
            targetPackageName = cfg.targetPackageName.orEmpty(),
            targetAppVersion = cfg.targetAppVersion,
            startTimeFormatted = startFormatted,
            selectedMetrics = cfg.selectedMetrics
        )
    }

    /**
     * Flushes any pending in-memory samples and returns the session CSV file for sharing.
     */
    suspend fun exportToFile(): File? = withContext(Dispatchers.IO) {
        val existingFile = activeSessionFile
        val snapshots = synchronized(bufferLock) { recordedBuffer.toList() }
            .ifEmpty {
                if (_recordingStartTimestampMs == 0L) emptyList()
                else metricsEngine.snapshotHistory.value.filter { it.timestampMs >= _recordingStartTimestampMs }
            }

        if (snapshots.isEmpty()) return@withContext null

        val dir = File(context.filesDir, "session_logs").apply { mkdirs() }
        val targetFile = existingFile ?: File(
            dir,
            "framex_session_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.csv"
        )

        val meta = _lastMetadata.value ?: buildMetadataHeader(_config.value, _recordingStartTimestampMs)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        BufferedWriter(OutputStreamWriter(FileOutputStream(targetFile, false), Charsets.UTF_8), 8192).use { writer ->
            writeCrashSafeHeader(writer, meta)
            for (snap in snapshots) {
                writer.write(formatCsvRow(dateFormat, snap))
            }
            writer.flush()
        }

        _lastExportedFile.value = targetFile
        targetFile
    }

    fun buildShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "FrameX session log — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun currentRecordingCount(): Int {
        synchronized(bufferLock) {
            if (recordedBuffer.isNotEmpty()) return recordedBuffer.size
        }
        if (!_isRecording.value || _recordingStartTimestampMs == 0L) return 0
        return metricsEngine.snapshotHistory.value.count { it.timestampMs >= _recordingStartTimestampMs }
    }

    companion object {
        private const val REQUESTER_KEY = "session_logger"
        private const val CHANNEL_CAPACITY = 512
        private const val CHUNK_FLUSH_INTERVAL_MS = 5_000L
        private const val MAX_SESSION_SAMPLES = 3600
    }
}
