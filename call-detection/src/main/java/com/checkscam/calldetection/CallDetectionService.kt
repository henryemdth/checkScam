package com.checkscam.calldetection

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow

class CallDetectionService(
    private val context: Context,
    stateManager: CallStateManager? = null
) {

    private val stateManager = stateManager
        ?: CallStateManager(listener = { state, pkg ->
            listener?.onCallStateChanged(state, pkg)
        })

    private var telephonyDetector: TelephonyCallDetector? = null
    private var listener: CallDetectionListener? = null
    private var notificationTextListener: ((String, String) -> Unit)? = null
    private var running = false

    val state: CallState get() = stateManager.state
    val activePackage: String? get() = stateManager.activePackageName
    val stateFlow: StateFlow<CallState> get() = stateManager.stateFlow
    val activePackageFlow: StateFlow<String?> get() = stateManager.activePackageFlow
    val isRunning: Boolean get() = running

    fun setListener(listener: CallDetectionListener) {
        this.listener = listener
    }

    /** Optional live stream of notification text from all apps (see [ThirdPartyCallDetector]). */
    fun setNotificationTextListener(listener: (String, String) -> Unit) {
        this.notificationTextListener = listener
    }

    fun start() {
        if (running) return
        Log.d(TAG, "Starting call detection")

        // The OS-bound services connect asynchronously; the wire is re-read on
        // connect so late-binding connections still get attached.
        CallStateWire.stateManager = stateManager
        CallStateWire.notificationTextSink = notificationTextListener

        telephonyDetector = TelephonyCallDetector(context, stateManager).also { it.start() }

        val accessibilityService = NativeCallAccessibilityService.getInstance()
        accessibilityService?.setStateManager(stateManager)

        val notificationDetector = ThirdPartyCallDetector.getInstance()
        notificationDetector?.setStateManager(stateManager)
        notificationDetector?.setNotificationTextListener { pkg, text ->
            notificationTextListener?.invoke(pkg, text)
        }

        running = true
        Log.d(TAG, "Call detection started")
    }

    fun stop() {
        if (!running) return
        Log.d(TAG, "Stopping call detection")

        telephonyDetector?.stop()
        telephonyDetector = null

        stateManager.reset()
        CallStateWire.clear()
        running = false
        Log.d(TAG, "Call detection stopped")
    }

    companion object {
        private const val TAG = "CallDetectionService"
    }
}
