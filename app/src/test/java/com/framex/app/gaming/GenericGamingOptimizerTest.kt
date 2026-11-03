package com.framex.app.gaming

import android.content.ContextWrapper
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.ledger.ExecutionLedger
import com.framex.app.gaming.ledger.LedgerExecutor
import com.framex.app.privilege.FakePrivilegeBackend
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericGamingOptimizerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settings = SettingsRepository.inMemory()
    private val backend = FakePrivilegeBackend(PrivilegeMode.SHIZUKU)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun optimizer(mode: PrivilegeMode): GenericGamingOptimizer {
        settings.appPrefs.setPrivilegeMode(mode)
        val manager = PrivilegeManager(settings, backend, FakePrivilegeBackend(PrivilegeMode.ROOT), scope)
            .also { it.start() }
        val audit = SystemAuditLogRepository(settings)
        return GenericGamingOptimizer(
            manager,
            settings,
            DeviceDiagnosticManager(ContextWrapper(null)),
            LedgerExecutor(manager, ExecutionLedger(), audit)
        )
    }

    @Test
    fun withoutAPrivilegeMode_applyFailsAndRunsNothing() = runBlocking {
        assertFalse(optimizer(PrivilegeMode.NONE).applyOptimizationsForGame("com.example.game", 10_001))
        assertEquals(0, backend.execCalls)
    }

    @Test
    fun withoutAPrivilegeMode_revertFailsAndRunsNothing() = runBlocking {
        assertFalse(optimizer(PrivilegeMode.NONE).revertOptimizations())
        assertEquals(0, backend.execCalls)
    }

    @Test
    fun revertWithoutASnapshotStillSucceeds() = runBlocking {
        assertTrue(optimizer(PrivilegeMode.SHIZUKU).revertOptimizations())
    }

    @Test
    fun attachRejectsAnUnsafePackageName() = runBlocking {
        assertFalse(optimizer(PrivilegeMode.SHIZUKU).attachGame("com.x; reboot", null))
    }
}
