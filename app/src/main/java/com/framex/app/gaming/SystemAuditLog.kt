package com.framex.app.gaming

import androidx.annotation.Keep

enum class LogStatus {
    SUCCESS,
    FAILED,
    INFO
}

@Keep
data class SystemAuditLog(
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val details: String,
    val status: LogStatus
)
