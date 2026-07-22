package com.framex.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class InstallResult {
    object Success : InstallResult()
    object PermissionRequired : InstallResult()
    data class SignatureMismatch(val errorMessage: String) : InstallResult()
    data class Failed(val reason: String) : InstallResult()
}

@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    fun canInstallPackages(): Boolean {
        if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
            return true
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    suspend fun installApk(
        apkFile: File,
        onResult: (InstallResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            withContext(Dispatchers.Main) {
                onResult(InstallResult.Failed("APK file does not exist"))
            }
            return@withContext
        }

        // Engine A: Shizuku Privileged Direct 1-Tap Installation (Bypasses Unknown Apps Prompt)
        if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
            val cmd = "cmd package install -r ${apkFile.absolutePath}"
            val output = shizukuManager.executeCommand(cmd)
            withContext(Dispatchers.Main) {
                if (output.contains("Success", ignoreCase = true)) {
                    onResult(InstallResult.Success)
                } else if (output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                    output.contains("signatures do not match", ignoreCase = true) ||
                    output.contains("SHARED_USER_INCOMPATIBLE", ignoreCase = true)
                ) {
                    onResult(
                        InstallResult.SignatureMismatch(
                            "You have a Debug build installed which conflicts with the Release APK signature."
                        )
                    )
                } else {
                    performStandardPackageInstall(context, apkFile, onResult)
                }
            }
            return@withContext
        }

        // Engine B: Standard Android PackageInstaller Session API
        withContext(Dispatchers.Main) {
            performStandardPackageInstall(context, apkFile, onResult)
        }
    }

    private fun performStandardPackageInstall(
        context: Context,
        apkFile: File,
        onResult: (InstallResult) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            onResult(InstallResult.PermissionRequired)
            return
        }

        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.openWrite("package", 0, apkFile.length()).use { output ->
                FileInputStream(apkFile).use { input ->
                    input.copyTo(output)
                }
                session.fsync(output)
            }

            val intent = Intent(context, UpdateInstallerReceiver::class.java)
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, pendingIntentFlags)

            session.commit(pendingIntent.intentSender)
            session.close()
        } catch (e: Exception) {
            onResult(InstallResult.Failed(e.localizedMessage ?: "Failed to launch package installer session"))
        }
    }

    fun openUnknownAppSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    suspend fun handleSignatureMismatch(
        downloadedApkFile: File?,
        targetVersionName: String,
        onAppClosing: () -> Unit
    ) = withContext(Dispatchers.IO) {
        var publicApkFile: File? = null

        if (downloadedApkFile != null && downloadedApkFile.exists()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "FrameX_v${targetVersionName}-release.apk")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { output ->
                            downloadedApkFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                } else {
                    val publicDownloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(publicDownloadsDir, "FrameX_v${targetVersionName}-release.apk")
                    downloadedApkFile.copyTo(targetFile, overwrite = true)
                    publicApkFile = targetFile
                }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Saved FrameX_v${targetVersionName}-release.apk to Downloads folder",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.e("Failed to copy APK to MediaStore/Downloads", e)
            }
        }

        withContext(Dispatchers.Main) {
            onAppClosing()
        }

        // ENGINE A: Shizuku Shell Daemon 2-Step Auto Uninstall & Re-install Chain
        if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
            val apkPath = publicApkFile?.absolutePath ?: "/sdcard/Download/FrameX_v${targetVersionName}-release.apk"
            val command = "cmd package uninstall ${context.packageName} && cmd package install -r \"$apkPath\""
            shizukuManager.executeCommand(command)
            return@withContext
        }

        // ENGINE B: Standard Fallback System Uninstaller Intent
        withContext(Dispatchers.Main) {
            try {
                val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(uninstallIntent)
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.e("Failed to launch uninstaller intent", e)
            }
        }
    }
}
