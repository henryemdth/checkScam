package com.checkscam.diarization

enum class Speaker {
    SPEAKER_A,
    SPEAKER_B,
    UNKNOWN
}

data class DiarizedSegment(
    val speaker: Speaker,
    val text: String,
    val startMs: Long,
    val endMs: Long
)

interface DiarizationEngine {
    fun processTranscript(segments: List<com.checkscam.stt.TranscriptSegment>): List<DiarizedSegment>
    fun reset()
}
