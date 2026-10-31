package com.framex.app.ui.screens.about

import com.framex.app.gaming.catalog.Toggle

/** Reads the switch a catalog toggle stands for from the screen state. */
fun AboutUiState.isOn(toggle: Toggle): Boolean = when (toggle) {
    Toggle.ALWAYS -> true
    Toggle.MONSTER_MODE -> vivoMonsterMode
    Toggle.THERMAL_UNLOCK -> disableThermalThrottling
    Toggle.GAME_HANDSHAKE -> vivoGameHandshake
    Toggle.GYRO_PROMOTION -> vivoGyroPromotion
    Toggle.TOUCH_OPTIMIZATION -> vivoTouchOptimization
    Toggle.FPS_144_UNLOCK -> vivo144FpsUnlock
    Toggle.VIP_THREAD -> vivoVipThread
    Toggle.PULSE_GAME_PLUS -> vivoPulseGamePlusMode
    Toggle.PULSE_STANDARD_PROMOTION -> vivoPulseStandardPromotion
    Toggle.PULSE_SCENE_MORE_FPS -> vivoPulseSceneMoreFps
}

/** The event that flips the switch a catalog toggle stands for; null for toggles without one. */
fun Toggle.toEvent(enabled: Boolean): AboutUiEvent? = when (this) {
    Toggle.ALWAYS -> null
    Toggle.MONSTER_MODE -> AboutUiEvent.SetVivoMonsterMode(enabled)
    Toggle.THERMAL_UNLOCK -> AboutUiEvent.SetDisableThermalThrottling(enabled)
    Toggle.GAME_HANDSHAKE -> AboutUiEvent.SetVivoGameHandshake(enabled)
    Toggle.GYRO_PROMOTION -> AboutUiEvent.SetVivoGyroPromotion(enabled)
    Toggle.TOUCH_OPTIMIZATION -> AboutUiEvent.SetVivoTouchOptimization(enabled)
    Toggle.FPS_144_UNLOCK -> AboutUiEvent.SetVivo144FpsUnlock(enabled)
    Toggle.VIP_THREAD -> AboutUiEvent.SetVivoVipThread(enabled)
    Toggle.PULSE_GAME_PLUS -> AboutUiEvent.SetVivoPulseGamePlusMode(enabled)
    Toggle.PULSE_STANDARD_PROMOTION -> AboutUiEvent.SetVivoPulseStandardPromotion(enabled)
    Toggle.PULSE_SCENE_MORE_FPS -> AboutUiEvent.SetVivoPulseSceneMoreFps(enabled)
}
