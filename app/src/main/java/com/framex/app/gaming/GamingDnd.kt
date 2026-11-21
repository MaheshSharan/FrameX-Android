package com.framex.app.gaming

import android.app.NotificationManager
import android.content.Context

/** Do Not Disturb toggling, only when the person has granted notification policy access. */
internal object GamingDnd {
    fun enable(context: Context) = setFilter(context, NotificationManager.INTERRUPTION_FILTER_PRIORITY)

    fun restore(context: Context) = setFilter(context, NotificationManager.INTERRUPTION_FILTER_ALL)

    private fun setFilter(context: Context, filter: Int) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(filter)
        }
    }
}
