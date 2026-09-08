# CheckScam

Real-time **vishing (phone scam) detection** for Android, tuned for the **Bolivian context** — local banks (Banco Unión, BNB, BCP, Bisa), telecoms (Tigo, Entel, Viva), public entities (Aduana, Impuestos, Policía) and regional slang. Runs entirely **on-device, 100% offline**: no audio, transcript, or metadata ever leaves the phone.

## How it works

During a call (native dialer or third-party apps: WhatsApp, Telegram, Zoom, Google Meet) CheckScam:

1. Detects the active call and forces speakerphone on.
2. Captures conversation audio via the microphone.
3. Transcribes speech locally with `whisper.cpp`.
4. Separates speaker turns (`<SPEAKER_A>` / `<SPEAKER_B>`) with an energy-based heuristic.
5. Classifies the conversation with a small on-device SLM (`llama.cpp` + GBNF-constrained JSON output).
6. Alerts you **during or right after the call** with a risk level and an explainable rationale.

## Key features

- **100 % local processing** — no internet, no cloud APIs, no telemetry.
- **Incremental inference** — evaluates partial transcripts (first 15–30 seconds) for early warnings, not just post-call.
- **Explainable alerts** — severity `LOW / MEDIUM / HIGH / CRITICAL` plus a natural-language `rationale` telling you *why* a call is dangerous (urgency tactics, spoofing, requests for SMS/QR codes, etc.).
- **Spanish / Latin American tuned** — STT optimized for colloquial Bolivian speech.
- **Consent first, always** — capture requires explicit opt-in and a visible way to disable it at any time.

## Privacy & legal (non-negotiable)

- Captured audio is transcribed **in memory and discarded immediately** (saving is opt-in, reserved for dataset generation).
- No audio, text, or metadata is sent to any server — no analytics SDKs, no cloud crash reporting with conversation content.
- A consent notice is shown before capture is ever enabled.
- **The app never auto-terminates a call** — you keep full control.

## Architecture

```text
[Active call detection]
   AccessibilityService + NotificationListenerService + TelephonyManager
        ↓
[Audio capture]
   ForegroundService + AudioRecord (Mic + Speakerphone enabled)
        ↓
[Local STT transcription]
   whisper.cpp (NDK/JNI, streaming/windowed PCM chunks)
        ↓
[Speaker diarization]
   Heuristic energy/pause parser -> <SPEAKER_A> / <SPEAKER_B>
        ↓
[Local model inference]
   llama.cpp (GGUF, GBNF-constrained JSON)
   Input: ChatML prompt with <SOURCE: STREAM_ASR> + speaker turns
   Output: { rationale, is_scam, risk_level, scam_type }
        ↓
[User alert]
   Local notification with severity level and rationale
```

### Modules

| Module | Responsibility |
|---|---|
| `:app` | Compose UI, permissions/consent flow, `CheckScamApplication` orchestration (`CallAudioCoordinator`) |
| `:call-detection` | `CallStateManager` state machine, `TelephonyCallDetector`, `NativeCallAccessibilityService`, `ThirdPartyCallDetector` |
| `:audio-capture` | `AudioCaptureService` (ForegroundService) + `AudioRecorderManager` (16 kHz / 16-bit mono PCM) |
| `:stt-engine` | JNI bindings to vendored whisper.cpp v1.6.0, model in `assets/`, streaming 4 s windows |
| `:diarization` | `AudioEnergyAnalyzer` (RMS) + `DiarizationPipeline` injecting `<SPEAKER_A>`/`<SPEAKER_B>` |
| `:scam-classifier` | JNI bindings to vendored llama.cpp, GGUF + GBNF grammar assets, resilient JSON parser |
| `:alerts` | `AlertManager` mapping `FraudSynthesized` results to severity notifications |
| `training/` | Python: Pydantic schema + GBNF grammar generator (off-device model training later) |
| `dataset/` | ChatML JSONL synthetic/real datasets (training, versioned) |

## Threat taxonomy

Classification contract (single source of truth: [`training/schema.py`](training/schema.py), module `FraudSynthesized`):

| Field | Values |
|---|---|
| `source_type` | `STREAM_ASR`, `WHATSAPP_DIRECT`, `WHATSAPP_GROUP`, `SMS`, `EMAIL` |
| `is_scam` | `true` / `false` |
| `risk_level` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `scam_type` | `None`, `Falso Familiar / Extorsión Policial`, `Phishing / Suplantación Bancaria`, `Suplantación Entidad Pública (Aduana / Impuestos)`, `Fraude de Telecomunicaciones / Falso Soporte` |

JSON output is enforced at inference time with a **GBNF grammar** generated from the Pydantic schema (`training/generate_gbnf.py` → `scam_schema.gbnf`). A tolerant Kotlin fallback parser (`ScamOutputParser`) strips residual markdown fences and never throws on malformed output.

## Tech stack

| Layer | Technology |
|---|---|
| UI / app logic | Kotlin + Jetpack Compose, minSdk 26 / targetSdk 35 |
| Background | ForegroundService, WorkManager-style orchestration |
| Call detection | AccessibilityService + NotificationListenerService + TelephonyManager |
| Audio | `AudioRecord`, `MediaRecorder.AudioSource.MIC` + forced speakerphone |
| STT | `whisper.cpp` v1.6.0 (NDK/JNI), `ggml-base.bin` (147 MB, int8, Spanish) |
| Diarization | Energy/pause heuristic (v1) |
| Classifier | `llama.cpp` (pinned **b6103**), `Llama-3.2-1B-Instruct-Q4_K_M.gguf` (771 MB), GBNF grammar |
| Persistence | Room / DataStore (local only) |

## Repository layout

```text
/app                  → Compose UI, permissions, consent, orchestration
/call-detection        → call state machine + native/third-party detectors
/audio-capture          → AudioRecord + foreground capture service
/stt-engine             → whisper.cpp JNI bindings, streaming transcription
/diarization            → speaker turn separation (<SPEAKER_A> / <SPEAKER_B>)
/scam-classifier        → llama.cpp JNI bindings + GBNF grammar assets
/alerts                 → severity notifications and rationale display
/training               → schema.py (Pydantic), generate_gbnf.py, grammar artifacts
/dataset                → versioned ChatML JSONL datasets
AGENTS.md               → full engineering spec and conventions
MEMORY.md               → phase-by-phase project status
```

## Build & test

Prerequisites:

- JDK 17, Android SDK Platform 35, Android NDK + CMake (via SDK Manager)
- `git-lfs` (models are tracked via LFS — `git lfs pull` after clone; if missing, `export PATH=$HOME/bin:$PATH`)

```bash
# fetch model binaries (whisper .bin, llama .gguf)
git lfs pull

# full build: compile + lint + all unit tests
./gradlew build

# just the debug APK
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# install on a device/emulator
./gradlew :app:installDebug
```

**Tests:** 78 unit tests across `:app`, `:call-detection`, `:diarization`, and `:scam-classifier` (Debug + Release variants), zero network at runtime, no instrumented tests yet.

## Model artifacts (LFS-tracked)

| Asset | Size | Used by |
|---|---|---|
| `stt-engine/src/main/assets/ggml-base.bin` | 147 MB | whisper.cpp STT |
| `scam-classifier/src/main/assets/Llama-3.2-1B-Instruct-Q4_K_M.gguf` | 771 MB | scam classifier |
| `scam-classifier/src/main/assets/scam_schema.gbnf` | ~1 KB | GBNF JSON constraint |

Native libs are built as a single static `.so` per ABI (`libstt-engine.so`, `libscam-classifier.so`; `c++_static`, no bundled `libc++_shared`). Large binaries are excluded from AAPT2 compression (`noCompress += gguf, bin`) to keep builds fast and heap-friendly.

## Roadmap status

Currently at **Phase 5 complete**.

| Phase | Status |
|---|---|
| 1. Call detection | ✅ Done |
| 2. Audio capture | ✅ Done (runtime permission prompts pending) |
| 3. STT (whisper.cpp) | ✅ Done |
| 4. Diarization | ✅ Done |
| 5. Scam classifier (on-device SLM) | ✅ Done |
| 6. Alerts & in-app UI | 🔜 Pending |
| 7. End-to-end pipeline + fixtures + benchmarks | 🔜 Pending |
| 8. Dataset & fine-tuning (post-MVP) | 🔜 Pending |

See [`MEMORY.md`](MEMORY.md) for the full itemized status and [`AGENTS.md`](AGENTS.md) for the engineering spec, constraints, and conventions.

## Licensing

- `whisper.cpp` and `llama.cpp` are vendored under the MIT License (see `LICENSE` in each vendored tree).
- Model weights: `Llama 3.2` (Meta Community License) and whisper `ggml-base` — intended for offline, in-app use.