package com.checkscam.app

import android.app.Application
import com.checkscam.alerts.AlertHistoryStore
import com.checkscam.alerts.AlertManager
import com.checkscam.calldetection.CallDetectionService

class CheckScamApplication : Application() {

    val callDetectionService: CallDetectionService by lazy { CallDetectionService(this) }

    val alertManager: AlertManager by lazy { AlertManager(this) }
    val alertHistoryStore: AlertHistoryStore by lazy { AlertHistoryStore(this) }

    val callAudioCoordinator: CallAudioCoordinator by lazy {
        CallAudioCoordinator(
            context = this,
            callDetectionService = callDetectionService,
            alertManager = alertManager,
            historyStore = alertHistoryStore
        )
    }

    override fun onCreate() {
        super.onCreate()
    }
}