package com.framex.app.gaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemPackageListsTest {

    private val reportedSamsungPackages = listOf(
        "com.samsung.android.dialer",
        "com.samsung.android.calendar",
        "com.sec.android.app.camera",
        "com.sec.android.app.clockpackage",
        "com.sec.android.gallery3d",
        "com.sec.android.app.myfiles",
        "com.sec.android.app.samsungapps",
        "com.sec.android.app.vepreload"
    )

    @Test
    fun reportedSamsungPackagesAreKnown() {
        val known = OemPackageResolver.OEM_BLOATED_PACKAGES + OemPackageResolver.OEM_DEEP_FREEZE_PACKAGES
        assertTrue(known.containsAll(reportedSamsungPackages))
    }

    @Test
    fun everydaySamsungAppsAreNeverSuspendedByDefault() {
        val everyday = reportedSamsungPackages - "com.sec.android.app.samsungapps"
        assertTrue(OemPackageResolver.OEM_DEEP_FREEZE_PACKAGES.containsAll(everyday))
        assertFalse(OemPackageResolver.OEM_BLOATED_PACKAGES.any { it in everyday })
    }

    @Test
    fun playStoreIsNeverSuspendedByDefault() {
        val vending = "com.android.vending"
        assertFalse(vending in OemPackageResolver.OEM_BLOATED_PACKAGES)
        assertFalse(vending in OemPackageResolver.GOOGLE_SAFE_TO_SUSPEND)
        assertTrue(vending in OemPackageResolver.CRITICAL_SYSTEM_PACKAGES)
    }

    @Test
    fun criticalPackagesAreNeverOemSuspendCandidates() {
        val overlap = OemPackageResolver.OEM_BLOATED_PACKAGES
            .intersect(OemPackageResolver.CRITICAL_SYSTEM_PACKAGES)
        assertTrue("Critical packages in OEM list: $overlap", overlap.isEmpty())
    }
}
