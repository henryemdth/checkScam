package com.checkscam.audiocapture

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioRecorderManager(
    private val audioManager: AudioManager
) {

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private val _audioChunks = Channel<ByteArray>(capacity = Channel.BUFFERED)
    val audioChunks = _audioChunks.receiveAsFlow()

    val isSpeakerphoneEnabled: Boolean
        get() = try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot check speakerphone state", e)
            false
        }

    fun start(scope: CoroutineScope) {
        if (audioRecord != null) {
            Log.w(TAG, "Already recording, ignoring start")
            return
        }

        if (!isSpeakerphoneEnabled) {
            Log.w(TAG, "WARNING: Speakerphone is OFF — audio capture quality will be poor")
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ).coerceAtLeast(MIN_BUFFER_SIZE)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e)
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        Log.d(TAG, "AudioRecord started (${SAMPLE_RATE}Hz, buffer=${bufferSize}B)")

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readCount > 0) {
                    val pcmBytes = shortsToBytes(buffer, readCount)
                    _audioChunks.send(pcmBytes)
                } else if (readCount < 0) {
                    Log.e(TAG, "AudioRecord read error: $readCount")
                    break
                }
            }
            Log.d(TAG, "Capture loop ended")
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord stop called in invalid state", e)
        }
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "AudioRecord stopped and released")
    }

    private fun shortsToBytes(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    companion object {
        private const val TAG = "AudioRecorderManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MIN_BUFFER_SIZE = 4096
    }
}
