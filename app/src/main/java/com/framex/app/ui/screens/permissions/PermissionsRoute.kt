package com.framex.app.ui.screens.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.framex.app.ui.featurediscovery.DiscoveryScreenId
import com.framex.app.ui.featurediscovery.ScreenDiscoveryEffect

@Composable
fun PermissionsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    ScreenDiscoveryEffect(DiscoveryScreenId.PERMISSIONS)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onEvent(PermissionsUiEvent.UpdateNotificationPermission(granted))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(PermissionsUiEvent.RefreshPermissions)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PermissionsUiEffect.LaunchShizukuApp -> {
                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                }
                PermissionsUiEffect.OpenOverlaySettings -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
                PermissionsUiEffect.OpenUsageSettings -> {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
                PermissionsUiEffect.OpenBatterySettings -> {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
                PermissionsUiEffect.OpenWriteSettings -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
                PermissionsUiEffect.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                is PermissionsUiEffect.ShowToast -> {
                    Toast.makeText(context, context.getString(effect.messageRes), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PermissionsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onEvent = viewModel::onEvent,
        modifier = modifier
    )
}
