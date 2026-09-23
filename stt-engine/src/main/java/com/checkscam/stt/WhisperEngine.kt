package com.checkscam.stt

interface WhisperEngine {
    /** Strict initialize: throws on failure or when already initialized. */
    fun initialize(modelPath: String)

    /**
     * Best-effort initialize using the engine's own model path. Returns
     * whether the engine ended up ready; must be idempotent (a second call
     * after a successful init returns true without re-initializing).
     */
    fun initialize(): Boolean =
        runCatching { initialize("") }.fold({ true }, { false })

    fun transcribeChunk(pcmData: ShortArray): String
    fun reset()
    fun release()
}