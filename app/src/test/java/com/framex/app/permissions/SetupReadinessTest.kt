package com.framex.app.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupReadinessTest {

    private fun status(vararg granted: AppPermission): Map<AppPermission, Boolean> =
        AppPermission.entries.associateWith { it in granted }

    @Test
    fun ready_whenPrivilegeAndEssentialsAreGranted() {
        assertTrue(isSetupReady(status(AppPermission.OVERLAY, AppPermission.NOTIFICATIONS), privilegeReady = true))
    }

    @Test
    fun notReady_withoutPrivilegeAccess() {
        assertFalse(isSetupReady(status(AppPermission.OVERLAY, AppPermission.NOTIFICATIONS), privilegeReady = false))
    }

    @Test
    fun notReady_whenAnEssentialPermissionIsMissing() {
        assertFalse(isSetupReady(status(AppPermission.OVERLAY), privilegeReady = true))
        assertFalse(isSetupReady(status(AppPermission.NOTIFICATIONS), privilegeReady = true))
    }

    @Test
    fun optionalPermissionsDoNotAffectReadiness() {
        assertTrue(
            isSetupReady(
                status(AppPermission.OVERLAY, AppPermission.NOTIFICATIONS, AppPermission.USAGE_STATS),
                privilegeReady = true
            )
        )
        assertFalse(isSetupReady(status(AppPermission.DND, AppPermission.USAGE_STATS), privilegeReady = true))
    }
}
