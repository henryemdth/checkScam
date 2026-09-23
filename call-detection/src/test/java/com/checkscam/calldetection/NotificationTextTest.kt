package com.checkscam.calldetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {

    @Test
    fun `formatNotificationText concatenates title text and bigText`() {
        val result = ThirdPartyCallDetector.formatNotificationText(
            title = "Tigo",
            text = "Mensaje",
            bigText = "Detalle largo"
        )
        assertEquals("Tigo Mensaje Detalle largo", result)
    }

    @Test
    fun `formatNotificationText trims leading and trailing whitespace`() {
        val result = ThirdPartyCallDetector.formatNotificationText(
            title = "  Banco Unión  ",
            text = " ",
            bigText = " respaldo "
        )
        assertTrue(result.startsWith("Banco Unión"))
        assertTrue(result.endsWith("respaldo"))
        assertTrue(result == result.trim())
    }

    @Test
    fun `formatNotificationText caps at maxChars`() {
        val long = "x".repeat(500)
        val result = ThirdPartyCallDetector.formatNotificationText(long, "", "")
        assertTrue(result.length <= 300)
        assertEquals(300, result.length)
    }

    @Test
    fun `formatNotificationText handles empty fields`() {
        val result = ThirdPartyCallDetector.formatNotificationText("", "solo texto", "")
        assertEquals("solo texto", result)
    }
}