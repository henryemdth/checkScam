package com.checkscam.calldetection

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class NativeCallAccessibilityService : AccessibilityService() {

    private var stateManager: CallStateManager? = null
    private var currentState: CallState = CallState.IDLE

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            packageNames = NATIVE_DIALER_PACKAGES
        }
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in NATIVE_DIALER_PACKAGES) return

        val eventText = event.text.joinToString(" ").lowercase()
        val className = event.className?.toString()?.lowercase() ?: ""

        val newState = detectCallState(eventText, className)

        if (newState != null && newState != currentState) {
            currentState = newState
            Log.d(TAG, "Detected state: $newState from package: $packageName")
            stateManager?.onStateReported(newState, NATIVE_PHONE_PACKAGE)
        }
    }

    private fun detectCallState(eventText: String, className: String): CallState? {
        val isIncomingKeywords = listOf(
            "incoming", "ringing", "entrante",
            "incomingcallscreen", "incoming_call"
        )
        val isActiveKeywords = listOf(
            "incall", "in_call", "active", "connected",
            "incallscreen", "dialer"
        )

        if (isIncomingKeywords.any { eventText.contains(it) || className.contains(it) }) {
            return CallState.INCOMING
        }
        if (isActiveKeywords.any { eventText.contains(it) || className.contains(it) }) {
            return CallState.IN_PROGRESS
        }

        if (className.contains("dialer") || className.contains("phone")) {
            return if (currentState == CallState.INCOMING || currentState == CallState.IN_PROGRESS) {
                CallState.IN_PROGRESS
            } else {
                null
            }
        }

        return null
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
    }

    fun setStateManager(manager: CallStateManager) {
        this.stateManager = manager
    }

    companion object {
        private const val TAG = "NativeCallAccessSvc"
        const val NATIVE_PHONE_PACKAGE = "com.android.phone"
        private val NATIVE_DIALER_PACKAGES = arrayOf(
            "com.android.phone",
            "com.android.dialer",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.sec.android.app.dialer"
        )

        private var instance: NativeCallAccessibilityService? = null

        fun getInstance(): NativeCallAccessibilityService? = instance
    }
}
