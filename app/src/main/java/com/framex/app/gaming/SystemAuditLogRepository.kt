package com.framex.app.gaming

import com.framex.app.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemAuditLogRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val _logs = MutableStateFlow<List<SystemAuditLog>>(emptyList())
    val logs: StateFlow<List<SystemAuditLog>> = _logs.asStateFlow()

    fun addLog(action: String, details: String, status: LogStatus) {
        if (!settingsRepository.auditLoggingEnabled.value) return
        val entry = SystemAuditLog(action = action, details = details, status = status)
        _logs.update { (listOf(entry) + it).take(100) }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
