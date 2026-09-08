package com.checkscam.calldetection

fun interface CallDetectionListener {
    fun onCallStateChanged(state: CallState, packageName: String?)
}
