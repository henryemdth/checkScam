package com.checkscam.calldetection

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CallStateManager(private val listener: CallDetectionListener) {

    private val _state = MutableStateFlow(CallState.IDLE)
    private val _activePackage = MutableStateFlow<String?>(null)

    val stateFlow: StateFlow<CallState> = _state.asStateFlow()
    val activePackageFlow: StateFlow<String?> = _activePackage.asStateFlow()

    val state: CallState get() = _state.value
    val activePackageName: String? get() = _activePackage.value

    fun onStateReported(newState: CallState, packageName: String?) {
        val validTransition = validateTransition(state, newState)
        if (!validTransition) {
            Log.w(TAG, "Ignoring invalid transition: $state -> $newState (pkg=$packageName)")
            return
        }
        Log.d(TAG, "Transition: $state -> $newState (pkg=$packageName)")
        _state.value = newState
        _activePackage.value = if (newState == CallState.IDLE) null else packageName
        listener?.onCallStateChanged(state, activePackageName)
    }

    fun reset() {
        _state.value = CallState.IDLE
        _activePackage.value = null
        listener?.onCallStateChanged(CallState.IDLE, null)
    }

    private fun validateTransition(from: CallState, to: CallState): Boolean {
        return when (from) {
            CallState.IDLE -> to == CallState.INCOMING || to == CallState.IN_PROGRESS
            CallState.INCOMING -> to == CallState.IN_PROGRESS || to == CallState.ENDED || to == CallState.IDLE
            CallState.IN_PROGRESS -> to == CallState.ENDED || to == CallState.IDLE
            CallState.ENDED -> to == CallState.IDLE
        }
    }

    companion object {
        private const val TAG = "CallStateManager"
    }
}
