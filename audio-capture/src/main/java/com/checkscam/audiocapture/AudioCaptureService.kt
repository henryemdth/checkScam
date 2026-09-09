package com.checkscam.audiocapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AudioCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var recorderManager: AudioRecorderManager

    private val _captureState = MutableStateFlow(CaptureState.STOPPED)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val audioManager: AudioManager
        get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun onCreate() {
        super.onCreate()
        recorderManager = AudioRecorderManager(audioManager)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> startCapture()
            ACTION_STOP_CAPTURE -> stopCapture()
            else -> Log.w(TAG, "Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        if (instance === this) instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startCapture() {
        _captureState.update { CaptureState.STARTING }
        try {
            startForegroundCompat()
        } catch (e: Throwable) {
            // ForegroundServiceStartNotAllowedException (service started from a
            // background context, e.g. instrumented tests) must not kill the
            // pipeline: continue capturing; the system may still schedule the
            // process out, but live analysis keeps working meanwhile.
            Log.w(TAG, "Foreground start not allowed; continuing degraded", e)
        }
        try {
            recorderManager.start(serviceScope)
            _captureState.update { CaptureState.CAPTURING }
        } catch (e: Throwable) {
            Log.e(TAG, "Recorder start failed", e)
            _captureState.update { CaptureState.STOPPED }
        }
        Log.d(TAG, "Capture start handled (state=${_captureState.value})")
    }

    private fun stopCapture() {
        if (::recorderManager.isInitialized) {
            recorderManager.stop()
        }
        _captureState.update { CaptureState.STOPPED }
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "Capture stopped")
    }

    private fun startForegroundCompat() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CheckScam")
            .setContentText("Capturando audio de la llamada en el dispositivo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AudioCaptureService"
        const val ACTION_START_CAPTURE = "com.checkscam.audiocapture.ACTION_START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.checkscam.audiocapture.ACTION_STOP_CAPTURE"
        private const val CHANNEL_ID = "audio_capture_channel"
        private const val CHANNEL_NAME = "Audio Capture"
        private const val CHANNEL_DESCRIPTION = "Persistent notification while capturing call audio"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var instance: AudioCaptureService? = null

        fun start(context: Context) {
            val intent = Intent(context, AudioCaptureService::class.java)
                .setAction(ACTION_START_CAPTURE)
            ContextCompatWrapper.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioCaptureService::class.java)
                .setAction(ACTION_STOP_CAPTURE)
            context.startService(intent)
        }

        fun audioChunks(): Flow<ByteArray>? = instance?.recorderManager?.audioChunks

        fun isCapturing(): Boolean =
            instance?._captureState?.value == CaptureState.CAPTURING
    }
}

enum class CaptureState {
    STARTING,
    CAPTURING,
    STOPPED
}

internal object ContextCompatWrapper {
    fun startForegroundService(context: Context, intent: Intent) {
        context.startForegroundService(intent)
    }
}
