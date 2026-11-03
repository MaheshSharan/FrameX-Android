package com.framex.app.hardware

import com.framex.app.privilege.FakePrivilegeBackend
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.privilege.PrivilegedOutcome
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

class CpuTuningControllerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val commands = mutableListOf<String>()
    private val path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** [echoBack] decides what the read-back after a write returns. */
    private fun controller(mode: PrivilegeMode, echoBack: (String) -> String = { it }): CpuTuningController {
        val settings = SettingsRepository.inMemory()
        settings.appPrefs.setPrivilegeMode(mode)
        val shizuku = FakePrivilegeBackend(PrivilegeMode.SHIZUKU)
        val root = FakePrivilegeBackend(PrivilegeMode.ROOT)
        val handler: (String) -> PrivilegedOutcome = { cmd ->
            commands += cmd
            when {
                cmd.startsWith("echo '") -> PrivilegedOutcome.Success(echoBack(cmd.substringAfter("echo '").substringBefore("'")))
                cmd.startsWith("stat") -> PrivilegedOutcome.Success(statOutput)
                else -> PrivilegedOutcome.Success("")
            }
        }
        shizuku.handler = handler
        root.handler = handler
        return CpuTuningController(PrivilegeManager(settings, shizuku, root, scope).also { it.start() })
    }

    private var statOutput = ""

    @Test
    fun rootWriteWithLock_unlocksWritesThenLocks() = runBlocking {
        val ok = controller(PrivilegeMode.ROOT).setClusterMaxFreq(0, 1_800_000L, lock = true)

        assertTrue(ok)
        assertEquals(
            listOf("chmod 664 '$path'", "echo '1800000' > '$path' && cat '$path'", "chmod 444 '$path'"),
            commands
        )
    }

    @Test
    fun rootWriteThatDoesNotVerify_leavesTheNodeWritable() = runBlocking {
        val ok = controller(PrivilegeMode.ROOT, echoBack = { "999" }).setClusterMaxFreq(0, 1_800_000L, lock = true)

        assertFalse(ok)
        assertEquals("chmod 664 '$path'", commands.last())
    }

    @Test
    fun shizukuNeverChangesFileModes() = runBlocking {
        controller(PrivilegeMode.SHIZUKU).setClusterMaxFreq(0, 1_800_000L, lock = true)

        assertTrue(commands.none { it.startsWith("chmod") })
    }

    @Test
    fun invalidGovernorNameIsRejectedWithoutRunningAnything() = runBlocking {
        assertFalse(controller(PrivilegeMode.ROOT).setClusterGovernor(0, "x; rm -rf /"))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun isLocked_requiresEveryNodeToLackOwnerWrite() = runBlocking {
        val controller = controller(PrivilegeMode.ROOT)

        statOutput = "444\n444\n"
        assertTrue(controller.isLocked())
        statOutput = "444\n664\n"
        assertFalse(controller.isLocked())
        statOutput = ""
        assertFalse(controller.isLocked())
    }

    @Test
    fun lockAndUnlockAreRootOnly() = runBlocking {
        val shizuku = controller(PrivilegeMode.SHIZUKU)

        assertFalse(shizuku.lockAll())
        assertFalse(shizuku.unlockAll())
        assertTrue(commands.isEmpty())
    }
}
