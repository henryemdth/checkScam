package com.checkscam.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PcmWindowAccumulatorTest {

    @Test
    fun bytesToShortsLE_decodesLittleEndianCorrectly() {
        // 0x00 0x01 -> 0x0100 = 256
        val bytes = byteArrayOf(0x00, 0x01)
        val shorts = PcmWindowAccumulator.bytesToShortsLE(bytes)
        assertEquals(1, shorts.size)
        assertEquals(256.toShort(), shorts[0])
    }

    @Test
    fun bytesToShortsLE_handlesNegativeHighByte() {
        // 0x00 0xFF -> 0xFF00 = -256
        val bytes = byteArrayOf(0x00, 0xFF.toByte())
        val shorts = PcmWindowAccumulator.bytesToShortsLE(bytes)
        assertEquals((-256).toShort(), shorts[0])
    }

    @Test
    fun bytesToShortsLE_zeroPaddingForOddLength() {
        val bytes = byteArrayOf(0x34, 0x12, 0x56)
        val shorts = PcmWindowAccumulator.bytesToShortsLE(bytes)
        assertEquals(1, shorts.size) // trailing byte ignored
        assertEquals(0x1234.toShort(), shorts[0])
    }

    @Test
    fun takeWindow_returnsNullUntilFull() {
        val acc = PcmWindowAccumulator(windowSizeShorts = 4, overlapShorts = 1)
        assertNull(acc.takeWindow())
        acc.addChunk(shortArrayToBytes(shortArrayOf(1, 2, 3)))
        assertNull(acc.takeWindow())
        acc.addChunk(shortArrayToBytes(shortArrayOf(4)))
        val window = acc.takeWindow()
        assertNotNull(window)
    }

    @Test
    fun takeWindow_slidesWithOverlap() {
        val acc = PcmWindowAccumulator(windowSizeShorts = 4, overlapShorts = 1)
        acc.addChunk(shortArrayToBytes(shortArrayOf(1, 2, 3, 4)))
        val first = acc.takeWindow()
        assertEquals(shortArrayOf(1, 2, 3, 4).toList(), first!!.toList())
        // Buffer should be empty now (no extra samples).
        assertNull(acc.takeWindow())
    }

    @Test
    fun takeWindow_keepsOverlapTailAcrossWindows() {
        val acc = PcmWindowAccumulator(windowSizeShorts = 4, overlapShorts = 2)
        acc.addChunk(shortArrayToBytes(shortArrayOf(1, 2, 3, 4, 5, 6)))
        val first = acc.takeWindow()
        assertEquals(shortArrayOf(1, 2, 3, 4).toList(), first!!.toList())
        // overlap 2 keeps [3,4], plus [5,6] -> total 4, next window immediately ready.
        val second = acc.takeWindow()
        assertEquals(shortArrayOf(3, 4, 5, 6).toList(), second!!.toList())
    }

    @Test
    fun reset_clearsBuffer() {
        val acc = PcmWindowAccumulator(windowSizeShorts = 2, overlapShorts = 0)
        acc.addChunk(shortArrayToBytes(shortArrayOf(1, 2)))
        assertEquals(2, acc.size())
        acc.reset()
        assertEquals(0, acc.size())
        assertNull(acc.takeWindow())
    }

    private fun shortArrayToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }
}