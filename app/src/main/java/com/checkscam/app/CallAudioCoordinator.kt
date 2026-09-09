package com.checkscam.app

import android.content.Context
import android.util.Log
import com.checkscam.alerts.AlertHistoryStore
import com.checkscam.alerts.AlertManager
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

/**
 * Live phase of the on-device analysis pipeline, visible in the Compose UI.
 */
enum class PipelineStatus { IDLE, LISTENING, ANALYZING }

class CallAudioCoordinator(
    context: Context,
    private val callDetectionService: CallDetectionService,
    alertManager: AlertManager = AlertManager(context),
    historyStore: AlertHistoryStore = AlertHistoryStore(context),
    scamClassifier: ScamClassifier? = null,
    chunksProvider: (() -> Flow<ByteArray>)? = null,
    onScamResult: ((FraudSynthesized) -> Unit)? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appContext = context.applicationContext

    private val alertManager = alertManager
    private val historyStore = historyStore
    private val onScamResult = onScamResult
    private val scamClassifier: ScamClassifier = scamClassifier
        ?: LlamaScamClassifier(engine = LlamaEngineImpl(appContext))

    private val diarizationPipeline = DiarizationPipeline(AudioEnergyAnalyzer())
    private val transcriptionCoordinator = TranscriptionCoordinator(
        appContext,
        chunksProvider = chunksProvider ?: { AudioCaptureService.audioChunks() ?: emptyFlow() }
    )

    val diarizedOutput: Flow<String> = diarizationPipeline.output

    private val _status = MutableStateFlow(PipelineStatus.IDLE)
    val status: kotlinx.coroutines.flow.StateFlow<PipelineStatus> = _status.asStateFlow()

    private var started = false
    private var collectingTranscripts = false
    private var collectingAnalysis = false
    private var classifierInitStarted = false
    private var classifierReady = false
    private var lastAnalysisAtMs = 0L
    private var lastAnalyzedSnapshot = ""

    fun start() {
        if (started) return
        started = true
        Log.d(TAG, "CallAudioCoordinator started")

        initClassifierAsync()

        // Drive audio capture from call state.
        scope.launch {
            callDetectionService.stateFlow.collect { state ->
                when (state) {
                    CallState.INCOMING, CallState.IN_PROGRESS -> startCapturing()
                    CallState.ENDED, CallState.IDLE -> stopCapturing()
                }
            }
        }
    }

    private fun initClassifierAsync() {
        if (classifierInitStarted) return
        classifierInitStarted = true
        scope.launch(Dispatchers.Default) {
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
                Log.e(TAG, "Classifier initialization failed; analysis disabled", e)
                false
            }
            classifierReady = ready
            if (ready) Log.d(TAG, "Classifier ready; analysis enabled")
        }
    }

    private fun startCapturing() {
        AudioCaptureService.start(appContext)
        transcriptionCoordinator.start()
        _status.value = PipelineStatus.LISTENING
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
        val result = withContext(Dispatchers.Default) {
            scamClassifier.classify(snapshot)
        }
        _status.value = PipelineStatus.LISTENING
        onScamResult?.invoke(result)

        val dangerous = result.isScam || result.riskLevel.ordinal >= 2
        if (dangerous) {
            Log.i(TAG, "Scam alert: ${result.riskLevel} / ${result.scamType}")
            alertManager.showAlert(result)
            historyStore.record(result)
        } else {
            Log.d(TAG, "Analysis OK (${result.riskLevel}); no alert")
        }
    }

    private fun stopCapturing() {
        collectingTranscripts = false
        collectingAnalysis = false
        transcriptionCoordinator.stop()
        AudioCaptureService.stop(appContext)
        diarizationPipeline.reset()
        _status.value = PipelineStatus.IDLE
        Log.d(TAG, "Audio capturing stopped")
    }

    fun stop() {
        if (!started) return
        started = false
        scope.cancel()
        transcriptionCoordinator.stop()
        alertManager.cancelActive()
        scamClassifier.release()
        classifierInitStarted = false
        classifierReady = false
        _status.value = PipelineStatus.IDLE
        Log.d(TAG, "CallAudioCoordinator stopped")
    }

    companion object {
        private const val TAG = "CallAudioCoordinator"
        private const val MODEL_ASSET_NAME = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        private const val GRAMMAR_ASSET_NAME = "scam_schema.gbnf"
        private const val MIN_NEW_CHARS = 60
        private const val MIN_ANALYSIS_INTERVAL_MS = 15_000L
    }
}