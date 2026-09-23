package com.checkscam.diarization

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Synchronizes STT transcripts with their corresponding audio energy (RMS).
 *
 * A transcript is attributed to SPEAKER_A (local user) if its window RMS is
 * loud, or to SPEAKER_B (remote caller) if quieter but above silence.
 * Structural tokens are injected only when the active speaker changes.
 * Output is accumulated and prefixed with the <SOURCE: STREAM_ASR> header,
 * matching the ChatML prompt format consumed by the scam classifier.
 */
class DiarizationPipeline(
    private val analyzer: AudioEnergyAnalyzer = AudioEnergyAnalyzer(),
    private val onTurn: ((String) -> Unit)? = null
) {

    private val accumulated = StringBuilder()
    private var lastSpeaker: SpeakerGuess? = null

    private val _output = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)
    val output: Flow<String> = _output.asSharedFlow()

    fun process(input: DiarizationInput) {
        if (input.text.isEmpty()) return
        val speaker = analyzer.classify(input.rms)
        val attribution = tokenFor(speaker).ifEmpty { lastTokenOrEmpty() }

        when {
            // Silence: keep attribution to last speaker, append without token.
            speaker == SpeakerGuess.SILENCE -> {
                appendToCurrentTurn(input.text)
            }
            lastSpeaker != speaker -> {
                // Turn boundary: close previous line, open a new one with token.
                if (accumulated.isNotEmpty()) accumulated.append('\n')
                accumulated.append(tokenFor(speaker)).append(' ').append(input.text)
                lastSpeaker = speaker
            }
            else -> {
                appendToCurrentTurn(input.text)
            }
        }
        onTurn?.invoke("$attribution ${input.text.trim()} | rms=${"%.1f".format(input.rms)}")
        emit()
    }

    fun snapshot(): String {
        if (accumulated.isEmpty()) return "$SOURCE_HEADER\n"
        return "$SOURCE_HEADER\n$accumulated"
    }

    fun reset() {
        accumulated.clear()
        lastSpeaker = null
    }

    private fun emit() {
        _output.tryEmit(snapshot())
    }

    private fun appendToCurrentTurn(text: String) {
        if (accumulated.isEmpty()) {
            accumulated.append(text)
        } else {
            accumulated.append(' ').append(text)
        }
    }

    private fun tokenFor(speaker: SpeakerGuess): String = when (speaker) {
        SpeakerGuess.SPEAKER_A -> "<SPEAKER_A>"
        SpeakerGuess.SPEAKER_B -> "<SPEAKER_B>"
        SpeakerGuess.SILENCE -> ""
    }

    private fun lastTokenOrEmpty(): String = when (lastSpeaker) {
        SpeakerGuess.SPEAKER_A -> "<SPEAKER_A>"
        SpeakerGuess.SPEAKER_B -> "<SPEAKER_B>"
        else -> ""
    }

    companion object {
        const val SOURCE_HEADER = "<SOURCE: STREAM_ASR>"
    }
}