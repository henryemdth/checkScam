package com.checkscam.stt

import android.content.Context
import android.util.Log

class WhisperEngineImpl(
    context: Context,
    assetFileName: String = DEFAULT_MODEL_ASSET
) : WhisperEngine {

    @Volatile
    private var nativeInitialized = false

    private val modelCopyPath: String by lazy {
        AssetModelCopier.copyModelToInternalStorage(context, assetFileName)
    }

    init {
        System.loadLibrary("stt-engine")
    }

    override fun initialize(modelPath: String) {
        check(!nativeInitialized) { "WhisperEngine already initialized" }
        if (nativeInitialize(modelPath) == 0L) {
            throw IllegalStateException("Failed to initialize whisper model at $modelPath")
        }
        nativeInitialized = true
        Log.d(TAG, "whisper model initialized from $modelPath")
    }

    fun initialize(): Boolean {
        return try {
            initialize(modelCopyPath)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed", e)
            false
        }
    }

    override fun transcribeChunk(pcmData: ShortArray): String {
        check(nativeInitialized) { "WhisperEngine not initialized" }
        if (pcmData.isEmpty()) return ""
        return nativeTranscribe(pcmData)
    }

    override fun reset() {
        if (nativeInitialized) {
            nativeReset()
        }
    }

    override fun release() {
        if (nativeInitialized) {
            nativeRelease()
            nativeInitialized = false
        }
    }

    private external fun nativeInitialize(modelPath: String): Long
    private external fun nativeTranscribe(pcmData: ShortArray): String
    private external fun nativeReset()
    private external fun nativeRelease()

    companion object {
        private const val TAG = "WhisperEngineImpl"
        private const val DEFAULT_MODEL_ASSET = "ggml-base.bin"
    }
}
