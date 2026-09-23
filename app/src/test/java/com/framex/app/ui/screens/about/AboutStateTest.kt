package com.framex.app.ui.screens.about

import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutStateTest {

    @Test
    fun aboutUiState_defaultValues_areCorrect() {
        val state = AboutUiState()

        assertEquals("", state.versionName)
        assertEquals(0L, state.versionCode)
        assertFalse(state.autoUpdateEnabled)
        assertFalse(state.isCheckingUpdate)
        assertNull(state.statusMessage)
        assertNull(state.updateInfoState)
        assertNull(state.signatureErrorMessage)
        assertEquals(DownloadState.Idle, state.downloadState)
        assertFalse(state.isVivoDevice)
        assertFalse(state.isVivoOptActive)
        assertFalse(state.showVivoDiagModal)
        assertFalse(state.disableThermalThrottling)
        assertFalse(state.hasCrashLog)
        assertNull(state.pendingInstallApk)
        assertFalse(state.waitingForInstallPermission)
    }

    @Test
    fun aboutUiState_copyModifiers_updateFieldsCorrectly() {
        val initial = AboutUiState()
        val mockUpdate = AppUpdateInfo(
            versionName = "1.5.0",
            releaseNotes = "Bug fixes and performance improvements",
            downloadUrl = "https://github.com/releases/v1.5.0.apk",
            assetSize = 10485760L,
            isUpdateAvailable = true
        )

        val updated = initial.copy(
            versionName = "1.5.0",
            versionCode = 15L,
            autoUpdateEnabled = true,
            isCheckingUpdate = true,
            statusMessage = "Update available",
            updateInfoState = mockUpdate,
            isVivoDevice = true,
            isVivoOptActive = true,
            hasCrashLog = true,
            waitingForInstallPermission = true
        )

        assertEquals("1.5.0", updated.versionName)
        assertEquals(15L, updated.versionCode)
        assertTrue(updated.autoUpdateEnabled)
        assertTrue(updated.isCheckingUpdate)
        assertEquals("Update available", updated.statusMessage)
        assertEquals(mockUpdate, updated.updateInfoState)
        assertTrue(updated.isVivoDevice)
        assertTrue(updated.isVivoOptActive)
        assertTrue(updated.hasCrashLog)
        assertTrue(updated.waitingForInstallPermission)
    }

    @Test
    fun aboutUiState_pendingInstallApk_holdsFileReference() {
        val testFile = File("/mock/cache/update.apk")
        val state = AboutUiState(pendingInstallApk = testFile)

        assertEquals(testFile, state.pendingInstallApk)
    }
}
