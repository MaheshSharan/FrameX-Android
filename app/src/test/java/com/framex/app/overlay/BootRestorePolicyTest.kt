package com.framex.app.overlay

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRestorePolicyTest {

    @Test
    fun bootCompletedRestoresWhenWasRunningAndPermitted() {
        assertTrue(shouldRestoreOverlay(Intent.ACTION_BOOT_COMPLETED, wasRunning = true, canDrawOverlays = true))
    }

    @Test
    fun packageReplacedRestoresAfterAppUpdate() {
        assertTrue(shouldRestoreOverlay(Intent.ACTION_MY_PACKAGE_REPLACED, wasRunning = true, canDrawOverlays = true))
    }

    @Test
    fun lockedBootAndQuickBootAreIgnored() {
        assertFalse(shouldRestoreOverlay("android.intent.action.LOCKED_BOOT_COMPLETED", true, true))
        assertFalse(shouldRestoreOverlay("android.intent.action.QUICKBOOT_POWERON", true, true))
    }

    @Test
    fun doesNotRestoreWhenNotRunningBefore() {
        assertFalse(shouldRestoreOverlay(Intent.ACTION_BOOT_COMPLETED, wasRunning = false, canDrawOverlays = true))
    }

    @Test
    fun doesNotRestoreWithoutOverlayPermission() {
        assertFalse(shouldRestoreOverlay(Intent.ACTION_BOOT_COMPLETED, wasRunning = true, canDrawOverlays = false))
    }

    @Test
    fun nullActionIsIgnored() {
        assertFalse(shouldRestoreOverlay(null, true, true))
    }
}
