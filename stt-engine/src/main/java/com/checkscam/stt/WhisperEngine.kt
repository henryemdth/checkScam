package com.checkscam.stt

data class TranscriptSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float
)

interface WhisperEngine {
    fun initialize(modelPath: String)
    fun transcribeChunk(pcmData: ShortArray): String
    fun reset()
    fun release()
}
