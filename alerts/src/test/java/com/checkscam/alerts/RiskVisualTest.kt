package com.checkscam.alerts

import com.checkscam.classifier.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskVisualTest {

    @Test
    fun `colors are distinct and ordered highest for critical`() {
        val colors = setOf(
            RiskVisual.argb(RiskLevel.LOW),
            RiskVisual.argb(RiskLevel.MEDIUM),
            RiskVisual.argb(RiskLevel.HIGH),
            RiskVisual.argb(RiskLevel.CRITICAL)
        )
        assertEquals(4, colors.size)
        assertTrue(RiskVisual.order(RiskLevel.LOW) < RiskVisual.order(RiskLevel.CRITICAL))
        assertTrue(RiskVisual.order(RiskLevel.HIGH) < RiskVisual.order(RiskLevel.CRITICAL))
    }

    @Test
    fun `argb returns exact palette per risk`() {
        assertEquals(RiskVisual.CRITICAL_COLOR, RiskVisual.argb(RiskLevel.CRITICAL))
        assertEquals(RiskVisual.HIGH_COLOR, RiskVisual.argb(RiskLevel.HIGH))
        assertEquals(RiskVisual.MEDIUM_COLOR, RiskVisual.argb(RiskLevel.MEDIUM))
        assertEquals(RiskVisual.LOW_COLOR, RiskVisual.argb(RiskLevel.LOW))
    }

    @Test
    fun `label mirrors enum name for consistent notifications`() {
        assertEquals("CRITICAL", RiskVisual.label(RiskLevel.CRITICAL))
        assertEquals("LOW", RiskVisual.label(RiskLevel.LOW))
    }
}