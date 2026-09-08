package com.checkscam.classifier

/**
 * Builds the Llama 3.2 Instruct ChatML prompt fed to the on-device LLM.
 *
 * System prompt is verbatim from AGENTS.md §7; the diarized stream
 * (`<SOURCE: STREAM_ASR>` + `<SPEAKER_A>/<SPEAKER_B>` tokens) goes into the
 * user turn so the model evaluates the exact Phase-4 output format.
 */
object ChatMLPromptBuilder {

    val SYSTEM_PROMPT: String =
        "Eres un motor local de detección de fraude en tiempo real para Bolivia. " +
            "Analiza el texto de entrada y responde estrictamente con un JSON que contenga: " +
            "rationale (análisis conciso), is_scam (booleano), risk_level (LOW, MEDIUM, HIGH, CRITICAL) " +
            "y scam_type (categoría o 'None')."

    const val DEFAULT_MAX_CHARS = 6000

    fun build(
        diarizedText: String,
        maxChars: Int = DEFAULT_MAX_CHARS,
        systemPrompt: String = SYSTEM_PROMPT
    ): String {
        val userContent = diarizedText.trim().take(maxChars)
        return buildString {
            append("<|begin_of_text|>")
            append("<|start_header_id|>system<|end_header_id|>\n\n")
            append(systemPrompt)
            append("<|eot_id|>")
            append("<|start_header_id|>user<|end_header_id|>\n\n")
            append(userContent)
            append("<|eot_id|>")
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    }
}