package com.framex.app.ui.screens.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.framex.app.permissions.AppPermission
import com.framex.app.permissions.requestIntent
import com.framex.app.permissions.startSettingsActivity

/**
 * Returns the one action behind every Grant button: open the system screen for the
 * permission, or show the runtime dialog for notifications. [onRuntimeResult] runs after
 * that dialog closes, because it does not stop the activity and so triggers no resume refresh.
 */
@Composable
fun rememberPermissionGranter(onRuntimeResult: () -> Unit): (AppPermission) -> Unit {
    val context = LocalContext.current
    val notificationDialog = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRuntimeResult() }
    return remember(context, notificationDialog) {
        { permission ->
            val intent = permission.requestIntent(context)
            if (intent != null) {
                context.startSettingsActivity(intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationDialog.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
