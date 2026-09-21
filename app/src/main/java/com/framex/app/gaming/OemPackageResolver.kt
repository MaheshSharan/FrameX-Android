package com.framex.app.gaming

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OemPackageResolver @Inject constructor(
    private val vivoSuiteGate: VivoSuiteGate
) {

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Resolves OEM packages safe for background suspension based on active
     * hardware diagnostics and user optimization preferences.
     */
    fun getOemPackagesToSuspend(): List<String> {
        return if (vivoSuiteGate.isVivoSuiteEnabled) {
            VIVO_SAFE_TO_SUSPEND
        } else {
            emptyList()
        }
    }

    // =========================================================================
    // OEM Package Category Definitions (IDE & GitHub Symbol Navigation)
    // =========================================================================

    companion object {
        private val APP_STORES_AND_UPDATERS = listOf(
            "com.vivo.appstore",
            "com.bbk.updater",
            "com.vivo.website",
            "com.vivo.cardstore"
        )

        private val UI_BLOAT_AND_BACKGROUND_POLLERS = listOf(
            "com.vivo.assistant",
            "com.vivo.globalsearch",
            "com.vivo.magazine",           // Lockscreen magazine
            "com.bbk.theme",               // Theme store background sync
            "com.vivo.theme.effect",
            "com.vivo.video.floating"
        )

        private val WIDGETS_AND_SYNCERS = listOf(
            "com.vivo.weather",
            "com.vivo.healthwidget",
            "com.vivo.stepcount",
            "com.vivo.exhealth",
            "com.bbk.cloud"                // Vivo Cloud sync
        )

        /**
         * Secondary services safe to freeze.
         * NOTE: com.vivo.imanager is strictly excluded because it hosts ANDR-VIVO-PERF /
         * GameCube perf backend—suspending it breaks perf_lock handshakes.
         */
        private val SECONDARY_SERVICES = listOf(
            "com.vivo.xspace",
            "com.vivo.doubleinstance",     // App clone daemon
            "com.vivo.musicwidgetmix",
            "com.vivo.smartshot",
            "com.vivo.nps"                 // Net Promoter Score / Analytics
        )

        val VIVO_SAFE_TO_SUSPEND: List<String> =
            APP_STORES_AND_UPDATERS +
            UI_BLOAT_AND_BACKGROUND_POLLERS +
            WIDGETS_AND_SYNCERS +
            SECONDARY_SERVICES
    }
}