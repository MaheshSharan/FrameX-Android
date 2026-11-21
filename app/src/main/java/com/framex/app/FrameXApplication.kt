package com.framex.app

import android.app.Application
import com.framex.app.gaming.GamingModeEngine
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.framex.app.permissions.PermissionsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FrameXApplication : Application() {

    @Inject
    lateinit var privilegeManager: com.framex.app.privilege.PrivilegeManager

    @Inject
    lateinit var gamingModeEngine: GamingModeEngine

    @Inject
    lateinit var permissionsManager: PermissionsManager

    override fun onCreate() {
        super.onCreate()
        com.framex.app.utils.CrashHandler.init(this)
        com.framex.app.gaming.GamingChannels.deleteLegacy(this)
        privilegeManager.start()
        // Grants change in system screens; RESUME fires when the app comes back from one,
        // including translucent dialogs that never stop the activity.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    privilegeManager.refresh()
                    permissionsManager.refresh()
                }
            }
        )
        // Recover gaming mode state if the process was killed while mode was active.
        gamingModeEngine.recoverPersistedState()
    }
}
