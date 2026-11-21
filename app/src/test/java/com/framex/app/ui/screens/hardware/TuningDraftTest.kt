package com.framex.app.ui.screens.hardware

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningDraftTest {

    @Test
    fun emptyDraftHasNothingToApply() {
        assertTrue(TuningDraft().isEmpty)
    }

    @Test
    fun anySelectionMakesTheDraftApplicable() {
        assertFalse(TuningDraft(cpuFreqKHz = mapOf(0 to 1_800_000L)).isEmpty)
        assertFalse(TuningDraft(cpuGovernor = mapOf(4 to "performance")).isEmpty)
        assertFalse(TuningDraft(gpuFreqKHz = 800_000L).isEmpty)
        assertFalse(TuningDraft(gpuGovernor = "simple_ondemand").isEmpty)
    }
}
