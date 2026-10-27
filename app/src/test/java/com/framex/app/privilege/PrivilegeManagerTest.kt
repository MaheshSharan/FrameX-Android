package com.framex.app.privilege

import com.framex.app.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrivilegeManagerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settings = SettingsRepository.inMemory()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun manager(
        saved: PrivilegeMode,
        shizuku: FakePrivilegeBackend,
        root: FakePrivilegeBackend
    ): PrivilegeManager {
        settings.appPrefs.setPrivilegeMode(saved)
        return PrivilegeManager(settings, shizuku, root, scope).also { it.start() }
    }

    @Test
    fun rootSelectedButNotGranted_neverTouchesShizuku() = runBlocking {
        val shizuku = FakePrivilegeBackend(PrivilegeMode.SHIZUKU)
        val root = FakePrivilegeBackend(PrivilegeMode.ROOT, connectsOnStart = false)
        val manager = manager(PrivilegeMode.ROOT, shizuku, root)

        val outcome = manager.exec("id")

        assertFalse(outcome.isSuccess)
        assertFalse(manager.isReady.value)
        assertEquals(0, shizuku.startCalls)
        assertEquals(0, shizuku.execCalls)
    }

    @Test
    fun noModeSelected_failsWithoutStartingAnyBackend() = runBlocking {
        val shizuku = FakePrivilegeBackend(PrivilegeMode.SHIZUKU)
        val root = FakePrivilegeBackend(PrivilegeMode.ROOT)
        val manager = manager(PrivilegeMode.NONE, shizuku, root)

        assertFalse(manager.exec("id").isSuccess)
        assertEquals(0, shizuku.startCalls + root.startCalls)
    }

    @Test
    fun switchMode_isRefusedWhileGamingModeIsActiveAndCannotBeReverted() = runBlocking {
        val manager = manager(PrivilegeMode.SHIZUKU, FakePrivilegeBackend(PrivilegeMode.SHIZUKU), FakePrivilegeBackend(PrivilegeMode.ROOT))
        settings.setGamingModeActive(true)

        assertEquals(SwitchResult.BLOCKED_GAMING_ACTIVE, manager.switchMode(PrivilegeMode.ROOT))
        assertEquals(PrivilegeMode.SHIZUKU, manager.currentMode.value)
    }

    @Test
    fun switchMode_revertsGamingModeFirstThenSwitches() = runBlocking {
        val manager = manager(PrivilegeMode.SHIZUKU, FakePrivilegeBackend(PrivilegeMode.SHIZUKU), FakePrivilegeBackend(PrivilegeMode.ROOT))
        settings.setGamingModeActive(true)
        manager.registerSwitchGuard {
            settings.setGamingModeActive(false)
            true
        }

        assertEquals(SwitchResult.SWITCHED, manager.switchMode(PrivilegeMode.ROOT))
        assertEquals(PrivilegeMode.ROOT, manager.currentMode.value)
        assertEquals(PrivilegeMode.ROOT, settings.appPrefs.getPrivilegeMode())
    }

    @Test
    fun switchMode_isRefusedWhenTheGuardCannotRevert() = runBlocking {
        val manager = manager(PrivilegeMode.SHIZUKU, FakePrivilegeBackend(PrivilegeMode.SHIZUKU), FakePrivilegeBackend(PrivilegeMode.ROOT))
        settings.setGamingModeActive(true)
        manager.registerSwitchGuard { false }

        assertEquals(SwitchResult.BLOCKED_GAMING_ACTIVE, manager.switchMode(PrivilegeMode.ROOT))
        assertEquals(PrivilegeMode.SHIZUKU, manager.currentMode.value)
    }

    @Test
    fun switchMode_isRefusedWhileRecoveryIsPending() = runBlocking {
        val manager = manager(PrivilegeMode.SHIZUKU, FakePrivilegeBackend(PrivilegeMode.SHIZUKU), FakePrivilegeBackend(PrivilegeMode.ROOT))
        settings.setNeedsThermalOverrideActive(true)

        assertEquals(SwitchResult.BLOCKED_RECOVERY_PENDING, manager.switchMode(PrivilegeMode.ROOT))
    }

    @Test
    fun switchMode_stopsTheOldBackendAndStartsTheNewOne() = runBlocking {
        val shizuku = FakePrivilegeBackend(PrivilegeMode.SHIZUKU)
        val root = FakePrivilegeBackend(PrivilegeMode.ROOT)
        val manager = manager(PrivilegeMode.SHIZUKU, shizuku, root)

        assertEquals(SwitchResult.SWITCHED, manager.switchMode(PrivilegeMode.ROOT))

        assertEquals(1, shizuku.stopCalls)
        assertEquals(1, root.startCalls)
    }

    @Test
    fun runWithBackend_restoresTheSelectedModeWhenTheBlockThrows() = runBlocking {
        val manager = manager(PrivilegeMode.SHIZUKU, FakePrivilegeBackend(PrivilegeMode.SHIZUKU), FakePrivilegeBackend(PrivilegeMode.ROOT))

        try {
            manager.runWithBackend(PrivilegeMode.ROOT) { error("boom") }
            fail("expected the block to throw")
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }

        assertEquals(PrivilegeMode.SHIZUKU, manager.currentMode.value)
        assertEquals(PrivilegeMode.SHIZUKU, settings.appPrefs.getPrivilegeMode())
    }

    @Test
    fun runWithBackend_returnsNullAndRestoresWhenTheBackendNeverConnects() = runBlocking {
        val manager = manager(
            PrivilegeMode.SHIZUKU,
            FakePrivilegeBackend(PrivilegeMode.SHIZUKU),
            FakePrivilegeBackend(PrivilegeMode.ROOT, connectsOnStart = false)
        )
        var ran = false

        val result = manager.runWithBackend(PrivilegeMode.ROOT, timeoutMs = 50L) { ran = true }

        assertNull(result)
        assertFalse(ran)
        assertEquals(PrivilegeMode.SHIZUKU, manager.currentMode.value)
    }

    @Test
    fun runWithBackend_runsTheBlockOnTheRequestedBackend() = runBlocking {
        val shizuku = FakePrivilegeBackend(PrivilegeMode.SHIZUKU)
        val root = FakePrivilegeBackend(PrivilegeMode.ROOT)
        val manager = manager(PrivilegeMode.SHIZUKU, shizuku, root)

        val ok = manager.runWithBackend(PrivilegeMode.ROOT) { manager.exec("id").isSuccess }

        assertEquals(true, ok)
        assertTrue(root.execCalls > 0)
    }
}
