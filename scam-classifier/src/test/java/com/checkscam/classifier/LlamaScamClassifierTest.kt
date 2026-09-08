package com.checkscam.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaScamClassifierTest {

    private class FakeEngine(
        var result: String = ""
    ) : LlamaEngine {
        var lastPrompt: String = ""
        var released = false

        override fun initialize(modelPath: String, grammarPath: String, nCtx: Int) {
            // no native state; init is a no-op for the fake
        }

        override fun classify(prompt: String): String {
            lastPrompt = prompt
            return result
        }

        override fun reset() = Unit

        override fun release() {
            released = true
        }
    }

    private val json =
        """{"source_type": "STREAM_ASR", "simulated_raw_text": "texto", "rationale": "urgencia", "is_scam": true, "risk_level": "HIGH", "scam_type": "Phishing / Suplantación Bancaria"}"""

    @Test
    fun `classify wires prompt builder and parser to native output`() {
        val engine = FakeEngine(result = json)
        val classifier = LlamaScamClassifier(engine)

        val result = classifier.classify("<SPEAKER_B> holas")

        assertTrue(engine.lastPrompt.contains("<SPEAKER_B> holas"))
        assertTrue(engine.lastPrompt.contains("<|begin_of_text|>"))
        assertTrue(result.isScam)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(ScamType.BANK_IMPERSONATION, result.scamType)
    }

    @Test
    fun `classify passes primary model through prompt builder`() {
        val engine = FakeEngine(result = json)
        var seenPrompt = ""
        val classifier = LlamaScamClassifier(
            engine = engine,
            promptBuilder = { text -> "PROMPT[$text]" }
        )

        classifier.classify("hola mundo")

        assertEquals("PROMPT[hola mundo]", engine.lastPrompt)
    }

    @Test
    fun `classify tolerates empty native output`() {
        val engine = FakeEngine(result = "")
        val classifier = LlamaScamClassifier(engine)

        val result = classifier.classify("hola")

        assertFalse(result.isScam)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals(ScamType.NONE, result.scamType)
    }

    @Test
    fun `classify tolerates fenced native output`() {
        val engine = FakeEngine(result = "```json\n$json\n```")
        val classifier = LlamaScamClassifier(engine)

        val result = classifier.classify("hola")

        assertTrue(result.isScam)
        assertEquals(ScamType.BANK_IMPERSONATION, result.scamType)
    }

    @Test
    fun `classify never exposes exception on malformed native output`() {
        val engine = FakeEngine(result = "esto no es json")
        val classifier = LlamaScamClassifier(engine)

        val result = classifier.classify("hola")

        assertFalse(result.isScam)
        assertEquals(RiskLevel.LOW, result.riskLevel)
    }

    @Test
    fun `classify accepts injected output parser`() {
        val engine = FakeEngine(result = "anything")
        val classifier = LlamaScamClassifier(
            engine = engine,
            outputParser = { FraudSynthesized(isScam = true, riskLevel = RiskLevel.CRITICAL) }
        )

        val result = classifier.classify("hola")

        assertTrue(result.isScam)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
    }

    @Test
    fun `release forwards to engine`() {
        val engine = FakeEngine(result = json)
        val classifier = LlamaScamClassifier(engine)

        classifier.release()

        assertTrue(engine.released)
    }
}