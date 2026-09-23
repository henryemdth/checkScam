package com.checkscam.app

import android.content.Context
import android.util.Log
import com.checkscam.app.observability.AppLogBuffer
import com.checkscam.app.observability.LogStage
import com.checkscam.diarization.AudioEnergyAnalyzer
import com.checkscam.diarization.DiarizationInput
import com.checkscam.stt.WhisperEngine
import com.checkscam.stt.WhisperEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranscriptionCoordinator(
    context: Context?,
    private val chunksProvider: () -> Flow<ByteArray>,
    private val energyAnalyzer: AudioEnergyAnalyzer = AudioEnergyAnalyzer(),
    private val logBuffer: AppLogBuffer? = null,
    private val engine: WhisperEngine = WhisperEngineImpl(
        requireNotNull(context) { "context is required when using the default engine" }.applicationContext
    ),
    private val initRetryMs: Long = RETRY_INIT_MS
) {

    private var scope: CoroutineScope = newScope()
    private val accumulator = PcmWindowAccumulator(WINDOW_SIZE_SHORTS, OVERLAP_SHORTS)

    private val _transcriptions = MutableSharedFlow<DiarizationInput>(replay = 0, extraBufferCapacity = 16)
    val transcriptions: Flow<DiarizationInput> = _transcriptions.asSharedFlow()

    @Volatile
    private var engineReady = false

    fun start() {
        // Rebuild the scope: stop() cancels it, and a cancelled scope must not
        // be reused by the next call session. Also cancel any previous scope so
        // a double start() (e.g. INCOMING followed by IN_PROGRESS) never leaks
        // an orphaned init/collect loop.
        scope.cancel()
        scope = newScope()
        // Engine init (model presence) and chunk ingestion run in parallel so
        // audio flows (and AUDIO logs) are never blocked while the model loads
        // or retries.
        scope.launch { initEngineRetrying() }
        scope.launch { collectChunks() }
    }

    private suspend fun initEngineRetrying() {
        var attempt = 0
        while (true) {
            val ok = withContext(Dispatchers.IO) { engine.initialize() }
            if (ok) {
                engineReady = true
                logBuffer?.log(LogStage.SYSTEM, "STT engine initialized")
                Log.d(TAG, "STT engine initialized")
                return
            }
            // Whisper needs its model file in internal storage. The model is
            // imported from storage separately; surface the wait in the console
            // (throttled) instead of failing silently.
            attempt++
            if (attempt == 1 || attempt % 5 == 0) {
                logBuffer?.log(
                    LogStage.SYSTEM,
                    "STT init failed ${attempt}x (model missing or invalid); retrying"
                )
            }
            Log.w(TAG, "STT engine init failed (model missing or invalid); retrying")
            withContext(Dispatchers.IO) { Thread.sleep(initRetryMs) }
        }
    }

    private suspend fun collectChunks() {
        var loggedWaiting = false
        // The capture service may spin up after us; poll for a live chunk
        // stream until it exists, then collect.
        while (scope.isActive) {
            val chunkFlow = chunksProvider()
            if (chunkFlow === emptyFlow<ByteArray>()) {
                if (!loggedWaiting) {
                    loggedWaiting = true
                    logBuffer?.log(LogStage.SYSTEM, "Capture not yet running; polling for audio")
                }
                withContext(Dispatchers.IO) { Thread.sleep(POLL_INTERVAL_MS) }
                continue
            }
            chunkFlow.collect { chunk ->
                accumulate(chunk)
            }
        }
    }

    private fun accumulate(chunk: ByteArray) {
        accumulator.addChunk(chunk)
        var window = accumulator.takeWindow()
        while (window != null) {
            val rms = energyAnalyzer.rms(window)
            logBuffer?.log(LogStage.AUDIO, "rms=${"%.1f".format(rms)}")
            val text = if (engineReady) engine.transcribeChunk(window) else ""
            if (text.isNotBlank()) {
                logBuffer?.log(LogStage.STT, text.trim())
                val input = DiarizationInput(
                    text = text,
                    rms = rms,
                    startMs = System.currentTimeMillis(),
                    endMs = System.currentTimeMillis()
                )
                scope.launch { _transcriptions.emit(input) }
            }
            window = accumulator.takeWindow()
        }
    }

    fun stop() {
        engineReady = false
        accumulator.reset()
        engine.release()
        scope.cancel()
    }

    private fun newScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "TranscriptionCoordinator"
        // Increased from 4s to 7s: Whisper's accuracy drops significantly on short 4s
        // audio clips because it loses acoustic context. 7 seconds provides enough
        // context for complete sentences, vastly improving Spanish transcription accuracy
        // while maintaining acceptable real-time latency.
        private const val WINDOW_SECONDS = 7
        private const val OVERLAP_SECONDS = 1
        private const val SAMPLE_RATE = 16000
        private const val POLL_INTERVAL_MS = 250L
        internal const val RETRY_INIT_MS = 1000L
        private const val WINDOW_SIZE_SHORTS = WINDOW_SECONDS * SAMPLE_RATE
        private const val OVERLAP_SHORTS = OVERLAP_SECONDS * SAMPLE_RATE
    }
}