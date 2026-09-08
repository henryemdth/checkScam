package com.checkscam.diarization

import org.junit.Assert.assertEquals
import org.junit.Test

class DiarizationPipelineTest {

    private val analyzer = AudioEnergyAnalyzer()
    private val pipeline = DiarizationPipeline(analyzer)

    private fun input(text: String, rms: Float) = DiarizationInput(text, rms, startMs = 0, endMs = 0)

    @Test
    fun firstTurn_attributesSpeakerBAndWrapsWithHeader() {
        pipeline.process(input("hola buenos dias", 1500f))
        assertEquals(
            "<SOURCE: STREAM_ASR>\n<SPEAKER_B> hola buenos dias",
            pipeline.snapshot()
        )
    }

    @Test
    fun speakerChange_injectsSpeakerA() {
        pipeline.process(input("hola buenos dias", 1500f))
        pipeline.process(input("que necesita", 4000f))
        assertEquals(
            "<SOURCE: STREAM_ASR>\n<SPEAKER_B> hola buenos dias\n<SPEAKER_A> que necesita",
            pipeline.snapshot()
        )
    }

    @Test
    fun sameSpeaker_appendsWithoutToken() {
        pipeline.process(input("hola", 1500f))
        pipeline.process(input("buenos dias", 1600f))
        assertEquals(
            "<SOURCE: STREAM_ASR>\n<SPEAKER_B> hola buenos dias",
            pipeline.snapshot()
        )
    }

    @Test
    fun silence_keepsLastSpeakerAttribution() {
        pipeline.process(input("hola", 1500f))
        pipeline.process(input("...", 100f))
        assertEquals(
            "<SOURCE: STREAM_ASR>\n<SPEAKER_B> hola ...",
            pipeline.snapshot()
        )
    }

    @Test
    fun emptyText_isIgnored() {
        pipeline.process(input("", 1500f))
        assertEquals("<SOURCE: STREAM_ASR>\n", pipeline.snapshot())
    }

    @Test
    fun reset_clearsAccumulation() {
        pipeline.process(input("hola", 1500f))
        pipeline.reset()
        assertEquals("<SOURCE: STREAM_ASR>\n", pipeline.snapshot())
    }

    @Test
    fun alternateSpeakers_producesCleanTurnMarkers() {
        pipeline.process(input("buenos dias soy de tigo", 1400f))
        pipeline.process(input("de que numero", 4200f))
        pipeline.process(input("es una actualizacion urgente", 1300f))
        pipeline.process(input("colgare la llamada", 4500f))
        assertEquals(
            "<SOURCE: STREAM_ASR>\n" +
                "<SPEAKER_B> buenos dias soy de tigo\n" +
                "<SPEAKER_A> de que numero\n" +
                "<SPEAKER_B> es una actualizacion urgente\n" +
                "<SPEAKER_A> colgare la llamada",
            pipeline.snapshot()
        )
    }
}