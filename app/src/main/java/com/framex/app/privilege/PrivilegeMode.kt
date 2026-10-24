package com.framex.app.privilege

enum class PrivilegeMode(val key: String) {
    NONE("none"),
    SHIZUKU("shizuku"),
    ROOT("root");

    companion object {
        fun fromKey(key: String?): PrivilegeMode = when (key?.lowercase()) {
            "root" -> ROOT
            "shizuku" -> SHIZUKU
            else -> NONE
        }
    }
}

data class ShellExecutionResult(
    val exitCode: Int,
    val output: String,
    val isSuccess: Boolean = exitCode == 0
)
