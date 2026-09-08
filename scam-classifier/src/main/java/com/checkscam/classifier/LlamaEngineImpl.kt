package com.checkscam.classifier

import android.content.Context
import android.util.Log

class LlamaEngineImpl(
    context: Context,
    modelAssetName: String = DEFAULT_MODEL_ASSET,
    grammarAssetName: String = DEFAULT_GRAMMAR_ASSET
) : LlamaEngine {

    @Volatile
    private var nativeInitialized = false

    private val modelCopyPath: String by lazy {
        AssetModelCopier.copyToInternalStorage(context, modelAssetName)
    }

    private val grammarCopyPath: String by lazy {
        AssetModelCopier.copyToInternalStorage(context, grammarAssetName)
    }

    init {
        System.loadLibrary("scam-classifier")
    }

    override fun initialize(modelPath: String, grammarPath: String, nCtx: Int) {
        check(!nativeInitialized) { "LlamaEngine already initialized" }
        if (nativeInitialize(modelPath, grammarPath, nCtx) == 0L) {
            throw IllegalStateException("Failed to initialize llama model at $modelPath")
        }
        nativeInitialized = true
        Log.d(TAG, "llama model initialized from $modelPath")
    }

    fun initialize(): Boolean {
        return try {
            initialize(modelCopyPath, grammarCopyPath)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed", e)
            false
        }
    }

    override fun classify(prompt: String): String {
        check(nativeInitialized) { "LlamaEngine not initialized" }
        if (prompt.isEmpty()) return ""
        return nativeClassify(prompt)
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

    private external fun nativeInitialize(modelPath: String, grammarPath: String, nCtx: Int): Long
    private external fun nativeClassify(prompt: String): String
    private external fun nativeReset()
    private external fun nativeRelease()

    companion object {
        private const val TAG = "LlamaEngineImpl"
        private const val DEFAULT_MODEL_ASSET = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        private const val DEFAULT_GRAMMAR_ASSET = "scam_schema.gbnf"
    }
}