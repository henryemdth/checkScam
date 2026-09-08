package com.checkscam.classifier

/**
 * Production [ScamClassifier]: wires the native [LlamaEngine], the ChatML
 * prompt builder and the resilient output parser together. Each dependency is
 * injectable for unit testing (the native layer is never touched in JVM tests).
 */
class LlamaScamClassifier(
    private val engine: LlamaEngine,
    private val promptBuilder: (String) -> String = ChatMLPromptBuilder::build,
    private val outputParser: (String) -> FraudSynthesized = ScamOutputParser::parse
) : ScamClassifier {

    override fun initialize(modelPath: String, grammarPath: String) {
        engine.initialize(modelPath, grammarPath)
    }

    override fun classify(diarizedText: String): FraudSynthesized {
        val prompt = promptBuilder(diarizedText)
        val raw = engine.classify(prompt)
        return outputParser(raw)
    }

    override fun release() {
        engine.release()
    }
}