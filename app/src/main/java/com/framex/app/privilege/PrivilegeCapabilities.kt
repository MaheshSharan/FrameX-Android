package com.framex.app.privilege

/**
 * What the active backend can really do on this device. A shell that starts successfully is
 * not proof of privilege: SELinux and file permissions decide, and they differ between a
 * Shizuku shell user and root.
 */
data class PrivilegeCapabilities(
    val uid: Int,
    val seContext: String?,
    val canReadProcStat: Boolean,
    val canWriteSysfs: Boolean
)
