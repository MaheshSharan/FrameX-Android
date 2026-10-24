package com.framex.app.privilege

import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivilegeManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val rootCommandRunner: RootCommandRunner,
    val shizukuManager: ShizukuManager
) {
    private val _currentMode = MutableStateFlow(PrivilegeMode.NONE)
    val currentMode: StateFlow<PrivilegeMode> = _currentMode.asStateFlow()

    init {
        val saved = settingsRepository.appPrefs.getPrivilegeMode()
        _currentMode.value = saved
    }

    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        rootCommandRunner.isRootAvailable()
    }

    fun setMode(mode: PrivilegeMode) {
        _currentMode.value = mode
        settingsRepository.appPrefs.setPrivilegeMode(mode)
    }

    suspend fun executeCommand(command: String, timeoutMs: Long = 5000L): ShellExecutionResult = withContext(Dispatchers.IO) {
        if (_currentMode.value == PrivilegeMode.ROOT) {
            rootCommandRunner.execute(command, timeoutMs)
        } else {
            val result = shizukuManager.executeCommandWithResult(command)
            if (result != null) {
                ShellExecutionResult(result.exitCode, result.output)
            } else {
                ShellExecutionResult(-1, "Privilege provider unavailable or command failed")
            }
        }
    }

    suspend fun readSystemFile(path: String): String = withContext(Dispatchers.IO) {
        if (_currentMode.value == PrivilegeMode.ROOT) {
            val res = rootCommandRunner.execute("cat $path", 2000L)
            if (res.isSuccess) res.output else ""
        } else {
            val output = shizukuManager.executeCommand("cat $path")
            output.trim()
        }
    }
}
