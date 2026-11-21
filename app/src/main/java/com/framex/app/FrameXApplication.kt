package com.framex.app

import android.app.Application
import com.framex.app.gaming.GamingModeEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FrameXApplication : Application() {

    @Inject
    lateinit var privilegeManager: com.framex.app.privilege.PrivilegeManager

    @Inject
    lateinit var gamingModeEngine: GamingModeEngine

    override fun onCreate() {
        super.onCreate()
        com.framex.app.utils.CrashHandler.init(this)
        com.framex.app.gaming.GamingChannels.deleteLegacy(this)
        privilegeManager.start()
        // Recover gaming mode state if the process was killed while mode was active.
        gamingModeEngine.recoverPersistedState()
    }
}
