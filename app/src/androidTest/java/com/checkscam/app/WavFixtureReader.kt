package com.checkscam.app

import java.io.InputStream

/** Loads a fixture from the instrumentation test assets (test APK, not target app). */
fun readAudioFixture(assetName: String): WavFixtureReader {
    val assets = androidx.test.platform.app.InstrumentationRegistry
        .getInstrumentation()
        .context
        .assets
    return WavFixtureReader.read(assets.open("audio_fixtures/$assetName"))
}

/**
 * Minimal RIFF/WAV reader for the instrumented fixtures. Expects raw PCM,
 * 16 000 Hz, mono, 16-bit little-endian (the exact format AudioRecord produces
 * so fixtures can be fed as if they came from the microphone).
 */
class WavFixtureReader private constructor(
    val pcmBytes: ByteArray
) {

    val durationMs: Long get() = pcmBytes.size.toLong() * 1000 / BYTES_PER_SECOND

    /** Slices the PCM payload into fixed-size chunks mirroring the live mic buffer. */
    fun toChunks(bytesPerChunk: Int = CHUNK_BYTES): List<ByteArray> {
        val chunks = ArrayList<ByteArray>(pcmBytes.size / bytesPerChunk + 1)
        var offset = 0
        while (offset < pcmBytes.size) {
            val len = minOf(bytesPerChunk, pcmBytes.size - offset)
            chunks.add(pcmBytes.copyOfRange(offset, offset + len))
            offset += len
        }
        return chunks
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_SECOND = SAMPLE_RATE * 2 // 16-bit mono
        const val CHUNK_BYTES = BYTES_PER_SECOND / 2 // 0.5 s

        fun read(input: InputStream): WavFixtureReader {
            input.use { raw ->
                val data = raw.readBytes()
                require(data.size >= 44) { "Not a valid WAV header (too short)" }

                require(riff(data) == "RIFF" && riff(data, 8) == "WAVE") {
                    "Not a RIFF/WAVE file"
                }
                require(pcmFormat(data)) { "Audio format must be PCM (1)" }
                require((data[22].toInt() and 0xFF) or ((data[23].toInt() and 0xFF) shl 8) == 1) {
                    "Mono expected"
                }
                require(sampleRate(data) == SAMPLE_RATE) { "16 kHz expected, got ${sampleRate(data)} Hz" }
                require(bitsPerSample(data) == 16) { "16-bit PCM expected, got ${bitsPerSample(data)} bits" }

                val pcm = dataChunkPayload(data)
                require(pcm.isNotEmpty()) { "WAV has no data chunk" }
                return if (pcm.size % 2 == 1) {
                    WavFixtureReader(pcm.copyOfRange(0, pcm.size - 1))
                } else {
                    WavFixtureReader(pcm)
                }
            }
        }

        private fun riff(data: ByteArray, offset: Int = 0): String =
            String(data, offset, 4, Charsets.US_ASCII)

        private fun pcmFormat(data: ByteArray): Boolean =
            (data[20].toInt() and 0xFF) == 1 && (data[21].toInt() and 0xFF) == 0

        private fun le16(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun le32(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)

        private fun sampleRate(data: ByteArray): Int = le32(data, 24)
        private fun bitsPerSample(data: ByteArray): Int = le16(data, 34)

        /** Walks RIFF chunks to the `data` chunk (tolerates extra chunks). */
        private fun dataChunkPayload(data: ByteArray): ByteArray {
            var offset = 12
            while (offset + 8 <= data.size) {
                val id = riff(data, offset)
                val chunkSize = le32(data, offset + 4)
                if (id == "data") {
                    val available = data.size - offset - 8
                    val payload = minOf(chunkSize, available)
                    return data.copyOfRange(offset + 8, offset + 8 + payload)
                }
                offset += 8 + chunkSize + (chunkSize and 1) // pad-byte alignment
            }
            return ByteArray(0)
        }
    }
}