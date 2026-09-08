package com.checkscam.app

import android.app.Application
import com.checkscam.calldetection.CallDetectionService

class CheckScamApplication : Application() {

    val callDetectionService: CallDetectionService by lazy { CallDetectionService(this) }
    val callAudioCoordinator: CallAudioCoordinator by lazy {
        CallAudioCoordinator(this, callDetectionService)
    }

    override fun onCreate() {
        super.onCreate()
    }
}