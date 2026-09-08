package com.checkscam.app

import android.content.Context
import android.util.Log
import com.checkscam.diarization.AudioEnergyAnalyzer
import com.checkscam.diarization.DiarizationInput
import com.checkscam.stt.WhisperEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranscriptionCoordinator(
    context: Context,
    private val chunksProvider: () -> Flow<ByteArray>,
    private val energyAnalyzer: AudioEnergyAnalyzer = AudioEnergyAnalyzer()
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = WhisperEngineImpl(context.applicationContext)
    private val accumulator = PcmWindowAccumulator(WINDOW_SIZE_SHORTS, OVERLAP_SHORTS)

    private val _transcriptions = MutableSharedFlow<DiarizationInput>(replay = 0, extraBufferCapacity = 16)
    val transcriptions: Flow<DiarizationInput> = _transcriptions.asSharedFlow()

    private var initialized = false

    fun start() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { engine.initialize() }
            if (!ok) {
                Log.e(TAG, "STT engine failed to initialize; transcription disabled")
                return@launch
            }
            initialized = true
            // The capture service may spin up after us; poll for a live chunk
            // stream until it exists, then collect.
            var chunkFlow = chunksProvider()
            while (chunkFlow === emptyFlow<ByteArray>()) {
                withContext(Dispatchers.IO) { Thread.sleep(POLL_INTERVAL_MS) }
                chunkFlow = chunksProvider()
            }
            chunkFlow.collect { chunk ->
                accumulate(chunk)
            }
        }
    }

    private fun accumulate(chunk: ByteArray) {
        if (!initialized) return
        accumulator.addChunk(chunk)
        var window = accumulator.takeWindow()
        while (window != null) {
            val text = engine.transcribeChunk(window)
            if (text.isNotBlank()) {
                val rms = energyAnalyzer.rms(window)
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
        initialized = false
        accumulator.reset()
        engine.release()
        scope.cancel()
    }

    companion object {
        private const val TAG = "TranscriptionCoordinator"
        private const val WINDOW_SECONDS = 4
        private const val OVERLAP_SECONDS = 1
        private const val SAMPLE_RATE = 16000
        private const val POLL_INTERVAL_MS = 250L
        private const val WINDOW_SIZE_SHORTS = WINDOW_SECONDS * SAMPLE_RATE
        private const val OVERLAP_SHORTS = OVERLAP_SECONDS * SAMPLE_RATE
    }
}