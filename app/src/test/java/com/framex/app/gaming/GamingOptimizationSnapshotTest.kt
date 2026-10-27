package com.framex.app.gaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GamingOptimizationSnapshotTest {

    private fun snapshot(phantom: SettingValue?) = GamingOptimizationSnapshot(
        activeGamePackage = "com.game",
        activeGameUid = 10123,
        timestamp = 42L,
        minRefreshRate = SettingValue("60", existed = true),
        peakRefreshRate = null,
        touchResponseSpeed = SettingValue("", existed = false),
        userPreferredDisplayModeId = null,
        affectedPackages = setOf("com.a", "com.b"),
        monitorPhantomProcs = phantom
    )

    @Test
    fun roundTrip_keepsThePhantomProcessBaseline() {
        val restored = GamingOptimizationSnapshot.fromJson(snapshot(SettingValue("true", existed = true)).toJson())

        assertNotNull(restored)
        assertEquals(SettingValue("true", existed = true), restored!!.monitorPhantomProcs)
        assertEquals(setOf("com.a", "com.b"), restored.affectedPackages)
        assertEquals("com.game", restored.activeGamePackage)
    }

    @Test
    fun snapshotWrittenByAnEarlierBuild_hasNoPhantomBaseline() {
        val legacyJson = """
            {"activeGamePackage":"com.game","activeGameUid":1,"timestamp":1,
             "minRefreshRate":null,"peakRefreshRate":null,"touchResponseSpeed":null,
             "userPreferredDisplayModeId":null,"affectedPackages":["com.a"]}
        """.trimIndent()

        val restored = GamingOptimizationSnapshot.fromJson(legacyJson)

        assertNotNull(restored)
        assertNull(restored!!.monitorPhantomProcs)
        assertEquals(setOf("com.a"), restored.affectedPackages)
    }
}
