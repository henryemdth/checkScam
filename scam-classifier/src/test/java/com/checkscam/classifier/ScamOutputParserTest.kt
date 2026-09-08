package com.checkscam.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamOutputParserTest {

    private val validJson =
        """{"source_type": "STREAM_ASR", "simulated_raw_text": "hola le llamo de la central de tigo su numero sera bloqueado", "rationale": "El estafador usa urgencia para obtener datos.", "is_scam": true, "risk_level": "HIGH", "scam_type": "Fraude de Telecomunicaciones / Falso Soporte"}"""

    @Test
    fun `parses valid JSON with Spanish enum values`() {
        val result = ScamOutputParser.parse(validJson)

        assertEquals(MessageSource.STREAM_ASR, result.sourceType)
        assertEquals("hola le llamo de la central de tigo su numero sera bloqueado", result.simulatedRawText)
        assertEquals("El estafador usa urgencia para obtener datos.", result.rationale)
        assertTrue(result.isScam)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(ScamType.TELECOM_FRAUD, result.scamType)
    }

    @Test
    fun `parses JSON wrapped in markdown fences`() {
        val fenced = "```json\n$validJson\n```"
        val result = ScamOutputParser.parse(fenced)

        assertTrue(result.isScam)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(ScamType.TELECOM_FRAUD, result.scamType)
    }

    @Test
    fun `parses JSON with stray trailing fence`() {
        val result = ScamOutputParser.parse("$validJson```")
        assertTrue(result.isScam)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
    }

    @Test
    fun `ignores unknown extra keys`() {
        val withExtra = validJson.replace("}", ",\"unknown_key\": \"x\"}")
        val result = ScamOutputParser.parse(withExtra)
        assertTrue(result.isScam)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
    }

    @Test
    fun `missing keys fall back to schema defaults`() {
        val partial = """{"is_scam": true, "risk_level": "HIGH"}"""
        val result = ScamOutputParser.parse(partial)

        assertTrue(result.isScam)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(ScamType.NONE, result.scamType)
        assertEquals(MessageSource.STREAM_ASR, result.sourceType)
    }

    @Test
    fun `invalid boolean coerces and falls back to safe defaults`() {
        val bad = """{"is_scam": "notabool", "risk_level": "MEDIUM"}"""
        val result = ScamOutputParser.parse(bad)

        assertFalse(result.isScam)
        assertEquals(RiskLevel.MEDIUM, result.riskLevel)
        assertEquals(ScamType.NONE, result.scamType)
    }

    @Test
    fun `invalid enum value falls back case-insensitively`() {
        val lowerRisk = """{"is_scam": true, "risk_level": "high"}"""
        val result = ScamOutputParser.parse(lowerRisk)

        assertTrue(result.isScam)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
    }

    @Test
    fun `invalid enum value with unknown word falls back to default`() {
        val bogus = """{"risk_level": "bogus", "scam_type": "bogus"}"""
        val result = ScamOutputParser.parse(bogus)

        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals(ScamType.NONE, result.scamType)
    }

    @Test
    fun `garbage output becomes benign default carrying rationale`() {
        val result = ScamOutputParser.parse("no me hagas caso por favor")

        assertFalse(result.isScam)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals(ScamType.NONE, result.scamType)
        assertEquals("no me hagas caso por favor", result.rationale)
    }

    @Test
    fun `scam type with accents parens and slashes round trips`() {
        val gov = validJson.replace(
            "Fraude de Telecomunicaciones / Falso Soporte",
            "Suplantación Entidad Pública (Aduana / Impuestos)"
        )
        val result = ScamOutputParser.parse(gov)

        assertTrue(result.isScam)
        assertEquals(ScamType.GOVERNMENT_IMPERSONATION, result.scamType)
    }

    @Test
    fun `empty output yields benign default`() {
        val result = ScamOutputParser.parse("")
        assertFalse(result.isScam)
        assertEquals(RiskLevel.LOW, result.riskLevel)
        assertEquals(ScamType.NONE, result.scamType)
    }

    @Test
    fun `CRITICAL risk level parses`() {
        val critical = """{"is_scam": true, "risk_level": "CRITICAL", "scam_type": "Falso Familiar / Extorsión Policial"}"""
        val result = ScamOutputParser.parse(critical)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(ScamType.FAKE_FAMILY_EXTORTION, result.scamType)
    }
}