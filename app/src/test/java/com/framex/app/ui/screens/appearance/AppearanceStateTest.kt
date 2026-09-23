package com.framex.app.ui.screens.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceStateTest {

    @Test
    fun defaultStateHasNoChanges() {
        val state = AppearanceUiState()
        assertFalse("Default state should not have pending changes", state.hasChanges)
    }

    @Test
    fun opacityChangeTriggersHasChanges() {
        val state = AppearanceUiState(savedOpacity = 0.75f, opacity = 0.5f)
        assertTrue("Opacity difference should trigger hasChanges", state.hasChanges)
    }

    @Test
    fun scaleChangeTriggersHasChanges() {
        val stateEqual = AppearanceUiState(savedScale = 1.0f, scale = 1.005f)
        assertFalse("Scale difference <= 0.01 should not trigger hasChanges", stateEqual.hasChanges)

        val stateChanged = AppearanceUiState(savedScale = 1.0f, scale = 1.1f)
        assertTrue("Scale difference > 0.01 should trigger hasChanges", stateChanged.hasChanges)
    }

    @Test
    fun colorIndexChangeTriggersHasChanges() {
        val state = AppearanceUiState(savedColorIndex = 0, colorIndex = 2)
        assertTrue("Color index difference should trigger hasChanges", state.hasChanges)
    }

    @Test
    fun containerIndicesChangeTriggersHasChanges() {
        val bgState = AppearanceUiState(savedBgColorIndex = 0, bgColorIndex = 1)
        assertTrue("BgColor index difference should trigger hasChanges", bgState.hasChanges)

        val borderState = AppearanceUiState(savedBorderColorIndex = 0, borderColorIndex = 2)
        assertTrue("BorderColor index difference should trigger hasChanges", borderState.hasChanges)

        val textState = AppearanceUiState(savedTextColorIndex = 0, textColorIndex = 3)
        assertTrue("TextColor index difference should trigger hasChanges", textState.hasChanges)

        val cpuState = AppearanceUiState(savedCpuHotWarning = false, cpuHotWarning = true)
        assertTrue("CpuHotWarning difference should trigger hasChanges", cpuState.hasChanges)
    }

    @Test
    fun optionsHaveCorrectCount() {
        assertEquals(4, BackgroundOption.entries.size)
        assertEquals(4, BorderStyleOption.entries.size)
        assertEquals(3, TextSizeOption.entries.size)
        assertEquals(4, TextColorOption.entries.size)
    }
}
