package com.framex.app.privilege

enum class PrivilegeMode(val key: String) {
    SHIZUKU("shizuku"),
    ROOT("root");

    companion object {
        /** Unknown, missing and legacy `none` values map to Shizuku, the default mode. */
        fun fromKey(key: String?): PrivilegeMode = if (key.equals(ROOT.key, ignoreCase = true)) ROOT else SHIZUKU
    }
}
