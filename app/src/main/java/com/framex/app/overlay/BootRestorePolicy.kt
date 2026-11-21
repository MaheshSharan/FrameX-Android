package com.framex.app.overlay

import android.content.Intent

/**
 * Decides whether the overlay should be started again after a boot or an app update.
 *
 * Only broadcasts delivered after the first unlock are handled: the settings live in
 * credential-encrypted storage, which cannot be read during locked boot.
 */
internal fun shouldRestoreOverlay(
    action: String?,
    wasRunning: Boolean,
    canDrawOverlays: Boolean
): Boolean {
    val isRestoreTrigger = action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED
    return isRestoreTrigger && wasRunning && canDrawOverlays
}
