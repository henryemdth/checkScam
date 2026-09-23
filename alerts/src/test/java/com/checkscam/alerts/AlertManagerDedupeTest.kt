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

    @Test
    fun `decisionMessage flags each non-dangerous outcome`() {
        val risk = RiskLevel.LOW
        val msg = AlertManager.decisionMessage(
            dangerous = false,
            notificationsEnabled = true,
            duplicate = false,
            result = result(risk = risk),
            key = AlertManager.alertKey(result(risk = risk))
        )
        assertTrue(msg.startsWith("SKIP not-dangerous"))
        assertTrue(msg.contains("risk=$risk"))
    }

    @Test
    fun `decisionMessage flags disabled notifications`() {
        val msg = AlertManager.decisionMessage(
            dangerous = true,
            notificationsEnabled = false,
            duplicate = false,
            result = result(),
            key = "k"
        )
        assertTrue(msg.startsWith("SKIP notifications-disabled"))
    }

    @Test
    fun `decisionMessage flags dedupe suppression`() {
        val msg = AlertManager.decisionMessage(
            dangerous = true,
            notificationsEnabled = true,
            duplicate = true,
            result = result(),
            key = "k"
        )
        assertTrue(msg.startsWith("DEDUPED"))
        assertTrue(msg.contains("key=k"))
    }

    @Test
    fun `decisionMessage flags dispatch`() {
        val msg = AlertManager.decisionMessage(
            dangerous = true,
            notificationsEnabled = true,
            duplicate = false,
            result = result(),
            key = "k"
        )
        assertTrue(msg.startsWith("DISPATCHED"))
        assertTrue(msg.contains("type=${ScamType.TELECOM_FRAUD}"))
    }
}