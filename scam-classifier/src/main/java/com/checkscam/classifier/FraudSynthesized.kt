package com.checkscam.classifier

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kotlin mirror of `training/schema.py` `FraudSynthesized`.
 *
 * Single source of truth for fields and enum values is the Pydantic model; the
 * Android runtime consumes this mirror. `@SerialName` keeps serialized keys /
 * values byte-identical to the schema (and to the GBNF grammar).
 */
@Serializable
enum class MessageSource(val label: String) {
    @SerialName("STREAM_ASR") STREAM_ASR("STREAM_ASR"),
    @SerialName("WHATSAPP_DIRECT") WHATSAPP_DIRECT("WHATSAPP_DIRECT"),
    @SerialName("WHATSAPP_GROUP") WHATSAPP_GROUP("WHATSAPP_GROUP"),
    @SerialName("SMS") SMS("SMS"),
    @SerialName("EMAIL") EMAIL("EMAIL")
}

@Serializable
enum class RiskLevel(val label: String) {
    @SerialName("LOW") LOW("LOW"),
    @SerialName("MEDIUM") MEDIUM("MEDIUM"),
    @SerialName("HIGH") HIGH("HIGH"),
    @SerialName("CRITICAL") CRITICAL("CRITICAL")
}

@Serializable
enum class ScamType(val label: String) {
    @SerialName("None") NONE("None"),
    @SerialName("Falso Familiar / Extorsión Policial") FAKE_FAMILY_EXTORTION("Falso Familiar / Extorsión Policial"),
    @SerialName("Phishing / Suplantación Bancaria") BANK_IMPERSONATION("Phishing / Suplantación Bancaria"),
    @SerialName("Suplantación Entidad Pública (Aduana / Impuestos)") GOVERNMENT_IMPERSONATION(
        "Suplantación Entidad Pública (Aduana / Impuestos)"
    ),
    @SerialName("Fraude de Telecomunicaciones / Falso Soporte") TELECOM_FRAUD(
        "Fraude de Telecomunicaciones / Falso Soporte"
    )
}

/** Structured JSON output of the on-device scam classifier (GBNF-constrained). */
@Serializable
data class FraudSynthesized(
    @SerialName("source_type")
    val sourceType: MessageSource = MessageSource.STREAM_ASR,
    @SerialName("simulated_raw_text")
    val simulatedRawText: String = "",
    val rationale: String = "",
    @SerialName("is_scam")
    val isScam: Boolean = false,
    @SerialName("risk_level")
    val riskLevel: RiskLevel = RiskLevel.LOW,
    @SerialName("scam_type")
    val scamType: ScamType = ScamType.NONE
)