package com.framex.app.shizuku

import android.content.pm.PackageManager
import com.framex.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import com.framex.app.gaming.LogStatus
import com.framex.app.gaming.SystemAuditLogRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    private val auditLogRepository: SystemAuditLogRepository
) {

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()
    
    // Mutex to serialize commands through the single persistent binder connection.
    private val commandMutex = Mutex()
    // @Volatile ensures cross-thread visibility for the binder reference.
    @Volatile private var commandRunner: ICommandRunner? = null
    // Guard flag prevents duplicate bindUserService calls during async connection setup.
    @Volatile private var isConnecting = false
    private var userServiceConnection: android.content.ServiceConnection? = null
    private var pendingConnection: CompletableDeferred<ICommandRunner?>? = null
    private var firstBindNotBeforeMs = 0L

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isShizukuAvailable.value = true
        auditLogRepository.addLog("Shizuku: Binder Connected", "Version ${Shizuku.getVersion()}", LogStatus.SUCCESS)
        checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isShizukuAvailable.value = false
        _hasPermission.value = false
        auditLogRepository.addLog("Shizuku: Binder Died", "Remote process lost or killed", LogStatus.FAILED)
        disconnectUserService()
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            _hasPermission.value = granted
            auditLogRepository.addLog(
                "Shizuku: Permission",
                if (granted) "Granted by user" else "Denied by user",
                if (granted) LogStatus.SUCCESS else LogStatus.FAILED
            )
        }
    }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            
            _isShizukuAvailable.value = Shizuku.pingBinder()
            firstBindNotBeforeMs = SystemClock.elapsedRealtime() + INITIAL_BIND_DELAY_MS
            if (_isShizukuAvailable.value) {
                checkPermission()
            }
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("Shizuku init error", e)
            _isShizukuAvailable.value = false
        }
    }
    
    fun refreshState() {
        try {
            _isShizukuAvailable.value = Shizuku.pingBinder()
            if (_isShizukuAvailable.value) {
                checkPermission()
            } else {
                _hasPermission.value = false
            }
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("Shizuku refreshState error", e)
            _isShizukuAvailable.value = false
            _hasPermission.value = false
        }
    }

    fun destroy() {
        disconnectUserService()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    fun checkPermission() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            _hasPermission.value = false
            return
        }
        _hasPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
        }
    }

    private suspend fun awaitCommandRunner(): ICommandRunner? {
        commandRunner?.let { return it }
        val initialDelayMs = firstBindNotBeforeMs - SystemClock.elapsedRealtime()
        if (initialDelayMs > 0L) {
            kotlinx.coroutines.delay(initialDelayMs)
        }
        
        var attempts = 0
        while (attempts < MAX_BIND_RETRIES) {
            attempts++
            connectUserService()
            val deferred = pendingConnection ?: return commandRunner
            val runner = withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
            if (runner != null) {
                return runner
            }
            com.framex.app.utils.FrameXLog.w("bindUserService attempt $attempts/$MAX_BIND_RETRIES timed out after ${BIND_TIMEOUT_MS}ms")
            disconnectUserService(remove = true)
            if (attempts < MAX_BIND_RETRIES) {
                kotlinx.coroutines.delay(BIND_RETRY_DELAY_MS)
            }
        }
        com.framex.app.utils.FrameXLog.w("CommandRunner unavailable after $MAX_BIND_RETRIES bind attempts")
        return null
    }

    private suspend fun <T> executeLockedWithDeadline(
        operationName: String,
        timeoutMs: Long = IPC_COMMAND_DEADLINE_MS,
        defaultValue: T,
        block: (ICommandRunner) -> T
    ): T {
        val result = withTimeoutOrNull(timeoutMs) {
            commandMutex.withLock {
                val runner = awaitCommandRunner() ?: run {
                    com.framex.app.utils.FrameXLog.w("CommandRunner unavailable after bind attempt in $operationName")
                    return@withLock defaultValue
                }
                try {
                    withContext(Dispatchers.IO) {
                        block(runner)
                    }
                } catch (e: android.os.DeadObjectException) {
                    com.framex.app.utils.FrameXLog.w("$operationName encountered DeadObjectException; clearing stale binder", e)
                    disconnectUserService(remove = true)
                    refreshState()
                    defaultValue
                } catch (e: android.os.RemoteException) {
                    com.framex.app.utils.FrameXLog.e("$operationName failed with RemoteException", e)
                    defaultValue
                } catch (e: SecurityException) {
                    com.framex.app.utils.FrameXLog.e("$operationName failed with SecurityException", e)
                    refreshState()
                    defaultValue
                } catch (e: Exception) {
                    com.framex.app.utils.FrameXLog.e("$operationName failed", e)
                    defaultValue
                }
            }
        }
        if (result == null) {
            com.framex.app.utils.FrameXLog.w("$operationName timed out waiting for IPC lock/execution after ${timeoutMs}ms")
            return defaultValue
        }
        return result
    }

    suspend fun executeCommand(command: String): String {
        if (!_isShizukuAvailable.value || !_hasPermission.value) {
            com.framex.app.utils.FrameXLog.w("executeCommand called when Shizuku is unavailable or permitted")
            return ""
        }
        return executeLockedWithDeadline(
            operationName = "executeCommand($command)",
            timeoutMs = IPC_COMMAND_DEADLINE_MS,
            defaultValue = ""
        ) { runner ->
            runner.executeCommand(command)
        }
    }

    suspend fun executeCommandWithExitCode(command: String): Int {
        if (!_isShizukuAvailable.value || !_hasPermission.value) {
            com.framex.app.utils.FrameXLog.w("executeCommandWithExitCode called when Shizuku is unavailable or permission is not granted")
            return COMMAND_EXECUTION_FAILED
        }
        return executeLockedWithDeadline(
            operationName = "executeCommandWithExitCode($command)",
            timeoutMs = IPC_COMMAND_DEADLINE_MS,
            defaultValue = COMMAND_EXECUTION_FAILED
        ) { runner ->
            runner.executeCommandWithExitCode(command)
        }
    }

    suspend fun executeCommandWithResult(command: String): CommandResult? {
        if (!_isShizukuAvailable.value || !_hasPermission.value) {
            com.framex.app.utils.FrameXLog.w("executeCommandWithResult called when Shizuku is unavailable or permission is not granted")
            return null
        }
        return executeLockedWithDeadline<CommandResult?>(
            operationName = "executeCommandWithResult($command)",
            timeoutMs = IPC_COMMAND_DEADLINE_MS,
            defaultValue = null
        ) { runner ->
            runner.executeCommandWithResult(command)
        }
    }

    suspend fun readProcStat(): String {
        if (!_isShizukuAvailable.value || !_hasPermission.value) {
            com.framex.app.utils.FrameXLog.w("readProcStat called when Shizuku is unavailable")
            return ""
        }
        return executeLockedWithDeadline(
            operationName = "readProcStat",
            timeoutMs = IPC_TELEMETRY_DEADLINE_MS,
            defaultValue = ""
        ) { runner ->
            runner.readProcStat()
        }
    }

    suspend fun readCpuTelemetry(): String {
        if (!_isShizukuAvailable.value || !_hasPermission.value) {
            return ""
        }
        return executeLockedWithDeadline(
            operationName = "readCpuTelemetry",
            timeoutMs = IPC_TELEMETRY_DEADLINE_MS,
            defaultValue = ""
        ) { runner ->
            runner.readCpuTelemetry()
        }
    }

    suspend fun getThermalTemperatures(): String {
        if (!_isShizukuAvailable.value || !_hasPermission.value) {
            com.framex.app.utils.FrameXLog.w("getThermalTemperatures called when Shizuku is unavailable")
            return ""
        }
        return executeLockedWithDeadline(
            operationName = "getThermalTemperatures",
            timeoutMs = IPC_TELEMETRY_DEADLINE_MS,
            defaultValue = ""
        ) { runner ->
            runner.getThermalTemperatures()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun getSuspendedPackages(packageNames: List<String>): Set<String> {
        // 'cmd package list packages -s' filters system packages, NOT suspended packages.
        // Android does not have a single batch query flag for suspended packages in pm list packages.
        // Returning emptySet() ensures installed OEM and Google bloatware packages are not falsely ignored.
        return emptySet()
    }

    suspend fun suspendPackages(packageNames: List<String>, suspended: Boolean): SuspendResult? {
        if (!_isShizukuAvailable.value || !_hasPermission.value || packageNames.isEmpty()) {
            com.framex.app.utils.FrameXLog.w("suspendPackages skipped: Shizuku unavailable/unpermitted or package list empty")
            return null
        }
        return executeLockedWithDeadline<SuspendResult?>(
            operationName = "suspendPackages(count=${packageNames.size}, suspended=$suspended)",
            timeoutMs = IPC_BATCH_DEADLINE_MS,
            defaultValue = null
        ) { runner ->
            runner.suspendPackages(packageNames.toTypedArray(), suspended)
        }
    }

    suspend fun setAppOpMode(packageNames: List<String>, opCode: Int, mode: Int): Int {
        if (!_isShizukuAvailable.value || !_hasPermission.value || packageNames.isEmpty()) {
            com.framex.app.utils.FrameXLog.w("setAppOpMode skipped: Shizuku unavailable/unpermitted or package list empty")
            return 0
        }
        return executeLockedWithDeadline(
            operationName = "setAppOpMode(count=${packageNames.size}, opCode=$opCode)",
            timeoutMs = IPC_BATCH_DEADLINE_MS,
            defaultValue = 0
        ) { runner ->
            runner.setAppOpMode(packageNames.toTypedArray(), opCode, mode)
        }
    }

    private fun connectUserService() {
        if (commandRunner != null || isConnecting) return

        // OnBinderDeadListener only fires on a graceful Shizuku shutdown.
        // When the OS kills Shizuku abruptly (e.g. Nothing OS "adj 905: remove task" SIGKILL),
        // the binder death signal never arrives, leaving _isShizukuAvailable=true with a dead binder.
        // pingBinder() is the only reliable way to catch this case before we attempt binding.
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            com.framex.app.utils.FrameXLog.w("Shizuku daemon dead (OS-level kill detected via pingBinder). Halting reconnect.")
            _isShizukuAvailable.value = false
            _hasPermission.value = false
            commandRunner = null
            isConnecting = false
            pendingConnection?.complete(null)
            pendingConnection = null
            return
        }

        isConnecting = true
        val deferred = CompletableDeferred<ICommandRunner?>()
        pendingConnection = deferred

        com.framex.app.utils.FrameXLog.w("connectUserService: attempting bindUserService")

        val args = userServiceArgs()

        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (userServiceConnection !== this) return
                com.framex.app.utils.FrameXLog.w("connectUserService: onServiceConnected")
                runCatching {
                    service?.linkToDeath({
                        com.framex.app.utils.FrameXLog.w("CommandRunner IBinder died via DeathRecipient")
                        commandRunner = null
                        isConnecting = false
                        refreshState()
                    }, 0)
                }
                val runner = ICommandRunner.Stub.asInterface(service)
                commandRunner = runner
                isConnecting = false
                pendingConnection?.complete(runner)
                pendingConnection = null
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                if (userServiceConnection !== this) return
                com.framex.app.utils.FrameXLog.w("connectUserService: onServiceDisconnected")
                commandRunner = null
                isConnecting = false
                pendingConnection?.complete(null)
                pendingConnection = null
            }
        }
        userServiceConnection = connection
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("bindUserService failed", e)
            isConnecting = false
            _isShizukuAvailable.value = false
            pendingConnection?.complete(null)
            pendingConnection = null
        }
    }

    private fun disconnectUserService(remove: Boolean = false) {
        val conn = userServiceConnection
        if (conn == null) {
            commandRunner = null
            isConnecting = false
            pendingConnection?.complete(null)
            pendingConnection = null
            return
        }
        try {
            Shizuku.unbindUserService(userServiceArgs(), conn, remove)
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("unbindUserService failed", e)
        }
        userServiceConnection = null
        commandRunner = null
        isConnecting = false
        pendingConnection?.complete(null)
        pendingConnection = null
    }

    private fun userServiceArgs() = Shizuku.UserServiceArgs(
        ComponentName("com.framex.app", CommandRunnerService::class.java.name)
    ).daemon(true)
        .tag(USER_SERVICE_TAG)
        .version(BuildConfig.VERSION_CODE)
        .processNameSuffix("runner")

    companion object {
        const val REQUEST_CODE_PERMISSION = 1001
        private const val BIND_TIMEOUT_MS = 5000L
        private const val INITIAL_BIND_DELAY_MS = 2000L
        private const val MAX_BIND_RETRIES = 3
        private const val BIND_RETRY_DELAY_MS = 500L
        private const val IPC_TELEMETRY_DEADLINE_MS = 6_000L
        private const val IPC_COMMAND_DEADLINE_MS = 15_000L
        private const val IPC_BATCH_DEADLINE_MS = 25_000L
        private const val USER_SERVICE_TAG = "framex-command-runner"
        private const val COMMAND_EXECUTION_FAILED = -1
    }
}
