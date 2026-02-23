package com.framex.app

import android.app.Application
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FrameXApplication : Application() {

    @Inject
    lateinit var shizukuManager: ShizukuManager

    override fun onCreate() {
        super.onCreate()
        shizukuManager.init()
    }
}
