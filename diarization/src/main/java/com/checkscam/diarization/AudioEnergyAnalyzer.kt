package com.checkscam.diarization

enum class SpeakerGuess {
    SPEAKER_A,
    SPEAKER_B,
    SILENCE
}

class AudioEnergyAnalyzer(
    private val silenceThreshold: Float = DEFAULT_SILENCE_THRESHOLD,
    private val speakerAThreshold: Float = DEFAULT_SPEAKER_A_THRESHOLD
) {

    fun rms(shorts: ShortArray): Float {
        if (shorts.isEmpty()) return 0f
        var sumSquares = 0.0
        for (s in shorts) {
            val v = s.toDouble()
            sumSquares += v * v
        }
        return Math.sqrt(sumSquares / shorts.size).toFloat()
    }

    fun rms(bytes: ByteArray): Float {
        if (bytes.size < 2) return 0f
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt() and 0xFF
            shorts[i] = ((hi shl 8) or lo).toShort()
        }
        return rms(shorts)
    }

    fun classify(rmsValue: Float): SpeakerGuess {
        return when {
            rmsValue >= speakerAThreshold -> SpeakerGuess.SPEAKER_A
            rmsValue >= silenceThreshold -> SpeakerGuess.SPEAKER_B
            else -> SpeakerGuess.SILENCE
        }
    }

    companion object {
        const val DEFAULT_SILENCE_THRESHOLD = 500f
        const val DEFAULT_SPEAKER_A_THRESHOLD = 2500f
    }
}