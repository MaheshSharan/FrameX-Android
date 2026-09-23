package com.framex.app.ui.screens.splash

import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SplashStateTest {

    @Test
    fun splashUiState_defaultValues_areCorrect() {
        val state = SplashUiState()

        assertFalse(state.isOnboardingCompleted)
        assertFalse(state.autoUpdateEnabled)
        assertFalse(state.isCheckingUpdates)
        assertNull(state.updateInfoState)
        assertNull(state.signatureErrorMessage)
        assertEquals(DownloadState.Idle, state.downloadState)
        assertNull(state.pendingInstallApk)
        assertFalse(state.waitingForInstallPermission)
    }

    @Test
    fun splashUiState_copyModifiers_updateFieldsCorrectly() {
        val initial = SplashUiState()
        val mockUpdate = AppUpdateInfo(
            versionName = "2.0.0",
            releaseNotes = "Major release",
            downloadUrl = "https://github.com/releases/v2.0.0.apk",
            assetSize = 15728640L,
            isUpdateAvailable = true
        )

        val updated = initial.copy(
            isOnboardingCompleted = true,
            autoUpdateEnabled = true,
            isCheckingUpdates = true,
            updateInfoState = mockUpdate,
            signatureErrorMessage = "Signature mismatch detected",
            waitingForInstallPermission = true
        )

        assertTrue(updated.isOnboardingCompleted)
        assertTrue(updated.autoUpdateEnabled)
        assertTrue(updated.isCheckingUpdates)
        assertEquals(mockUpdate, updated.updateInfoState)
        assertEquals("Signature mismatch detected", updated.signatureErrorMessage)
        assertTrue(updated.waitingForInstallPermission)
    }

    @Test
    fun splashUiState_pendingInstallApk_holdsFileReference() {
        val testFile = File("/mock/cache/update.apk")
        val state = SplashUiState(pendingInstallApk = testFile)

        assertEquals(testFile, state.pendingInstallApk)
    }
}
