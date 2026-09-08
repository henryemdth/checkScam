package com.checkscam.classifier

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resilient parser for the LLM's raw output.
 *
 * Normal path: strict decode with [kotlinx.serialization.json.Json] configured
 * to ignore unknown keys and coerce bad values. Fallback path strips accidental
 * markdown fences (` ```json `) and, if the strict parse still fails, does a
 * tolerant field-by-field decode producing a safe default [FraudSynthesized].
 *
 * This parser NEVER throws: a garbage stream maps to a benign default so the
 * real-time pipeline keeps running.
 */
object ScamOutputParser {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val FENCE_RE = Regex(
        """(?s)^\s*(?:```(?:json)?)?\s*(\{.*?\})\s*(?:```)?\s*$"""
    )

    fun parse(raw: String): FraudSynthesized {
        val cleaned = stripFences(raw)

        // Strict path first.
        kotlin.runCatching {
            json.decodeFromString<FraudSynthesized>(cleaned)
        }.onSuccess { return it }

        // Tolerant path: pull a JSON object out and map field-by-field.
        return parseTolerant(cleaned)
    }

    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) {
            // Still remove a stray trailing fence if present.
            return trimmed.removeSuffix("```").trim()
        }
        return FENCE_RE.find(trimmed)?.groupValues?.getOrNull(1) ?: trimmed
    }

    private fun parseTolerant(raw: String): FraudSynthesized {
        val jsonObject: JsonObject? = extractJsonObject(raw)
        if (jsonObject == null) {
            return FraudSynthesized(rationale = raw.take(2048))
        }

        val sourceType = jsonObject["source_type"]?.jsonPrimitive?.contentOrNull
            ?.let(MessageSource::fromSerialized) ?: MessageSource.STREAM_ASR
        val rawText = jsonObject["simulated_raw_text"]?.jsonPrimitive?.contentOrNull ?: ""
        val rationale = jsonObject["rationale"]?.jsonPrimitive?.contentOrNull ?: ""
        val isScam = jsonObject["is_scam"]?.jsonPrimitive?.booleanOrNull ?: false
        val riskLevel = jsonObject["risk_level"]?.jsonPrimitive?.contentOrNull
            ?.let(RiskLevel::fromSerialized) ?: RiskLevel.LOW
        val scamType = jsonObject["scam_type"]?.jsonPrimitive?.contentOrNull
            ?.let(ScamType::fromSerialized) ?: ScamType.NONE

        return FraudSynthesized(
            sourceType = sourceType,
            simulatedRawText = rawText.take(4096),
            rationale = rationale.take(4096),
            isScam = isScam,
            riskLevel = riskLevel,
            scamType = scamType
        )
    }

    /** Locates the first JSON object `{...}` in arbitrary text. */
    private fun extractJsonObject(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
        }.getOrNull()
    }
}

private fun MessageSource.Companion.fromSerialized(value: String?): MessageSource? {
    if (value.isNullOrBlank()) return null
    return MessageSource.entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
        ?: MessageSource.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
}

private fun RiskLevel.Companion.fromSerialized(value: String?): RiskLevel? {
    if (value.isNullOrBlank()) return null
    return RiskLevel.entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
        ?: RiskLevel.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
}

private fun ScamType.Companion.fromSerialized(value: String?): ScamType? {
    if (value.isNullOrBlank()) return null
    return ScamType.entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
        ?: ScamType.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
}