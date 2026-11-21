package com.framex.app.gaming

import com.framex.app.privilege.PrivilegeMode
import com.framex.app.utils.FrameXLog

/** Logs how long each deactivation phase took and the running total. */
internal class DeactivationTimer(private val start: Long, private val mode: PrivilegeMode) {

    fun log(name: String, since: Long) {
        val now = System.nanoTime()
        FrameXLog.i(
            "deactivate phase '$name' ${(now - since) / 1_000_000}ms " +
                "(total ${(now - start) / 1_000_000}ms) mode=$mode",
            tag = TAG
        )
    }

    private companion object {
        const val TAG = "GamingModeEngine"
    }
}
