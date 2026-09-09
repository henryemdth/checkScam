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
    private var running = false

    val state: CallState get() = stateManager.state
    val activePackage: String? get() = stateManager.activePackageName
    val stateFlow: StateFlow<CallState> get() = stateManager.stateFlow
    val activePackageFlow: StateFlow<String?> get() = stateManager.activePackageFlow
    val isRunning: Boolean get() = running

    fun setListener(listener: CallDetectionListener) {
        this.listener = listener
    }

    fun start() {
        if (running) return
        Log.d(TAG, "Starting call detection")

        telephonyDetector = TelephonyCallDetector(context, stateManager).also { it.start() }

        val accessibilityService = NativeCallAccessibilityService.getInstance()
        accessibilityService?.setStateManager(stateManager)

        val notificationDetector = ThirdPartyCallDetector.getInstance()
        notificationDetector?.setStateManager(stateManager)

        running = true
        Log.d(TAG, "Call detection started")
    }

    fun stop() {
        if (!running) return
        Log.d(TAG, "Stopping call detection")

        telephonyDetector?.stop()
        telephonyDetector = null

        stateManager.reset()
        running = false
        Log.d(TAG, "Call detection stopped")
    }

    companion object {
        private const val TAG = "CallDetectionService"
    }
}
