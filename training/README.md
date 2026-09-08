# Training

Python tooling for the scam pattern classifier. Runs **off-device**; only produces artifacts consumed by the Android app. Nothing here ships to the device.

## Contents

- `schema.py` — **Single Source of Truth** for the classification contract. Defines `FraudSynthesized` (Pydantic) plus `MessageSource`, `RiskLevel`, `ScamType` enums. All of the following derive from it:
  - Kotlin data classes (`:scam-classifier` `FraudSynthesized`)
  - GBNF grammar for `llama.cpp` JSON enforcement
  - ChatML format for dataset generation and fine-tuning
- `generate_gbnf.py` — self-contained converter (`FraudSynthesized.model_json_schema()` → GBNF, `$defs` resolved inline). Emits:
  - `scam_schema.gbnf` — copied to `scam-classifier/src/main/assets/` at build time
  - `scam_schema.json` — JSON Schema witness output

## Usage

```bash
# pydantic is required (system pip may be PEP 668-blocked; use a venv)
python -m venv /tmp/checkscam-venv && /tmp/checkscam-venv/bin/pip install pydantic

# regenerate grammar + schema witness
/tmp/checkscam-venv/bin/python training/generate_gbnf.py

# verify the generated grammar against the Kotlin model (tests in :scam-classifier)
```

## Roadmap (post-MVP)

- Synthetic ChatML JSONL dataset generation for Bolivian banking/telecom vishing scenarios
- LoRA / QLoRA fine-tuning with Unsloth / Hugging Face `SFTTrainer` (base: Llama 3.2 1B)
- Quantization to `GGUF` (Q4_K_M) and swap-in for the MVP model without changing JNI/Kotlin code