package com.checkscam.stt

interface WhisperEngine {
    fun initialize(modelPath: String)
    fun transcribeChunk(pcmData: ShortArray): String
    fun reset()
    fun release()
}