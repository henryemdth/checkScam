package com.checkscam.calldetection

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class ThirdPartyCallDetector : NotificationListenerService() {

    private data class ActiveNotification(val packageName: String, val text: String)

    private var stateManager: CallStateManager? = null
    private var notificationTextListener: ((String, String) -> Unit)? = null
    private val activeNotifications = mutableMapOf<String, ActiveNotification>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName
        val combinedText = combinedTextOf(sbn)

        notificationTextListener?.invoke(packageName, combinedText)

        if (!SupportedApps.isSupported(packageName)) return

        Log.d(TAG, "Notification from $packageName: $combinedText")

        activeNotifications[sbn.key] = ActiveNotification(packageName, combinedText)

        if (SupportedApps.matchIncomingCallKeyword(packageName, combinedText)) {
            Log.d(TAG, "Incoming call detected from $packageName")
            stateManager?.onStateReported(CallState.INCOMING, packageName)
        } else if (SupportedApps.matchActiveCallKeyword(packageName, combinedText)) {
            Log.d(TAG, "Active call detected from $packageName")
            stateManager?.onStateReported(CallState.IN_PROGRESS, packageName)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName

        notificationTextListener?.invoke(packageName, "notification removed")

        if (!SupportedApps.isSupported(packageName)) return

        activeNotifications.remove(sbn.key)
        Log.d(TAG, "Notification removed from $packageName")

        val stillActive = activeNotifications.values.any { notification ->
            SupportedApps.matchIncomingCallKeyword(notification.packageName, notification.text) ||
                SupportedApps.matchActiveCallKeyword(notification.packageName, notification.text)
        }

        if (!stillActive && stateManager?.state != CallState.IDLE) {
            Log.d(TAG, "Call likely ended (no active call notification remains from $packageName)")
            stateManager?.onStateReported(CallState.ENDED, packageName)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun setStateManager(manager: CallStateManager) {
        this.stateManager = manager
    }

    /** Optional live stream of every posted/removed notification, regardless of supported-app gate. */
    fun setNotificationTextListener(listener: (String, String) -> Unit) {
        this.notificationTextListener = listener
    }

    companion object {
        private const val TAG = "ThirdPartyCallDetector"
        private const val MAX_TEXT_CHARS = 300

        private fun combinedTextOf(sbn: StatusBarNotification): String {
            val extras = sbn.notification.extras
            return formatNotificationText(
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            )
        }

        internal fun formatNotificationText(
            title: String,
            text: String,
            bigText: String,
            maxChars: Int = MAX_TEXT_CHARS
        ): String = "$title $text $bigText".trim().take(maxChars)

        private var instance: ThirdPartyCallDetector? = null

        fun getInstance(): ThirdPartyCallDetector? = instance
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        // Pick up wiring even if [CallDetectionService.start] ran before this
        // listener bound (system-side async bind).
        stateManager = CallStateWire.stateManager
        notificationTextListener = CallStateWire.notificationTextSink
        if (stateManager != null || notificationTextListener != null) {
            Log.d(TAG, "Attached to CallStateWire")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }
}
