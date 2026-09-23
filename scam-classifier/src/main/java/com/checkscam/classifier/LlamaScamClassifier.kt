package com.checkscam.classifier

/**
 * Production [ScamClassifier]: wires the native [LlamaEngine], the ChatML
 * prompt builder and the resilient output parser together. Each dependency is
 * injectable for unit testing (the native layer is never touched in JVM tests).
 */
class LlamaScamClassifier(
    private val engine: LlamaEngine,
    private val promptBuilder: (String) -> String = ChatMLPromptBuilder::build,
    private val outputParser: (String) -> FraudSynthesized = ScamOutputParser::parse,
    private val onRawOutput: ((String) -> Unit)? = null
) : ScamClassifier {

    override fun initialize(modelPath: String, grammarPath: String) {
        engine.initialize(modelPath, grammarPath)
    }

    override fun classify(diarizedText: String): FraudSynthesized {
        val prompt = promptBuilder(diarizedText)
        val raw = engine.classify(prompt)
        onRawOutput?.invoke(raw)
        return outputParser(raw)
    }

    /** Exact native time-to-first-token (ms) of the last [classify] call, 0 if never measured. */
    fun lastTtftMs(): Long = engine.lastTtftMs()

    override fun release() {
        engine.release()
    }
}