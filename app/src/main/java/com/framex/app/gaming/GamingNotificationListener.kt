package com.framex.app.gaming

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Notification Listener Service that intercepts and cancels incoming
 * notifications while Gaming Mode is active.
 *
 * OriginOS sometimes bypasses system DND for internal alerts.
 * This listener acts as an independent suppression layer.
 */
@AndroidEntryPoint
class GamingNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // =========================================================================
    // Lifecycle Callbacks
    // =========================================================================

    override fun onListenerConnected() {
        super.onListenerConnected()
        FrameXLog.i("GamingNotificationListener connected", tag = TAG)
        observeGamingModeState()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        FrameXLog.i("GamingNotificationListener destroyed", tag = TAG)
    }

    // =========================================================================
    // Notification Interception
    // =========================================================================

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (shouldSuppressNotification(sbn)) {
            dismissNotificationSafely(sbn.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action required on removal
    }

    // =========================================================================
    // Core Suppression Logic (IDE & GitHub Symbol Navigation)
    // =========================================================================

    private fun observeGamingModeState() {
        serviceScope.launch {
            GamingModeEngine.isActive.collectLatest { isActive ->
                if (isActive) {
                    purgeExistingNotifications()
                }
            }
        }
    }

    /**
     * Walks active notifications and cancels third-party notifications.
     */
    private fun purgeExistingNotifications() {
        try {
            val currentNotifications = activeNotifications ?: return
            for (sbn in currentNotifications) {
                if (shouldSuppressNotification(sbn)) {
                    dismissNotificationSafely(sbn.key)
                }
            }
            FrameXLog.i("Active notifications tray purged for Gaming Mode", tag = TAG)
        } catch (e: Exception) {
            FrameXLog.w("Failed to purge existing notifications", e, tag = TAG)
        }
    }

    private fun shouldSuppressNotification(sbn: StatusBarNotification): Boolean {
        if (!GamingModeEngine.isActive.value) return false
        return !isProtectedNotification(sbn)
    }

    /**
     * Prevents self-cancellation of FrameX foreground or recovery alerts.
     */
    private fun isProtectedNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != packageName) return false
        return sbn.id == GamingModeService.NOTIFICATION_ID ||
               sbn.id == GamingModeEngine.RECOVERY_NOTIFICATION_ID
    }

    private fun dismissNotificationSafely(key: String) {
        try {
            cancelNotification(key)
        } catch (e: Exception) {
            // Non-fatal if notification was already dismissed or service disconnected
            FrameXLog.w("Failed to cancel notification key: $key", e, tag = TAG)
        }
    }

    private companion object {
        const val TAG = "GamingNotifListener"
    }
}