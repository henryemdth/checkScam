package com.checkscam.alerts

import com.checkscam.classifier.FraudSynthesized
import com.checkscam.classifier.RiskLevel
import com.checkscam.classifier.ScamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertManagerDedupeTest {

    private fun result(
        risk: RiskLevel = RiskLevel.HIGH,
        type: ScamType = ScamType.TELECOM_FRAUD,
        rationale: String = "urgencia para obtener codigos"
    ) = FraudSynthesized(
        riskLevel = risk,
        scamType = type,
        rationale = rationale
    )

    @Test
    fun `identical alert within window is duplicate`() {
        val key = AlertManager.alertKey(result())
        assertTrue(AlertManager.isDuplicateOfLatest(key, key, elapsedSinceLatestMs = 5_000))
    }

    @Test
    fun `identical alert outside window is not duplicate`() {
        val key = AlertManager.alertKey(result())
        assertFalse(AlertManager.isDuplicateOfLatest(key, key, elapsedSinceLatestMs = 120_000))
    }

    @Test
    fun `different rationale is not duplicate`() {
        assertFalse(
            AlertManager.isDuplicateOfLatest(
                AlertManager.alertKey(result(rationale = "otra tecnica")),
                AlertManager.alertKey(result(rationale = "urgencia para obtener codigos")),
                elapsedSinceLatestMs = 1_000
            )
        )
    }

    @Test
    fun `no previous alert means never duplicate`() {
        val key = AlertManager.alertKey(result())
        assertFalse(AlertManager.isDuplicateOfLatest(key, null, elapsedSinceLatestMs = 0))
    }

    @Test
    fun `different report frequency is never duplicate`() {
        assertFalse(
            AlertManager.isDuplicateOfLatest(
                "A",
                AlertManager.alertKey(result()),
                elapsedSinceLatestMs = 500
            )
        )
    }
}