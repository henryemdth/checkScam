package com.checkscam.alerts

import com.checkscam.classifier.RiskLevel

/**
 * Pure mapping from a classifier [RiskLevel] to visual cues used by the
 * notification payload and the Compose dashboard (AGENTS.md §5.5 / §6).
 * Kept as a plain object so it is JVM-unit-testable without Android.
 */
object RiskVisual {

    val CRITICAL_COLOR = 0xFFD32F2F.toInt()
    val HIGH_COLOR = 0xFFF57C00.toInt()
    val MEDIUM_COLOR = 0xFFFBBC05.toInt()
    val LOW_COLOR = 0xFF757575.toInt()

    /** ARGB color used for icons, badges and the notification color. */
    fun argb(risk: RiskLevel): Int = when (risk) {
        RiskLevel.CRITICAL -> CRITICAL_COLOR
        RiskLevel.HIGH -> HIGH_COLOR
        RiskLevel.MEDIUM -> MEDIUM_COLOR
        RiskLevel.LOW -> LOW_COLOR
    }

    /** Severity ordering (0 = lowest) for sorting/badges. */
    fun order(risk: RiskLevel): Int = when (risk) {
        RiskLevel.LOW -> 0
        RiskLevel.MEDIUM -> 1
        RiskLevel.HIGH -> 2
        RiskLevel.CRITICAL -> 3
    }

    /** Short label for the notification title / status. */
    fun label(risk: RiskLevel): String = risk.name
}