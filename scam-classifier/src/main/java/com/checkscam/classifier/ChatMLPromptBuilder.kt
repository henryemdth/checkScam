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
        "Eres un clasificador conservador de fraude conversacional para Bolivia. " +
                "Tu objetivo es minimizar falsos positivos sin perder señales críticas. " +
                "No asumas contexto que no aparece en el texto. " +
                "Solo marca is_scam=true cuando haya evidencia textual suficiente. " +
                "\n\nReglas de decisión:" +
                "\n1) Si el texto es muy corto, ambiguo, o solo menciona un tema (ej: 'robo de cuenta') sin intento de engaño explícito, devuelve is_scam=false, risk_level=LOW, scam_type='None'." +
                "\n2) Marca is_scam=true solo si detectas al menos UNA señal crítica o DOS señales fuertes." +
                "\n3) Señales críticas: solicitud de OTP/código/token/contraseña, pedido de transferencia urgente, enlaces de login sospechosos, instalación de acceso remoto, suplantación con urgencia + pedido de datos." +
                "\n4) Señales fuertes: presión de tiempo, premio/herencia inesperada, solicitud de datos sensibles, cambio de canal para evitar verificación, promesas irreales de inversión." +
                "\n5) Si faltan datos para decidir, explica qué contexto falta en missing_context." +
                "\n\nResponde SOLO con JSON válido y sin texto adicional con este esquema exacto:" +
                "\n{\"rationale\":\"string\",\"is_scam\":boolean,\"risk_level\":\"LOW|MEDIUM|HIGH|CRITICAL\",\"scam_type\":\"AccountTakeover|Phishing|Impersonation|PaymentFraud|InvestmentScam|PrizeScam|TechSupport|LoanScam|RomanceScam|None\",\"confidence\":number,\"indicators\":[\"string\"],\"missing_context\":[\"string\"]}"

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