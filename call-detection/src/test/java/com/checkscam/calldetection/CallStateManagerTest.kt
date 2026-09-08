package com.checkscam.calldetection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallStateManagerTest {

    private lateinit var stateManager: CallStateManager
    private val stateChanges = mutableListOf<Pair<CallState, String?>>()

    @Before
    fun setup() {
        stateChanges.clear()
        stateManager = CallStateManager { state, pkg ->
            stateChanges.add(state to pkg)
        }
    }

    @Test
    fun `initial state is IDLE`() {
        assertEquals(CallState.IDLE, stateManager.state)
        assertNull(stateManager.activePackageName)
    }

    @Test
    fun `valid transition IDLE to INCOMING`() {
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        assertEquals(CallState.INCOMING, stateManager.state)
        assertEquals("com.whatsapp", stateManager.activePackageName)
        assertEquals(1, stateChanges.size)
        assertEquals(CallState.INCOMING, stateChanges[0].first)
    }

    @Test
    fun `valid transition INCOMING to IN_PROGRESS`() {
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.whatsapp")
        assertEquals(CallState.IN_PROGRESS, stateManager.state)
        assertEquals(2, stateChanges.size)
    }

    @Test
    fun `valid transition INCOMING to ENDED (missed call)`() {
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        stateManager.onStateReported(CallState.ENDED, "com.whatsapp")
        assertEquals(CallState.ENDED, stateManager.state)
        assertEquals(2, stateChanges.size)
    }

    @Test
    fun `valid transition ENDED to IDLE`() {
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.whatsapp")
        stateManager.onStateReported(CallState.ENDED, "com.whatsapp")
        stateManager.onStateReported(CallState.IDLE, null)
        assertEquals(CallState.IDLE, stateManager.state)
        assertNull(stateManager.activePackageName)
        assertEquals(4, stateChanges.size)
    }

    @Test
    fun `valid transition INCOMING to IDLE`() {
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        stateManager.onStateReported(CallState.IDLE, null)
        assertEquals(CallState.IDLE, stateManager.state)
        assertEquals(2, stateChanges.size)
    }

    @Test
    fun `valid transition IDLE to IN_PROGRESS (outgoing call)`() {
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.android.phone")
        assertEquals(CallState.IN_PROGRESS, stateManager.state)
        assertEquals("com.android.phone", stateManager.activePackageName)
        assertEquals(1, stateChanges.size)
    }

    @Test
    fun `invalid transition IDLE to ENDED is ignored`() {
        stateManager.onStateReported(CallState.ENDED, "com.whatsapp")
        assertEquals(CallState.IDLE, stateManager.state)
        assertEquals(0, stateChanges.size)
    }

    @Test
    fun `invalid transition IN_PROGRESS to INCOMING is ignored`() {
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.whatsapp")
        stateChanges.clear()

        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        assertEquals(CallState.IN_PROGRESS, stateManager.state)
        assertEquals(0, stateChanges.size)
    }

    @Test
    fun `reset returns to IDLE`() {
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.whatsapp")
        stateChanges.clear()

        stateManager.reset()
        assertEquals(CallState.IDLE, stateManager.state)
        assertNull(stateManager.activePackageName)
        assertEquals(1, stateChanges.size)
        assertEquals(CallState.IDLE, stateChanges[0].first)
    }

    @Test
    fun `full call lifecycle`() {
        stateManager.onStateReported(CallState.INCOMING, "com.android.phone")
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.android.phone")
        stateManager.onStateReported(CallState.ENDED, "com.android.phone")
        stateManager.onStateReported(CallState.IDLE, null)

        assertEquals(CallState.IDLE, stateManager.state)
        assertNull(stateManager.activePackageName)
        assertEquals(4, stateChanges.size)
    }

    @Test
    fun `activePackageName cleared on IDLE`() {
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        stateManager.onStateReported(CallState.IDLE, null)
        assertNull(stateManager.activePackageName)
    }

    @Test
    fun `stateFlow mirrors current state on transition`() {
        assertEquals(CallState.IDLE, stateManager.stateFlow.value)
        stateManager.onStateReported(CallState.INCOMING, "com.whatsapp")
        assertEquals(CallState.INCOMING, stateManager.stateFlow.value)
        assertEquals("com.whatsapp", stateManager.activePackageFlow.value)
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.whatsapp")
        assertEquals(CallState.IN_PROGRESS, stateManager.stateFlow.value)
    }

    @Test
    fun `stateFlow stays IDLE on invalid transition`() {
        stateManager.onStateReported(CallState.ENDED, "com.whatsapp")
        assertEquals(CallState.IDLE, stateManager.stateFlow.value)
    }

    @Test
    fun `stateFlow clears active package on reset`() {
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.whatsapp")
        stateManager.reset()
        assertEquals(CallState.IDLE, stateManager.stateFlow.value)
        assertNull(stateManager.activePackageFlow.value)
    }
}
