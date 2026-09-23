package com.framex.app.ui.screens.performance

import android.app.NotificationManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PerformanceRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerformanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val nm = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    var hasDndAccess by remember { mutableStateOf(nm.isNotificationPolicyAccessGranted) }
    var hasNotifListenerAccess by remember {
        mutableStateOf(
            android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )?.contains(context.packageName) == true
        )
    }
    var hasWriteSettingsAccess by remember { mutableStateOf(android.provider.Settings.System.canWrite(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasDndAccess = nm.isNotificationPolicyAccessGranted
                hasNotifListenerAccess = android.provider.Settings.Secure.getString(
                    context.contentResolver, "enabled_notification_listeners"
                )?.contains(context.packageName) == true
                hasWriteSettingsAccess = android.provider.Settings.System.canWrite(context)
                viewModel.onEvent(PerformanceUiEvent.RefreshInstalledApps)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PerformanceUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PerformanceScreen(
        uiState = uiState,
        hasDndAccess = hasDndAccess,
        hasNotifListenerAccess = hasNotifListenerAccess,
        hasWriteSettingsAccess = hasWriteSettingsAccess,
        onEvent = viewModel::onEvent,
        getGameConfigBoostRam = viewModel::getGameConfigBoostRam,
        setGameConfigBoostRam = viewModel::setGameConfigBoostRam,
        getGameConfigMemc = viewModel::getGameConfigMemc,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}
