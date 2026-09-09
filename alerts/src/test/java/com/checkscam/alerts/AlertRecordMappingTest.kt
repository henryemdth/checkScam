package com.checkscam.alerts

import com.checkscam.classifier.FraudSynthesized
import com.checkscam.classifier.MessageSource
import com.checkscam.classifier.RiskLevel
import com.checkscam.classifier.ScamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRecordMappingTest {

    private val sample = FraudSynthesized(
        sourceType = MessageSource.STREAM_ASR,
        simulatedRawText = "hola le llamamos de tigo su numero sera bloqueado",
        rationale = "El estafador usa urgencia para obtener datos personales.",
        isScam = true,
        riskLevel = RiskLevel.CRITICAL,
        scamType = ScamType.TELECOM_FRAUD
    )

    @Test
    fun `record round-trips through entity`() {
        val entity = sample.toEntity(occurredAtEpochMs = 1_700_000_000_000L)
        val record = entity.toRecord()

        assertEquals(1_700_000_000_000L, record.occurredAtEpochMs)
        assertEquals(MessageSource.STREAM_ASR, record.result.sourceType)
        assertEquals("hola le llamamos de tigo su numero sera bloqueado", record.result.simulatedRawText)
        assertEquals("El estafador usa urgencia para obtener datos personales.", record.result.rationale)
        assertTrue(record.result.isScam)
        assertEquals(RiskLevel.CRITICAL, record.result.riskLevel)
        assertEquals(ScamType.TELECOM_FRAUD, record.result.scamType)
    }

    @Test
    fun `unknown enum names fall back to safe defaults`() {
        val entity = AlertRecordEntity(
            id = 1,
            occurredAtEpochMs = 1234L,
            riskLevel = "NOT_A_LEVEL",
            scamType = "NOT_A_TYPE",
            sourceType = "NOT_A_SOURCE",
            rationale = "x",
            simulatedRawText = "",
            isScam = false
        )
        val record = entity.toRecord()

        assertEquals(RiskLevel.LOW, record.result.riskLevel)
        assertEquals(ScamType.NONE, record.result.scamType)
        assertEquals(MessageSource.STREAM_ASR, record.result.sourceType)
        assertFalse(record.result.isScam)
    }
}