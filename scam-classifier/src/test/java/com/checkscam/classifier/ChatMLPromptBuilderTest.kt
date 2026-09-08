package com.checkscam.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMLPromptBuilderTest {

    private val diarized =
        "<SOURCE: STREAM_ASR>\n<SPEAKER_B> hola buenos dias le llamamos de la central de tigo\n<SPEAKER_A> de que numero me llaman"

    @Test
    fun `prompt contains chat template control tokens`() {
        val prompt = ChatMLPromptBuilder.build(diarized)

        assertTrue(prompt.startsWith("<|begin_of_text|>"))
        assertTrue(prompt.contains("<|start_header_id|>system<|end_header_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>user<|end_header_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>assistant<|end_header_id|>"))
        assertTrue(prompt.contains("<|eot_id|>"))
    }

    @Test
    fun `prompt embeds the AGENTS system prompt verbatim`() {
        val prompt = ChatMLPromptBuilder.build(diarized)

        assertTrue(prompt.contains(ChatMLPromptBuilder.SYSTEM_PROMPT))
    }

    @Test
    fun `prompt embeds diarized user content verbatim`() {
        val prompt = ChatMLPromptBuilder.build(diarized)

        assertTrue(prompt.contains(diarized))
        assertTrue(prompt.contains("<SOURCE: STREAM_ASR>"))
        assertTrue(prompt.contains("<SPEAKER_B>"))
    }

    @Test
    fun `user content is trimmed`() {
        val prompt = ChatMLPromptBuilder.build("  $diarized  ")
        assertTrue(prompt.contains(diarized))
        assertFalse(prompt.contains("\n\n  <SOURCE"))
    }

    @Test
    fun `whitespace-only input yields valid prompt with empty user content`() {
        val prompt = ChatMLPromptBuilder.build("   ")
        assertTrue(prompt.contains("<|start_header_id|>user<|end_header_id|>\n\n<|eot_id|>"))
    }

    @Test
    fun `user content is truncated to maxChars`() {
        val long = diarized + "x".repeat(1000)
        val prompt = ChatMLPromptBuilder.build(long, maxChars = 100)

        assertFalse(prompt.contains("x".repeat(1000)))
        assertTrue(prompt.length < long.length + 512)
    }

    @Test
    fun `assistant turn is the final block so model only generates JSON`() {
        val prompt = ChatMLPromptBuilder.build(diarized)

        assertTrue(prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun `json schema keys are mentioned in system prompt`() {
        val prompt = ChatMLPromptBuilder.build("hola")

        assertTrue(prompt.contains("rationale"))
        assertTrue(prompt.contains("is_scam"))
        assertTrue(prompt.contains("risk_level"))
        assertTrue(prompt.contains("scam_type"))
    }

    @Test
    fun `default max chars is a positive constant`() {
        assertTrue(ChatMLPromptBuilder.DEFAULT_MAX_CHARS > 0)
    }

    @Test
    fun `alternate system prompt can be injected`() {
        val alt = "Sistema alternativo"
        val prompt = ChatMLPromptBuilder.build("hola", systemPrompt = alt)
        assertTrue(prompt.contains(alt))
        assertFalse(prompt.contains(ChatMLPromptBuilder.SYSTEM_PROMPT))
    }
}