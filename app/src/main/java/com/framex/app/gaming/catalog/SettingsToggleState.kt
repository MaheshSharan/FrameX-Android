package com.framex.app.gaming.catalog

import com.framex.app.repository.SettingsRepository

/** [ToggleState] over the Execution Center settings. */
class SettingsToggleState(private val settings: SettingsRepository) : ToggleState {

    override fun isOn(toggle: Toggle): Boolean = when (toggle) {
        Toggle.ALWAYS -> true
        Toggle.MONSTER_MODE -> settings.vivoMonsterMode.value
        Toggle.THERMAL_UNLOCK -> settings.disableThermalThrottling.value
        Toggle.GAME_HANDSHAKE -> settings.vivoGameHandshake.value
        Toggle.GYRO_PROMOTION -> settings.vivoGyroPromotion.value
        Toggle.TOUCH_OPTIMIZATION -> settings.vivoTouchOptimization.value
        Toggle.FPS_144_UNLOCK -> settings.vivo144FpsUnlock.value
        Toggle.VIP_THREAD -> settings.vivoVipThread.value
        Toggle.PULSE_GAME_PLUS -> pulse(settings.vivoPulseGamePlusMode.value)
        Toggle.PULSE_STANDARD_PROMOTION -> pulse(settings.vivoPulseStandardPromotion.value)
        Toggle.PULSE_SCENE_MORE_FPS -> pulse(settings.vivoPulseSceneMoreFps.value)
    }

    /** Every pulse command also needs the master pulse switch. */
    private fun pulse(own: Boolean): Boolean = settings.vivoMaintenancePulse.value && own
}
