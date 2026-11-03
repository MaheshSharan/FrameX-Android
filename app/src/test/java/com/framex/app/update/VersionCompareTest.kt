package com.framex.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun higherNumberWins() {
        assertTrue(VersionCompare.isHigher("1.6.1", "1.6.0"))
        assertTrue(VersionCompare.isHigher("1.10.0", "1.9.9"))
        assertFalse(VersionCompare.isHigher("1.5.9", "1.6.0"))
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertFalse(VersionCompare.isHigher("1.6.0", "1.6.0"))
        assertFalse(VersionCompare.isHigher("1.6.0-beta", "1.6.0-beta"))
    }

    @Test
    fun stableIsNewerThanItsOwnBeta() {
        assertTrue(VersionCompare.isHigher("1.6.0", "1.6.0-beta"))
        assertFalse(VersionCompare.isHigher("1.6.0-beta", "1.6.0"))
    }

    @Test
    fun betaOfALaterNumberIsStillNewer() {
        assertTrue(VersionCompare.isHigher("1.6.1-beta", "1.6.0"))
    }
}
