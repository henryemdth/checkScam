package com.checkscam.diarization

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEnergyAnalyzerTest {

    private val analyzer = AudioEnergyAnalyzer()

    @Test
    fun rms_zeroForEmptyArray() {
        assertEquals(0f, analyzer.rms(shortArrayOf()), 0f)
    }

    @Test
    fun rms_zeroForSilence() {
        assertEquals(0f, analyzer.rms(shortArrayOf(0, 0, 0, 0)), 0f)
    }

    @Test
    fun rms_constantNonZeroSignal() {
        assertEquals(100f, analyzer.rms(shortArrayOf(100, 100, 100)), 0.001f)
    }

    @Test
    fun rms_mixedSignalPositive() {
        val rms = analyzer.rms(shortArrayOf(4000, -4000, 4000, -4000))
        assertEquals(4000f, rms, 0.01f)
    }

    @Test
    fun rms_bytesLittleEndianDecode() {
        // 100 as LE shorts: 0x64 0x00
        val bytes = byteArrayOf(0x64, 0x00, 0x64, 0x00)
        assertEquals(100f, analyzer.rms(bytes), 0.001f)
    }

    @Test
    fun classify_loudIsSpeakerA() {
        assertEquals(SpeakerGuess.SPEAKER_A, analyzer.classify(3000f))
    }

    @Test
    fun classify_quietAboveSilenceIsSpeakerB() {
        assertEquals(SpeakerGuess.SPEAKER_B, analyzer.classify(1000f))
        assertEquals(SpeakerGuess.SPEAKER_B, analyzer.classify(500f))
    }

    @Test
    fun classify_belowSilenceIsSilence() {
        assertEquals(SpeakerGuess.SILENCE, analyzer.classify(0f))
        assertEquals(SpeakerGuess.SILENCE, analyzer.classify(499f))
    }

    @Test
    fun classify_boundaryExact() {
        assertEquals(SpeakerGuess.SPEAKER_A, analyzer.classify(2500f))
        assertEquals(SpeakerGuess.SPEAKER_B, analyzer.classify(2499f))
    }
}