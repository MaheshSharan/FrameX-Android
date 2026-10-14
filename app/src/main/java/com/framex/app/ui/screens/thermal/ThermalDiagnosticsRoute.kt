package com.framex.app.ui.screens.thermal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ThermalDiagnosticsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThermalDiagnosticsViewModel = hiltViewModel()
) {
    val metricsState by viewModel.metricsState.collectAsStateWithLifecycle()
    val snapshotHistory by viewModel.snapshotHistory.collectAsStateWithLifecycle()
    val recordedSnapshots by viewModel.recordedSnapshots.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingConfig by viewModel.recordingConfig.collectAsStateWithLifecycle()
    val lastMetadata by viewModel.lastMetadata.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsStateWithLifecycle()
    val hasShizukuPermission by viewModel.hasShizukuPermission.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShizukuState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val sampleCount = viewModel.recordedSampleCount(snapshotHistory)

    ThermalDiagnosticsScreen(
        metricsState = metricsState,
        snapshotHistory = snapshotHistory,
        recordedSnapshots = recordedSnapshots,
        recordingConfig = recordingConfig,
        installedApps = installedApps,
        platformInfo = viewModel.platformInfo,
        maxRefreshRateHz = viewModel.maxRefreshRateHz,
        sessionTimestamp = lastMetadata?.startTimeFormatted ?: "Live Telemetry",
        isRecording = isRecording,
        sampleCount = sampleCount,
        isShizukuAvailable = isShizukuAvailable,
        hasShizukuPermission = hasShizukuPermission,
        onSelectApp = viewModel::selectTargetApp,
        onToggleMetric = viewModel::toggleRecordedMetric,
        onNavigateBack = onNavigateBack,
        onOpenShizuku = {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Shizuku app not found", Toast.LENGTH_SHORT).show()
            }
        },
        onRequestShizukuPermission = viewModel::requestShizukuPermission,
        onToggleRecording = viewModel::toggleRecording,
        onExportCsv = {
            viewModel.exportSession(
                onReady = { file ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "FrameX session log — ${file.name}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share session log"))
                },
                onEmpty = {
                    Toast.makeText(context, "Nothing recorded yet — start a session first", Toast.LENGTH_SHORT).show()
                }
            )
        },
        onCopyReport = {
            val activeList = recordedSnapshots.ifEmpty { snapshotHistory }
            val summary = buildDiagnosticSummaryText(metricsState, activeList)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FrameX Diagnostic Summary", summary))
            Toast.makeText(context, "Diagnostic summary copied to clipboard!", Toast.LENGTH_LONG).show()
        },
        modifier = modifier
    )
}
