package com.checkscam.calldetection

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log

class TelephonyCallDetector(
    context: Context,
    private val stateManager: CallStateManager
) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var phoneStateListener: PhoneStateListener? = null

    @Suppress("DEPRECATION")
    fun start() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleCallState(state)
            }
        }
        @Suppress("DEPRECATION")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        Log.d(TAG, "TelephonyCallDetector started")
    }

    fun stop() {
        @Suppress("DEPRECATION")
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        phoneStateListener = null
        Log.d(TAG, "TelephonyCallDetector stopped")
    }

    private fun handleCallState(state: Int) {
        val callState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> CallState.INCOMING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallState.IN_PROGRESS
            TelephonyManager.CALL_STATE_IDLE -> {
                if (stateManager.state == CallState.IN_PROGRESS ||
                    stateManager.state == CallState.INCOMING
                ) {
                    CallState.ENDED
                } else {
                    CallState.IDLE
                }
            }
            else -> return
        }
        Log.d(TAG, "Telephony state: $state -> $callState")
        stateManager.onStateReported(callState, NATIVE_PHONE_PACKAGE)
    }

    companion object {
        private const val TAG = "TelephonyCallDetector"
        const val NATIVE_PHONE_PACKAGE = "com.android.phone"
    }
}
