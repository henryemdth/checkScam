package com.checkscam.calldetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateWireTest {

    private val manager = CallStateManager(listener = { _, _ -> Unit })

    @Test
    fun `holds stateManager and notification sink`() {
        assertNull(CallStateWire.stateManager)
        assertNull(CallStateWire.notificationTextSink)

        CallStateWire.stateManager = manager
        val sink: (String, String) -> Unit = { pkg, text -> assertNotNull(pkg) }
        CallStateWire.notificationTextSink = sink

        assertEquals(manager, CallStateWire.stateManager)
        assertNotNull(CallStateWire.notificationTextSink)
    }

    @Test
    fun `clear resets both fields`() {
        CallStateWire.stateManager = manager
        CallStateWire.notificationTextSink = { _, _ -> }

        CallStateWire.clear()

        assertNull(CallStateWire.stateManager)
        assertNull(CallStateWire.notificationTextSink)
    }

    @Test
    fun `sink forwards package and text arguments`() {
        var received: Pair<String, String>? = null
        CallStateWire.notificationTextSink = { pkg, text -> received = pkg to text }
        val sink = CallStateWire.notificationTextSink

        sink?.invoke("com.whatsapp", "Hola, mensaje")
        val (pkg, text) = received!!
        assertTrue(pkg == "com.whatsapp" && text == "Hola, mensaje")
        CallStateWire.clear()
    }
}