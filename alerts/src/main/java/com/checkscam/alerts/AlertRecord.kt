package com.checkscam.alerts

import com.checkscam.classifier.FraudSynthesized
import com.checkscam.classifier.MessageSource
import com.checkscam.classifier.RiskLevel
import com.checkscam.classifier.ScamType

/** Domain view of a persisted alert: classifier result + when it happened. */
data class AlertRecord(
    val occurredAtEpochMs: Long,
    val result: FraudSynthesized
)

internal fun AlertRecordEntity.toRecord(): AlertRecord = AlertRecord(
    occurredAtEpochMs = occurredAtEpochMs,
    result = FraudSynthesized(
        sourceType = MessageSource.values().firstOrNull { it.name == sourceType }
            ?: MessageSource.STREAM_ASR,
        simulatedRawText = simulatedRawText,
        rationale = rationale,
        isScam = isScam,
        riskLevel = RiskLevel.values().firstOrNull { it.name == riskLevel } ?: RiskLevel.LOW,
        scamType = ScamType.values().firstOrNull { it.name == scamType } ?: ScamType.NONE
    )
)

internal fun FraudSynthesized.toEntity(occurredAtEpochMs: Long): AlertRecordEntity =
    AlertRecordEntity(
        occurredAtEpochMs = occurredAtEpochMs,
        riskLevel = riskLevel.name,
        scamType = scamType.name,
        sourceType = sourceType.name,
        rationale = rationale,
        simulatedRawText = simulatedRawText,
        isScam = isScam
    )