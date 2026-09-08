# Dataset

Versioned **ChatML JSONL** datasets for scam classifier training (off-device) and offline QA. Currently empty; populated post-MVP (Phase 8).

## Format

One JSON object per line, following the ChatML convention consumed by the fine-tuning pipeline (see `AGENTS.md` §7):

```json
{
  "messages": [
    {
      "role": "system",
      "content": "Eres un motor local de detección de fraude en tiempo real para Bolivia. Analiza el texto de entrada y responde estrictamente con un JSON que contenga: rationale (análisis conciso), is_scam (booleano), risk_level (LOW, MEDIUM, HIGH, CRITICAL) y scam_type (categoría o 'None')."
    },
    {
      "role": "user",
      "content": "<SOURCE: STREAM_ASR>\n<SPEAKER_B> hola muy buenos dias le llamamos de la central de tigo ..."
    },
    {
      "role": "assistant",
      "content": "{\"rationale\": \"...\", \"is_scam\": true, \"risk_level\": \"HIGH\", \"scam_type\": \"Fraude de Telecomunicaciones / Falso Soporte\"}"
    }
  ]
}
```

The `assistant` content must conform exactly to `FraudSynthesized` in `training/schema.py` (fields: `rationale`, `is_scam`, `risk_level`, `scam_type`, optional `source_type`, `simulated_raw_text`).

## Data sources

- Public datasets (Kaggle, Hugging Face, LDC) — adapted to ChatML with `<SOURCE: STREAM_ASR>` framing
- Synthetic generation (Bolivian banking/telecom vishing scenarios)
- Own recordings with explicit consent (opt-in save path in the app)

## Conventions

- Audio/transcripts from third parties must never be included without consent
- Omit real victim identities; use Bolivian modismos verbatim (e.g., "megas", "tigo")
- For `STREAM_ASR`, write scammer speech in lowercase without punctuation (mirrors ASR output)