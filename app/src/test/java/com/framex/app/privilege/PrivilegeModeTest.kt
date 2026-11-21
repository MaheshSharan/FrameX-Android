package com.framex.app.privilege

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivilegeModeTest {

    @Test
    fun fromKey_readsKnownKeysCaseInsensitively() {
        assertEquals(PrivilegeMode.ROOT, PrivilegeMode.fromKey("root"))
        assertEquals(PrivilegeMode.ROOT, PrivilegeMode.fromKey("ROOT"))
        assertEquals(PrivilegeMode.SHIZUKU, PrivilegeMode.fromKey("shizuku"))
    }

    @Test
    fun fromKey_mapsMissingLegacyAndUnknownValuesToShizuku() {
        assertEquals(PrivilegeMode.SHIZUKU, PrivilegeMode.fromKey(null))
        assertEquals(PrivilegeMode.SHIZUKU, PrivilegeMode.fromKey("none"))
        assertEquals(PrivilegeMode.SHIZUKU, PrivilegeMode.fromKey("garbage"))
    }
}
