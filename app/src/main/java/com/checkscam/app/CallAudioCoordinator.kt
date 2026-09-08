package com.checkscam.app

import android.content.Context
import android.util.Log
import com.checkscam.audiocapture.AudioCaptureService
import com.checkscam.calldetection.CallDetectionService
import com.checkscam.calldetection.CallState
import com.checkscam.diarization.AudioEnergyAnalyzer
import com.checkscam.diarization.DiarizationPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class CallAudioCoordinator(
    context: Context,
    private val callDetectionService: CallDetectionService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val appContext = context.applicationContext

    private val diarizationPipeline = DiarizationPipeline(AudioEnergyAnalyzer())
    private val transcriptionCoordinator = TranscriptionCoordinator(
        appContext,
        chunksProvider = { AudioCaptureService.audioChunks() ?: emptyFlow() }
    )

    val diarizedOutput: Flow<String> = diarizationPipeline.output

    private var started = false
    private var collectingTranscripts = false

    fun start() {
        if (started) return
        started = true
        Log.d(TAG, "CallAudioCoordinator started")

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

    private fun startCapturing() {
        AudioCaptureService.start(appContext)
        transcriptionCoordinator.start()
        // Feed transcripts into diarization pipeline (single collector).
        if (!collectingTranscripts) {
            collectingTranscripts = true
            scope.launch {
                transcriptionCoordinator.transcriptions.collect { input ->
                    diarizationPipeline.process(input)
                }
            }
        }
        Log.d(TAG, "Audio capturing started")
    }

    private fun stopCapturing() {
        collectingTranscripts = false
        transcriptionCoordinator.stop()
        AudioCaptureService.stop(appContext)
        diarizationPipeline.reset()
        Log.d(TAG, "Audio capturing stopped")
    }

    fun stop() {
        if (!started) return
        started = false
        scope.cancel()
        transcriptionCoordinator.stop()
        Log.d(TAG, "CallAudioCoordinator stopped")
    }

    companion object {
        private const val TAG = "CallAudioCoordinator"
    }
}