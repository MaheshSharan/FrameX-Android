package com.framex.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object UpdateInstallerBus {
    private val _installEvents = MutableSharedFlow<InstallResult>(extraBufferCapacity = 1)
    val installEvents: SharedFlow<InstallResult> = _installEvents.asSharedFlow()

    fun postResult(result: InstallResult) {
        _installEvents.tryEmit(result)
    }
}

class UpdateInstallerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                UpdateInstallerBus.postResult(InstallResult.Success)
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                UpdateInstallerBus.postResult(
                    InstallResult.SignatureMismatch(
                        statusMessage ?: "You have a Debug build installed which conflicts with the Release APK signature."
                    )
                )
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
            else -> {
                UpdateInstallerBus.postResult(
                    InstallResult.Failed(statusMessage ?: "Installation failed (status $status)")
                )
            }
        }
    }
}
