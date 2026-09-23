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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framex.app.R
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
        viewModel.updateNotificationPermission(granted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PermissionsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onLaunchShizuku = {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, context.getString(R.string.perm_shizuku_not_found), Toast.LENGTH_SHORT).show()
            }
        },
        onRequestShizukuPermission = {
            if (uiState.isShizukuAvailable && !uiState.hasShizukuPermission) {
                viewModel.requestShizukuPermission()
            } else if (!uiState.isShizukuAvailable) {
                Toast.makeText(context, context.getString(R.string.perm_start_shizuku_first), Toast.LENGTH_SHORT).show()
            }
        },
        onRequestPermission = { permissionId ->
            when (permissionId) {
                PermissionId.OVERLAY -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
                PermissionId.USAGE_STATS -> {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }
                PermissionId.BATTERY_OPT -> {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
                PermissionId.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                PermissionId.WRITE_SETTINGS -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }
        },
        modifier = modifier
    )
}
