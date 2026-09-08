package com.checkscam.calldetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedAppsTest {

    @Test
    fun `isSupported returns true for known apps`() {
        assertTrue(SupportedApps.isSupported("com.whatsapp"))
        assertTrue(SupportedApps.isSupported("org.telegram.messenger"))
        assertTrue(SupportedApps.isSupported("us.zoom.videomeetings"))
        assertTrue(SupportedApps.isSupported("com.google.android.apps.tachyon"))
    }

    @Test
    fun `isSupported returns false for unknown apps`() {
        assertFalse(SupportedApps.isSupported("com.facebook.katana"))
        assertFalse(SupportedApps.isSupported("com.instagram.android"))
        assertFalse(SupportedApps.isSupported(null))
    }

    @Test
    fun `getApp returns correct app`() {
        val whatsapp = SupportedApps.getApp("com.whatsapp")
        assertEquals("WhatsApp", whatsapp?.displayName)
    }

    @Test
    fun `getApp returns null for unknown`() {
        assertNull(SupportedApps.getApp("com.unknown.app"))
    }

    @Test
    fun `getSupportedPackages contains all known packages`() {
        val packages = SupportedApps.getSupportedPackages()
        assertEquals(4, packages.size)
        assertTrue("com.whatsapp" in packages)
        assertTrue("org.telegram.messenger" in packages)
        assertTrue("us.zoom.videomeetings" in packages)
        assertTrue("com.google.android.apps.tachyon" in packages)
    }

    @Test
    fun `matchIncomingCallKeyword matches WhatsApp incoming`() {
        assertTrue(SupportedApps.matchIncomingCallKeyword("com.whatsapp", "Incoming call"))
        assertTrue(SupportedApps.matchIncomingCallKeyword("com.whatsapp", "Llamada entrante"))
    }

    @Test
    fun `matchIncomingCallKeyword rejects unrelated text`() {
        assertFalse(SupportedApps.matchIncomingCallKeyword("com.whatsapp", "New message from John"))
        assertFalse(SupportedApps.matchIncomingCallKeyword("com.whatsapp", ""))
    }

    @Test
    fun `matchActiveCallKeyword matches Zoom active`() {
        assertTrue(SupportedApps.matchActiveCallKeyword("us.zoom.videomeetings", "Meeting in progress"))
        assertTrue(SupportedApps.matchActiveCallKeyword("us.zoom.videomeetings", "Connected"))
    }

    @Test
    fun `matchActiveCallKeyword rejects unrelated text`() {
        assertFalse(SupportedApps.matchActiveCallKeyword("us.zoom.videomeetings", "New chat message"))
        assertFalse(SupportedApps.matchActiveCallKeyword("us.zoom.videomeetings", "Schedule for later"))
    }

    @Test
    fun `keyword matching is case insensitive`() {
        assertTrue(SupportedApps.matchIncomingCallKeyword("com.whatsapp", "INCOMING CALL"))
        assertTrue(SupportedApps.matchActiveCallKeyword("com.whatsapp", "On A Call"))
    }

    @Test
    fun `unknown package returns false for keyword matching`() {
        assertFalse(SupportedApps.matchIncomingCallKeyword("com.unknown", "Incoming call"))
        assertFalse(SupportedApps.matchActiveCallKeyword("com.unknown", "On a call"))
    }
}
