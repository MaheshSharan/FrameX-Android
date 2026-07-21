package com.framex.app.gaming

import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OemPackageResolver @Inject constructor(
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val settingsRepository: SettingsRepository
) {
    val VIVO_SAFE_TO_SUSPEND = listOf(
        // App Stores & Updaters
        "com.vivo.appstore",
        "com.bbk.updater",
        "com.vivo.website",
        "com.vivo.cardstore",

        // UI Bloat & Background Polling
        "com.vivo.assistant",
        "com.vivo.hiboard",            // Jovi / Minus-one screen
        "com.vivo.globalsearch",
        "com.vivo.magazine",           // Lockscreen magazine
        "com.bbk.theme",               // Theme store background sync
        "com.vivo.theme.effect",
        "com.vivo.video.floating",

        // Widgets & Syncers
        "com.vivo.weather",
        "com.vivo.weather.provider",
        "com.vivo.healthwidget",
        "com.vivo.stepcount",
        "com.vivo.exhealth",
        "com.bbk.cloud",               // Vivo Cloud sync

        // Secondary Vivo Services
        "com.vivo.imanager",           // Vivo cleaner
        "com.vivo.safecenter",         // Vivo security
        "com.vivo.xspace",
        "com.vivo.doubleinstance",     // App clone daemon
        "com.vivo.musicwidgetmix",
        "com.vivo.smartshot",
        "com.vivo.nps"                 // Net Promoter Score / Analytics
    )

    fun getOemPackagesToSuspend(): List<String> {
        val isVivoEnabled = deviceDiagnosticManager.isVivoOrIqoo() || settingsRepository.vivoOptEnabled.value
        return if (isVivoEnabled) {
            VIVO_SAFE_TO_SUSPEND
        } else {
            emptyList()
        }
    }
}
