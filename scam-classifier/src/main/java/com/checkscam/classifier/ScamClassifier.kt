package com.checkscam.classifier

/**
 * High-level on-device scam classifier. Consumes the diarized stream from
 * Phase 4 (AGENTS.md §5.4), produces a strict-JSON result backed by the
 * Pydantic `FraudSynthesized` schema.
 */
interface ScamClassifier {

    /** Loads model + GBNF grammar once; must be called before [classify]. */
    fun initialize(modelPath: String, grammarPath: String)

    /**
     * Classifies a diarized text slice (e.g. sliding window of recent turns).
     * Never throws; a failure surfaces as a benign default result so the
     * real-time pipeline is not interrupted.
     */
    fun classify(diarizedText: String): FraudSynthesized

    /** Frees all native resources. Safe to call multiple times. */
    fun release()
}