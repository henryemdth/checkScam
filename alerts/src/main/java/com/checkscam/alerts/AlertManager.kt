package com.checkscam.alerts

import com.checkscam.classifier.FraudSynthesized
import com.checkscam.classifier.RiskLevel

interface AlertListener {
    fun onScamAlert(result: FraudSynthesized)
}

class AlertManager {

    private var listener: AlertListener? = null

    fun setListener(listener: AlertListener) {
        this.listener = listener
    }

    fun showAlert(result: FraudSynthesized) {
        val dangerous = result.isScam || result.riskLevel != RiskLevel.LOW
        if (dangerous) {
            listener?.onScamAlert(result)
        }
    }

    fun dismissAlert() {
        // TODO: Dismiss active alert notification
    }
}