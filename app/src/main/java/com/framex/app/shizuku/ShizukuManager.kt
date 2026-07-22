package com.framex.app.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor() {

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()
    
    // Mutex to serialize commands through the single persistent binder connection.
    private val commandMutex = Mutex()
    // @Volatile ensures cross-thread visibility for the binder reference.
    @Volatile private var commandRunner: ICommandRunner? = null
    // Guard flag prevents duplicate bindUserService calls during async connection setup.
    @Volatile private var isConnecting = false
    private var userServiceConnection: android.content.ServiceConnection? = null

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isShizukuAvailable.value = true
        checkPermission()
        if (_hasPermission.value) connectUserService()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isShizukuAvailable.value = false
        _hasPermission.value = false
        disconnectUserService()
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            _hasPermission.value = grantResult == PackageManager.PERMISSION_GRANTED
            if (_hasPermission.value) connectUserService()
        }
    }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            
            _isShizukuAvailable.value = Shizuku.pingBinder()
            if (_isShizukuAvailable.value) {
                checkPermission()
                if (_hasPermission.value) connectUserService()
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
                if (_hasPermission.value) connectUserService()
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

    suspend fun executeCommand(command: String): String {
        if (!_isShizukuAvailable.value || !_hasPermission.value) {
            com.framex.app.utils.FrameXLog.w("executeCommand called when Shizuku is unavailable or permitted")
            return ""
        }
        return commandMutex.withLock {
            val runner = commandRunner
            if (runner == null) {
                com.framex.app.utils.FrameXLog.w("CommandRunner service null, attempting reconnect")
                connectUserService()
                return@withLock ""
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.executeCommand(command)
                }
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.e("executeCommand failed: $command", e)
                ""
            }
        }
    }

    suspend fun getThermalTemperatures(): String {
        if (!_isShizukuAvailable.value || !_hasPermission.value) {
            com.framex.app.utils.FrameXLog.w("getThermalTemperatures called when Shizuku is unavailable")
            return ""
        }
        return commandMutex.withLock {
            val runner = commandRunner ?: run {
                com.framex.app.utils.FrameXLog.w("CommandRunner service null in getThermalTemperatures")
                connectUserService()
                return@withLock ""
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.getThermalTemperatures()
                }
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.e("getThermalTemperatures failed", e)
                ""
            }
        }
    }

    suspend fun suspendPackages(packageNames: List<String>, suspended: Boolean): Int {
        if (!_isShizukuAvailable.value || !_hasPermission.value || packageNames.isEmpty()) {
            com.framex.app.utils.FrameXLog.w("suspendPackages skipped: Shizuku unavailable/unpermitted or package list empty")
            return 0
        }
        return commandMutex.withLock {
            val runner = commandRunner ?: run {
                com.framex.app.utils.FrameXLog.w("CommandRunner service null in suspendPackages")
                connectUserService()
                return@withLock 0
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.suspendPackages(packageNames.toTypedArray(), suspended)
                }
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.e("suspendPackages failed", e)
                0
            }
        }
    }

    suspend fun setAppOpMode(packageNames: List<String>, opCode: Int, mode: Int): Int {
        if (!_isShizukuAvailable.value || !_hasPermission.value || packageNames.isEmpty()) {
            com.framex.app.utils.FrameXLog.w("setAppOpMode skipped: Shizuku unavailable/unpermitted or package list empty")
            return 0
        }
        return commandMutex.withLock {
            val runner = commandRunner ?: run {
                com.framex.app.utils.FrameXLog.w("CommandRunner service null in setAppOpMode")
                connectUserService()
                return@withLock 0
            }
            try {
                withContext(Dispatchers.IO) {
                    runner.setAppOpMode(packageNames.toTypedArray(), opCode, mode)
                }
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.e("setAppOpMode failed", e)
                0
            }
        }
    }

    private fun connectUserService() {
        if (commandRunner != null || isConnecting) return
        isConnecting = true
        val args = Shizuku.UserServiceArgs(
            ComponentName("com.framex.app", CommandRunnerService::class.java.name)
        ).daemon(false).processNameSuffix("runner")
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                commandRunner = ICommandRunner.Stub.asInterface(service)
                isConnecting = false
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                commandRunner = null
                isConnecting = false
                if (_isShizukuAvailable.value && _hasPermission.value) {
                    connectUserService()
                }
            }
        }
        userServiceConnection = connection
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("bindUserService failed", e)
            isConnecting = false
        }
    }

    private fun disconnectUserService() {
        val conn = userServiceConnection ?: return
        val args = Shizuku.UserServiceArgs(
            ComponentName("com.framex.app", CommandRunnerService::class.java.name)
        ).daemon(false).processNameSuffix("runner")
        try {
            Shizuku.unbindUserService(args, conn, false)
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("unbindUserService failed", e)
        }
        userServiceConnection = null
        commandRunner = null
        isConnecting = false
    }

    companion object {
        const val REQUEST_CODE_PERMISSION = 1001
    }
}
