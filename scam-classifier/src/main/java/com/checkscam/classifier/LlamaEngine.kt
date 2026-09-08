package com.checkscam.classifier

/**
 * Low-level native inference contract. All JNI/native pointer details are
 * isolated behind this interface (AGENTS.md §9).
 */
interface LlamaEngine {

    /**
     * Loads the GGUF model and the GBNF grammar exactly once.
     *
     * @param modelPath absolute path to the quantized `.gguf` model.
     * @param grammarPath absolute path to the `.gbnf` grammar (optional: a
     *   missing/unparseable grammar degrades to unconstrained output).
     * @param nCtx token context window (KV cache size).
     */
    fun initialize(modelPath: String, grammarPath: String, nCtx: Int = DEFAULT_N_CTX)

    /** Runs inference on a fully-formed prompt; returns the raw generated text. */
    fun classify(prompt: String): String

    /** Drops KV cache state so the next call starts from a fresh sequence. */
    fun reset()

    /** Frees the native model + context. Safe to call multiple times. */
    fun release()

    companion object {
        const val DEFAULT_N_CTX = 4096
    }
}