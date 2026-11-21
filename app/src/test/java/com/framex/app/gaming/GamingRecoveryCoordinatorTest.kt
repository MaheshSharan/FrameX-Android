package com.framex.app.gaming

import android.content.ContextWrapper
import com.framex.app.privilege.FakePrivilegeBackend
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamingRecoveryCoordinatorTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = SettingsRepository.inMemory()

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Runs the coordinator and returns the privilege mode that was active when `disable` ran, or null. */
    private fun recover(
        selected: PrivilegeMode,
        appliedVia: PrivilegeMode,
        gamingActive: Boolean,
        sessionActive: Boolean = false
    ): PrivilegeMode? = runBlocking {
        settings.appPrefs.setPrivilegeMode(selected)
        settings.setGamingModeActive(gamingActive)
        settings.setGamingAppliedVia(appliedVia)
        val manager = PrivilegeManager(
            settings,
            FakePrivilegeBackend(PrivilegeMode.SHIZUKU),
            FakePrivilegeBackend(PrivilegeMode.ROOT),
            scope
        ).also { it.start() }
        val modeAtDisable = CompletableDeferred<PrivilegeMode>()
        val coordinator = GamingRecoveryCoordinator(ContextWrapper(null), manager, settings)

        val job = scope.launch {
            coordinator.watch(
                isSessionActive = { sessionActive },
                disable = {
                    modeAtDisable.complete(manager.currentMode.value)
                    settings.setGamingModeActive(false)
                },
                legacyCleanup = {}
            )
        }
        val result = withTimeoutOrNull(WAIT_MS) { modeAtDisable.await() }
        if (result == null) delay(SETTLE_MS)
        job.cancel()
        result
    }

    @Test
    fun interruptedSession_isUndoneThroughTheBackendThatAppliedIt() {
        assertEquals(PrivilegeMode.ROOT, recover(PrivilegeMode.SHIZUKU, PrivilegeMode.ROOT, gamingActive = true))
    }

    @Test
    fun interruptedSession_usesTheSelectedBackendWhenItAppliedTheChanges() {
        assertEquals(PrivilegeMode.SHIZUKU, recover(PrivilegeMode.SHIZUKU, PrivilegeMode.SHIZUKU, gamingActive = true))
    }

    @Test
    fun cleanState_doesNothing() {
        assertNull(recover(PrivilegeMode.SHIZUKU, PrivilegeMode.NONE, gamingActive = false))
    }

    @Test
    fun liveSession_isNeverRecovered() {
        assertNull(recover(PrivilegeMode.SHIZUKU, PrivilegeMode.SHIZUKU, gamingActive = true, sessionActive = true))
    }

    private companion object {
        const val WAIT_MS = 3_000L
        const val SETTLE_MS = 200L
    }
}
