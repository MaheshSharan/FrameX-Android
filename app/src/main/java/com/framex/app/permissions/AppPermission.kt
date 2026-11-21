package com.framex.app.permissions

import java.util.EnumSet

/** System permissions FrameX asks the user for. Declaration order is the display order. */
enum class AppPermission {
    OVERLAY,
    NOTIFICATIONS,
    USAGE_STATS,
    BATTERY_OPT,
    WRITE_SETTINGS,
    DND,
    NOTIF_LISTENER;

    companion object {
        /** Needed before the overlay is useful; every screen uses this one definition of "ready". */
        val ESSENTIAL: Set<AppPermission> = EnumSet.of(OVERLAY, NOTIFICATIONS)
    }
}
