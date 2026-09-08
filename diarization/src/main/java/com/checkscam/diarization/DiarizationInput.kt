package com.checkscam.diarization

data class DiarizationInput(
    val text: String,
    val rms: Float,
    val startMs: Long,
    val endMs: Long
)