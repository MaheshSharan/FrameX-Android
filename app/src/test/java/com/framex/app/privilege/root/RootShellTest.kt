package com.framex.app.privilege.root

import com.framex.app.privilege.PrivilegedOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Exercises the real framing, timeout and respawn logic against a local POSIX shell. It proves
 * the protocol, not root behaviour: a rooted device is still required for that.
 */
class RootShellTest {

    private val hasShell = File("/bin/sh").exists()

    private fun <T> withShell(block: suspend (RootShell) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val uid = ProcessBuilder("sh", "-c", "id -u").start().inputStream.bufferedReader().readText().trim()
        val shell = RootShell(listOf(listOf("sh")), scope, expectedUid = uid)
        try {
            block(shell)
        } finally {
            shell.close()
            scope.cancel()
        }
    }

    @Test
    fun returnsOutputAndExitCodes() {
        assumeTrue(hasShell)
        withShell { shell ->
            assertEquals(PrivilegedOutcome.Success("hello"), shell.exec("echo hello", 3_000))
            assertEquals(PrivilegedOutcome.Success("abc"), shell.exec("printf abc", 3_000))
            assertEquals(1, (shell.exec("false", 3_000) as PrivilegedOutcome.NonZeroExit).exitCode)
        }
    }

    @Test
    fun commandCannotKillShellOrStealStdin() {
        assumeTrue(hasShell)
        withShell { shell ->
            assertEquals(7, (shell.exec("exit 7", 3_000) as PrivilegedOutcome.NonZeroExit).exitCode)
            assertTrue(shell.exec("cat", 3_000) is PrivilegedOutcome.Success)
            assertEquals(PrivilegedOutcome.Success("after"), shell.exec("echo after", 3_000))
        }
    }

    @Test
    fun stalledCommandTimesOutAndShellRecovers() {
        assumeTrue(hasShell)
        withShell { shell ->
            assertTrue(shell.exec("sleep 5", 500) is PrivilegedOutcome.Timeout)
            delay(2_100)
            assertEquals(PrivilegedOutcome.Success("again"), shell.exec("echo again", 3_000))
        }
    }

    @Test
    fun outputIsBounded() {
        assumeTrue(hasShell)
        withShell { shell ->
            val outcome = shell.exec("yes | head -c 2000000", 5_000)
            assertTrue(outcome is PrivilegedOutcome.Success)
            assertTrue(outcome.output.length <= 256 * 1024)
        }
    }

    @Test
    fun concurrentCallersReceiveTheirOwnOutput() {
        assumeTrue(hasShell)
        withShell { shell ->
            val results = coroutineScope {
                (1..20).map { n -> async { shell.exec("echo $n", 5_000) } }.awaitAll()
            }
            results.forEachIndexed { index, result ->
                assertEquals(PrivilegedOutcome.Success("${index + 1}"), result)
            }
        }
    }

    @Test
    fun missingSuBinaryReportsUnavailable() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val shell = RootShell(listOf(listOf("definitely-not-a-su-binary")), scope)
        assertTrue(!shell.open())
        assertEquals(PrivilegedOutcome.BinderDisconnected, shell.exec("id -u", 1_000))
        scope.cancel()
    }
}
