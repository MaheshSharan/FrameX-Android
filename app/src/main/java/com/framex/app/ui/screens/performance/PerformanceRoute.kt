package com.framex.app.ui.screens.performance

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framex.app.R
import com.framex.app.utils.FrameXLog

@Composable
fun PerformanceRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerformanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(PerformanceUiEvent.RefreshSystemState)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PerformanceUiEffect.ShowToast -> {
                    val text = if (effect.formatArgs.isEmpty()) {
                        context.getString(effect.messageResId)
                    } else {
                        context.getString(effect.messageResId, *effect.formatArgs.toTypedArray())
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PerformanceScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        getGameConfigBoostRam = viewModel::getGameConfigBoostRam,
        setGameConfigBoostRam = viewModel::setGameConfigBoostRam,
        onRequestShizuku = {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:moe.shizuku.privileged.api")
                }
            runCatching { context.startActivity(intent) }.onFailure { e ->
                FrameXLog.w("Unable to open Shizuku settings", e)
                Toast.makeText(
                    context,
                    context.getString(R.string.perf_toast_open_shizuku_err),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onRequestWriteSettings = {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(intent) }.onFailure { e ->
                FrameXLog.w("Unable to open Write Settings", e)
                Toast.makeText(
                    context,
                    context.getString(R.string.perf_toast_open_write_settings_err),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onRequestDndAccess = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            runCatching { context.startActivity(intent) }.onFailure { e ->
                FrameXLog.w("Unable to open DND access settings", e)
                Toast.makeText(
                    context,
                    context.getString(R.string.perf_toast_open_dnd_err),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onRequestNotificationListener = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            runCatching { context.startActivity(intent) }.onFailure { e ->
                FrameXLog.w("Unable to open Notification Listener settings", e)
                Toast.makeText(
                    context,
                    context.getString(R.string.perf_toast_open_notif_listener_err),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}
