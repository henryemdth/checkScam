package com.checkscam.app

import android.content.Context
import android.util.Log
import com.checkscam.alerts.AlertHistoryStore
import com.checkscam.alerts.AlertManager
import com.checkscam.app.observability.AppLogBuffer
import com.checkscam.app.observability.LogStage
import com.checkscam.audiocapture.AudioCaptureService
import com.checkscam.calldetection.CallDetectionService
import com.checkscam.calldetection.CallState
import com.checkscam.classifier.AssetModelCopier
import com.checkscam.classifier.FraudSynthesized
import com.checkscam.classifier.LlamaEngineImpl
import com.checkscam.classifier.LlamaScamClassifier
import com.checkscam.classifier.ScamClassifier
import com.checkscam.diarization.AudioEnergyAnalyzer
import com.checkscam.diarization.DiarizationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Live phase of the on-device analysis pipeline, visible in the Compose UI.
 */
enum class PipelineStatus { IDLE, LISTENING, ANALYZING }

class CallAudioCoordinator(
    context: Context,
    private val callDetectionService: CallDetectionService,
    alertManager: AlertManager? = null,
    historyStore: AlertHistoryStore = AlertHistoryStore(context),
    classifier: ScamClassifier? = null,
    chunksProvider: (() -> Flow<ByteArray>)? = null,
    onScamResult: ((FraudSynthesized) -> Unit)? = null,
    logBuffer: AppLogBuffer? = null,
    rawOutputSink: ((String) -> Unit)? = null
) {

    private var scope: CoroutineScope = newScope()
    private val appContext = context.applicationContext

    private val alertManager = alertManager
        ?: AlertManager(
            context,
            // Surface dispatch/dedupe decisions in the diagnostic console
            // (AGENTS.md §5.6 "Alert trigger vs. dedupe suppression status").
            onDecision = { decision -> logBuffer?.log(LogStage.ALERTS, decision) }
        )
    private val historyStore = historyStore
    private val onScamResult = onScamResult
    private val logBuffer = logBuffer
    private val injectedClassifier = classifier
    private val defaultClassifier = LlamaScamClassifier(
        engine = LlamaEngineImpl(appContext),
        onRawOutput = rawOutputSink
    )
    private val scamClassifier: ScamClassifier get() = injectedClassifier ?: defaultClassifier

    private val diarizationPipeline = DiarizationPipeline(
        AudioEnergyAnalyzer(),
        onTurn = { logBuffer?.log(LogStage.DIARIZATION, it) }
    )
    private val transcriptionCoordinator = TranscriptionCoordinator(
        appContext,
        chunksProvider = chunksProvider ?: { AudioCaptureService.audioChunks() ?: emptyFlow() },
        logBuffer = logBuffer
    )

    val diarizedOutput: Flow<String> = diarizationPipeline.output

    private val _status = MutableStateFlow(PipelineStatus.IDLE)
    val status: kotlinx.coroutines.flow.StateFlow<PipelineStatus> = _status.asStateFlow()

    private var started = false
    private var capturing = false
    private var collectingTranscripts = false
    private var collectingAnalysis = false
    private var classifierInitStarted = false
    private var classifierReady = false
    private var lastAnalysisAtMs = 0L
    private var lastAnalyzedSnapshot = ""
    @Volatile
    private var finalVerdictRunning = false

    fun start() {
        if (started) return
        started = true
        scope = newScope()
        Log.d(TAG, "CallAudioCoordinator started")

        initClassifierAsync()

        logBuffer?.log(LogStage.SYSTEM, "Coordinator started; awaiting call state")
        // Drive audio capture from call state.
        scope.launch {
            callDetectionService.stateFlow.collect { state ->
                logBuffer?.log(
                    LogStage.SYSTEM,
                    "Call state: ${state.name} (pkg=${callDetectionService.activePackage ?: "unknown"})"
                )
                when (state) {
                    CallState.INCOMING, CallState.IN_PROGRESS -> startCapturing()
                    CallState.ENDED, CallState.IDLE -> stopCapturing()
                }
            }
        }
    }

    /** Re-invokes classifier initialization (e.g. after models were imported). */
    fun onModelsImported() {
        if (!started || classifierReady || classifierInitStarted) return
        Log.d(TAG, "Models imported; (re)initializing classifier")
        initClassifierAsync()
    }

    private fun initClassifierAsync() {
        if (classifierInitStarted) return
        classifierInitStarted = true
        scope.launch(Dispatchers.Default) {
            // The .gguf is intentionally NOT bundled (assets stripped); it only
            // exists after the user imports it via SAF. Pre-check instead of
            // letting the asset copier throw a misleading "Failed to copy asset".
            val modelFile = File(appContext.filesDir, MODEL_ASSET_NAME)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                classifierInitStarted = false
                logBuffer?.log(
                    LogStage.SYSTEM,
                    "Classifier: model not imported yet — importing enables analysis"
                )
                Log.d(TAG, "Classifier model absent; awaiting import")
                return@launch
            }
            var failReason: String? = null
            val ready = try {
                val modelPath = AssetModelCopier.copyToInternalStorage(
                    appContext, MODEL_ASSET_NAME
                )
                val grammarPath = AssetModelCopier.copyToInternalStorage(
                    appContext, GRAMMAR_ASSET_NAME
                )
                scamClassifier.initialize(modelPath, grammarPath)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Classifier initialization failed; will retry on import", e)
                failReason = e.message ?: e.javaClass.simpleName
                false
            }
            classifierReady = ready
            classifierInitStarted = false
            if (ready) {
                logBuffer?.log(LogStage.SYSTEM, "Classifier ready; analysis enabled")
                Log.d(TAG, "Classifier ready; analysis enabled")
            } else {
                logBuffer?.log(
                    LogStage.SYSTEM,
                    "Classifier init FAILED (${failReason ?: "unknown"}); analysis disabled"
                )
            }
        }
    }

    private fun startCapturing() {
        // Call state may emit INCOMING then IN_PROGRESS; guard re-entry so
        // capture (and the transcription coordinator) is never started twice.
        if (capturing) return
        capturing = true
        AudioCaptureService.start(appContext)
        transcriptionCoordinator.start()
        _status.value = PipelineStatus.LISTENING
        logBuffer?.log(LogStage.SYSTEM, "Capture started; MIC active")
        // Feed transcripts into diarization pipeline (single collector).
        if (!collectingTranscripts) {
            collectingTranscripts = true
            scope.launch {
                transcriptionCoordinator.transcriptions.collect { input ->
                    diarizationPipeline.process(input)
                }
            }
        }
        // Incremental classification over the accumulated diarized stream.
        if (!collectingAnalysis) {
            collectingAnalysis = true
            lastAnalysisAtMs = 0L
            lastAnalyzedSnapshot = ""
            scope.launch {
                diarizationPipeline.output.collect { snapshot ->
                    maybeAnalyze(snapshot)
                }
            }
        }
        Log.d(TAG, "Audio capturing started")
    }

    /**
     * Runs the on-device classifier over the growing snapshot, throttled:
     * only when enough new text arrived AND enough time elapsed since the last
     * inference (1B-parameter model must not run every STT window).
     */
    private suspend fun maybeAnalyze(snapshot: String) {
        if (!classifierReady) return
        val now = System.currentTimeMillis()
        val sinceLast = now - lastAnalysisAtMs
        val grewBy = snapshot.length - lastAnalyzedSnapshot.length
        if (sinceLast < MIN_ANALYSIS_INTERVAL_MS) return
        if (grewBy < MIN_NEW_CHARS) return

        lastAnalyzedSnapshot = snapshot
        lastAnalysisAtMs = now
        _status.value = PipelineStatus.ANALYZING
        logBuffer?.log(LogStage.CLASSIFIER, "PROMPT len=${snapshot.length} | ${snapshot.take(300).trim()}")
        val (result, ttftMs) = withContext(Dispatchers.Default) {
            val r = scamClassifier.classify(snapshot)
            val t = (injectedClassifier as? LlamaScamClassifier)?.lastTtftMs() ?: defaultClassifier.lastTtftMs()
            r to t
        }
        
        if (capturing) {
            _status.value = PipelineStatus.LISTENING
        } else if (!finalVerdictRunning) {
            _status.value = PipelineStatus.IDLE
        }
        
        logBuffer?.log(
            LogStage.CLASSIFIER,
            "RESULT risk=${result.riskLevel} isScam=${result.isScam} type=${result.scamType} ttftMs=$ttftMs"
        )
        logBuffer?.log(LogStage.CLASSIFIER, "RATIONALE ${result.rationale}")
        onScamResult?.invoke(result)

        val dangerous = result.isScam || result.riskLevel.ordinal >= 2
        if (dangerous) {
            Log.i(TAG, "Scam alert: ${result.riskLevel} / ${result.scamType}")
            alertManager.showAlert(result)
            historyStore.record(result)
        } else {
            logBuffer?.log(LogStage.ALERTS, "EVALUATED risk=${result.riskLevel} — no alert")
            Log.d(TAG, "Analysis OK (${result.riskLevel}); no alert")
        }
    }

    fun analyzeNotificationText(packageName: String, text: String) {
        if (!classifierReady) return

        // Filter for specific messaging apps to avoid analyzing system or unrelated notifications
        val messagingApps = listOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.android.mms", "com.google.android.apps.messaging", "com.samsung.android.messaging"
        )
        if (packageName !in messagingApps) return

        // Exclude system/call UI notifications which don't contain actual message text
        if (text.contains("Llamada", ignoreCase = true) || text.contains("Call", ignoreCase = true)) return

        scope.launch(Dispatchers.Default) {
            val sourceHeader = when {
                packageName.contains("whatsapp") -> "<SOURCE: WHATSAPP_DIRECT>\n"
                packageName.contains("telegram") -> "<SOURCE: WHATSAPP_DIRECT>\n" // Closest semantic mapping
                else -> "<SOURCE: SMS>\n"
            }
            
            // For a text message, we don't have speaker turns, just the text.
            val snapshot = "$sourceHeader<SPEAKER_B> $text"
            
            logBuffer?.log(LogStage.CLASSIFIER, "PROMPT [NOTIF] len=${snapshot.length} | ${snapshot.take(300).trim()}")
            try {
                val result = scamClassifier.classify(snapshot)
                val ttftMs = (injectedClassifier as? LlamaScamClassifier)?.lastTtftMs() ?: defaultClassifier.lastTtftMs()
                logBuffer?.log(
                    LogStage.CLASSIFIER,
                    "RESULT [NOTIF] risk=${result.riskLevel} isScam=${result.isScam} type=${result.scamType} ttftMs=$ttftMs"
                )
                logBuffer?.log(LogStage.CLASSIFIER, "RATIONALE [NOTIF] ${result.rationale}")
                
                val dangerous = result.isScam || result.riskLevel.ordinal >= 2
                if (dangerous) {
                    Log.i(TAG, "Notification Scam alert: ${result.riskLevel} / ${result.scamType}")
                    alertManager.showAlert(result)
                    historyStore.record(result)
                } else {
                    logBuffer?.log(LogStage.ALERTS, "EVALUATED [NOTIF] risk=${result.riskLevel} — no alert")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Notification classification failed", e)
            }
        }
    }

    private fun stopCapturing() {
        // Ignore spurious ENDED/IDLE re-emissions (and stop() teardown).
        if (!capturing) return
        capturing = false
        // Take the full diarized snapshot BEFORE reset so the end-of-call
        // verdict can classify the complete conversation (late scams included).
        val finalSnapshot = diarizationPipeline.snapshot()
        collectingTranscripts = false
        collectingAnalysis = false
        transcriptionCoordinator.stop()
        AudioCaptureService.stop(appContext)
        diarizationPipeline.reset()
        _status.value = PipelineStatus.IDLE
        logBuffer?.log(LogStage.SYSTEM, "Capture stopped")
        Log.d(TAG, "Audio capturing stopped")
        analyzeFinalVerdict(finalSnapshot)
    }

    /**
     * Final end-of-call classification over the whole conversation. Runs once,
     * unthrottled, so a scam hiding in the last seconds of a call still gets a
     * judgment. Alerts are deduped by [AlertManager] against mid-call alerts.
     */
    private fun analyzeFinalVerdict(snapshot: String) {
        if (finalVerdictRunning) return
        val speech = snapshot
            .removePrefix("${DiarizationPipeline.SOURCE_HEADER}\n")
            .removePrefix(DiarizationPipeline.SOURCE_HEADER)
            .trim()
        if (speech.isEmpty()) {
            logBuffer?.log(LogStage.SYSTEM, "Call finished — no speech captured to evaluate")
            return
        }
        if (!classifierReady) {
            logBuffer?.log(
                LogStage.SYSTEM,
                "Call finished — classifier not ready; skipping final verdict"
            )
            return
        }
        finalVerdictRunning = true
        _status.value = PipelineStatus.ANALYZING
        scope.launch(Dispatchers.Default) {
            try {
                val result = scamClassifier.classify(snapshot)
                logBuffer?.log(
                    LogStage.CLASSIFIER,
                    "FINAL risk=${result.riskLevel} isScam=${result.isScam} type=${result.scamType}"
                )
                logBuffer?.log(LogStage.CLASSIFIER, "RATIONALE ${result.rationale}")
                val dangerous = result.isScam || result.riskLevel.ordinal >= 2
                if (dangerous) {
                    Log.i(TAG, "Final verdict: scam ${result.riskLevel} / ${result.scamType}")
                    alertManager.showAlert(result)
                    historyStore.record(result)
                    logBuffer?.log(LogStage.SYSTEM, "Call finished — FINAL verdict: suspected scam")
                } else {
                    logBuffer?.log(LogStage.ALERTS, "EVALUATED risk=${result.riskLevel} — no alert (end of call)")
                    logBuffer?.log(LogStage.SYSTEM, "Call finished — FINAL verdict: no scam detected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Final verdict classification failed", e)
            } finally {
                finalVerdictRunning = false
                if (!capturing) {
                    _status.value = PipelineStatus.IDLE
                }
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        capturing = false
        collectingTranscripts = false
        collectingAnalysis = false
        scope.cancel()
        scope = newScope()
        transcriptionCoordinator.stop()
        alertManager.cancelActive()
        
        // Release on a background thread to prevent blocking the Main thread 
        // if a native classification is currently running.
        scope.launch(Dispatchers.Default) {
            scamClassifier.release()
        }
        
        classifierInitStarted = false
        classifierReady = false
        _status.value = PipelineStatus.IDLE
        logBuffer?.log(LogStage.SYSTEM, "Coordinator stopped")
        Log.d(TAG, "CallAudioCoordinator stopped")
    }

    private fun newScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

companion object {
        private const val TAG = "CallAudioCoordinator"
        private const val MODEL_ASSET_NAME = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        private const val GRAMMAR_ASSET_NAME = "scam_schema.gbnf"
        private const val MIN_NEW_CHARS = 60
        private const val MIN_ANALYSIS_INTERVAL_MS = 15_000L
    }
}