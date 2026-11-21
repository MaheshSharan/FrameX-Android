package com.framex.app.privilege.root

import com.framex.app.privilege.PrivilegedOutcome
import com.framex.app.utils.FrameXLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * A persistent root shell. One `su` process is kept open so a 1 Hz sampler does not pay a
 * process spawn per read.
 *
 * Commands are serialized by [mutex]; a caller that cannot get the shell within its own
 * deadline receives [PrivilegedOutcome.Timeout] instead of waiting forever. A command that
 * outlives its deadline cannot be cancelled on its own inside a shell, so the whole process is
 * killed and a fresh one is started by the next call. Owner: `RootBackend`, which closes the
 * shell when root mode is left.
 *
 * @param candidates `su` invocations to try in order; the first that answers as uid 0 is kept.
 */
internal class RootShell(
    private val candidates: List<List<String>>,
    private val scope: CoroutineScope,
    private val expectedUid: String = ROOT_UID
) {
    private class Session(
        val process: Process,
        val writer: BufferedWriter,
        val lines: Channel<String>,
        val readerJob: Job,
        val nonce: String
    )

    private val mutex = Mutex()
    private val sequence = AtomicLong()
    private var session: Session? = null
    private var preferred: List<String>? = null
    private var lastSpawnFailureNs = 0L

    /** Starts the shell if needed and reports whether root is available. */
    suspend fun open(): Boolean = mutex.withLock { ensureSession() != null }

    suspend fun exec(command: String, timeoutMs: Long): PrivilegedOutcome {
        val outcome = withTimeoutOrNull(timeoutMs + QUEUE_SLACK_MS) {
            mutex.withLock { runLocked(command, timeoutMs) }
        }
        if (outcome == null) FrameXLog.w("Root command queue deadline exceeded", tag = TAG)
        return outcome ?: PrivilegedOutcome.Timeout(timeoutMs)
    }

    suspend fun close() = mutex.withLock { destroySession() }

    private suspend fun runLocked(command: String, timeoutMs: Long): PrivilegedOutcome {
        val active = ensureSession() ?: return PrivilegedOutcome.BinderDisconnected
        return try {
            send(active, command, timeoutMs)
        } catch (e: CancellationException) {
            // The reply may still arrive; reusing the shell would pair it with the wrong command.
            destroySession()
            throw e
        }
    }

    private suspend fun send(active: Session, command: String, timeoutMs: Long): PrivilegedOutcome {
        val marker = "__FX_${active.nonce}_${sequence.incrementAndGet()}"
        try {
            active.writer.write(RootShellProtocol.payload(command, marker))
            active.writer.flush()
        } catch (e: IOException) {
            FrameXLog.w("Root shell write failed", e, tag = TAG)
            destroySession()
            return PrivilegedOutcome.BinderDisconnected
        }
        val result = withTimeoutOrNull(timeoutMs) { collectReply(active, marker) }
        if (result == null) {
            FrameXLog.w("Root command timed out after ${timeoutMs}ms; restarting shell", tag = TAG)
            destroySession()
            return PrivilegedOutcome.Timeout(timeoutMs)
        }
        if (result === PrivilegedOutcome.BinderDisconnected) destroySession()
        return result
    }

    private suspend fun collectReply(active: Session, marker: String): PrivilegedOutcome {
        val output = StringBuilder()
        while (true) {
            val line = active.lines.receiveCatching().getOrNull()
                ?: return PrivilegedOutcome.BinderDisconnected
            val hit = RootShellProtocol.matchMarker(line, marker)
            if (hit == null) {
                appendBounded(output, line)
            } else {
                if (hit.textBefore.isNotEmpty()) appendBounded(output, hit.textBefore)
                return PrivilegedOutcome.fromExit(hit.exitCode, output.toString().trim())
            }
        }
    }

    private fun appendBounded(output: StringBuilder, line: String) {
        if (output.length + line.length + 1 > MAX_OUTPUT_CHARS) return
        output.append(line).append('\n')
    }

    private suspend fun ensureSession(): Session? {
        session?.takeIf { it.process.isAlive }?.let { return it }
        destroySession()
        val sinceFailureMs = (System.nanoTime() - lastSpawnFailureNs) / NANOS_PER_MS
        if (lastSpawnFailureNs != 0L && sinceFailureMs < RESPAWN_BACKOFF_MS) return null

        val ordered = listOfNotNull(preferred) + candidates.filter { it != preferred }
        for (candidate in ordered) {
            val opened = spawn(candidate)
            if (opened != null && verifyRoot(opened)) {
                preferred = candidate
                session = opened
                return opened
            }
            if (opened != null) destroy(opened)
        }
        lastSpawnFailureNs = System.nanoTime()
        return null
    }

    private fun spawn(command: List<String>): Session? {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val lines = Channel<String>(READER_CAPACITY)
            val readerJob = scope.launch(Dispatchers.IO) { pumpLines(process, lines) }
            Session(
                process,
                process.outputStream.bufferedWriter(),
                lines,
                readerJob,
                UUID.randomUUID().toString().replace("-", "")
            )
        } catch (e: IOException) {
            FrameXLog.w("Could not start ${command.joinToString(" ")}", e, tag = TAG)
            null
        }
    }

    private suspend fun pumpLines(process: Process, lines: Channel<String>) {
        try {
            process.inputStream.bufferedReader().useLines { sequence ->
                for (line in sequence) lines.send(line)
            }
        } catch (e: IOException) {
            FrameXLog.d("Root shell stream closed: ${e.message}", tag = TAG)
        } finally {
            lines.close()
        }
    }

    /** A shell that starts is not proof of root; it must answer as uid 0. */
    private suspend fun verifyRoot(candidate: Session): Boolean {
        val outcome = send(candidate, "id -u", VERIFY_TIMEOUT_MS)
        return outcome is PrivilegedOutcome.Success && outcome.output.trim() == expectedUid
    }

    private fun destroySession() {
        session?.let { destroy(it) }
        session = null
    }

    private fun destroy(target: Session) {
        target.process.destroyForcibly()
        target.readerJob.cancel()
        target.lines.close()
        try {
            target.writer.close()
        } catch (e: IOException) {
            FrameXLog.d("Root shell writer already closed: ${e.message}", tag = TAG)
        }
    }

    private companion object {
        const val TAG = "RootShell"
        const val ROOT_UID = "0"
        const val QUEUE_SLACK_MS = 2_000L
        const val RESPAWN_BACKOFF_MS = 2_000L
        const val NANOS_PER_MS = 1_000_000L
        const val MAX_OUTPUT_CHARS = 256 * 1024
        const val READER_CAPACITY = 4096

        /** The first request may show the root manager's grant dialog, so allow time to answer. */
        const val VERIFY_TIMEOUT_MS = 30_000L
    }
}
