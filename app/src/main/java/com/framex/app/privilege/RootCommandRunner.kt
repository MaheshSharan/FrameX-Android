package com.framex.app.privilege

import com.framex.app.utils.FrameXLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootCommandRunner @Inject constructor() {

    companion object {
        private const val TAG = "RootCommandRunner"
        private const val DEFAULT_TIMEOUT_MS = 5000L
        private const val MAX_OUTPUT_BYTES = 32 * 1024
    }

    fun isRootAvailable(): Boolean {
        val result = execute("id", 2500L)
        return result.isSuccess && (result.output.contains("uid=0") || result.output.contains("root"))
    }

    fun execute(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellExecutionResult {
        var process: Process? = null
        return try {
            val proc = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process = proc

            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        val buffer = CharArray(2048)
                        var read: Int
                        while (reader.read(buffer).also { read = it } != -1) {
                            if (outputBuilder.length + read <= MAX_OUTPUT_BYTES) {
                                outputBuilder.append(buffer, 0, read)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            readerThread.start()

            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                readerThread.interrupt()
                FrameXLog.w("Command timed out after ${timeoutMs}ms: $command", tag = TAG)
                return ShellExecutionResult(-1, "Execution timed out")
            }

            readerThread.join(500)
            val exitCode = proc.exitValue()
            ShellExecutionResult(exitCode, outputBuilder.toString().trim())
        } catch (e: Exception) {
            FrameXLog.e("Failed to execute root command: $command", throwable = e, tag = TAG)
            ShellExecutionResult(-1, e.message ?: "Execution error")
        } finally {
            process?.destroy()
        }
    }
}
