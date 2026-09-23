package com.checkscam.app

import android.app.Application
import com.checkscam.alerts.AlertHistoryStore
import com.checkscam.alerts.AlertManager
import com.checkscam.app.observability.AppLogBuffer
import com.checkscam.app.observability.LogStage
import com.checkscam.calldetection.CallDetectionService

class CheckScamApplication : Application() {

    val callDetectionService: CallDetectionService by lazy {
        CallDetectionService(this).also {
            it.setNotificationTextListener { packageName, text ->
                appLogBuffer.log(LogStage.NOTIFICATIONS, "[$packageName] $text")
                // Feed messaging notifications to the scam classifier
                callAudioCoordinator.analyzeNotificationText(packageName, text)
            }
        }
    }

    val appLogBuffer: AppLogBuffer by lazy { AppLogBuffer() }

    val alertManager: AlertManager by lazy {
        AlertManager(
            context = this,
            onDecision = { appLogBuffer.log(LogStage.ALERTS, it) }
        )
    }
    val alertHistoryStore: AlertHistoryStore by lazy { AlertHistoryStore(this) }

    val callAudioCoordinator: CallAudioCoordinator by lazy {
        CallAudioCoordinator(
            context = this,
            callDetectionService = callDetectionService,
            alertManager = alertManager,
            historyStore = alertHistoryStore,
            logBuffer = appLogBuffer,
            rawOutputSink = { raw -> appLogBuffer.log(LogStage.CLASSIFIER, "RAW $raw") }
        )
    }

    override fun onCreate() {
        super.onCreate()
    }
}