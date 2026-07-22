package com.framex.app

import android.app.Application
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FrameXApplication : Application() {

    @Inject
    lateinit var shizukuManager: ShizukuManager

    @Inject
    lateinit var gamingModeEngine: GamingModeEngine

    override fun onCreate() {
        super.onCreate()
        com.framex.app.utils.CrashHandler.init(this)
        shizukuManager.init()
        // Recover gaming mode state if the process was killed while mode was active.
        gamingModeEngine.recoverPersistedState()
    }
}
