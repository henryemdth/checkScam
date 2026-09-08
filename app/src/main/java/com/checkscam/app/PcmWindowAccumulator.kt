package com.checkscam.app

/**
 * Pure, Android-free sliding-window accumulator fed by raw little-endian
 * PCM16 bytes. Once the window fills, a ready window is produced and the
 * buffer slides by keeping a tail overlap.
 */
class PcmWindowAccumulator(
    private val windowSizeShorts: Int,
    private val overlapShorts: Int
) {

    private val buffer = ArrayList<Short>(windowSizeShorts + 4096)

    val isFramed: Boolean get() = buffer.size >= windowSizeShorts

    fun size(): Int = buffer.size

    fun addChunk(bytes: ByteArray) {
        buffer.addAll(bytesToShortsLE(bytes).asIterable())
    }

    /**
     * Removes and returns a full window if one is available, sliding the
     * buffer forward by retaining the overlap tail. Returns null otherwise.
     */
    fun takeWindow(): ShortArray? {
        if (!isFramed) return null
        val window = ShortArray(windowSizeShorts)
        for (i in 0 until windowSizeShorts) {
            window[i] = buffer[i]
        }
        val tail = ArrayList<Short>(overlapShorts + 1024)
        for (i in (windowSizeShorts - overlapShorts) until buffer.size) {
            tail.add(buffer[i])
        }
        buffer.clear()
        buffer.addAll(tail)
        return window
    }

    fun reset() {
        buffer.clear()
    }

    companion object {
        fun bytesToShortsLE(bytes: ByteArray): ShortArray {
            val shorts = ShortArray(bytes.size / 2)
            for (i in shorts.indices) {
                val lo = bytes[i * 2].toInt() and 0xFF
                val hi = bytes[i * 2 + 1].toInt() and 0xFF
                shorts[i] = ((hi shl 8) or lo).toShort()
            }
            return shorts
        }
    }
}