package com.checkscam.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.checkscam.classifier.FraudSynthesized
import com.checkscam.classifier.RiskLevel

/**
 * Dispatches local Heads-Up notifications for scam alerts (AGENTS.md §5.5, §6).
 *
 * - Channel uses [NotificationManager.IMPORTANCE_HIGH] + bypass-DnD so alerts
 *   surface over the active dialer.
 * - The notification body shows the LLM's [FraudSynthesized.rationale] and
 *   [FraudSynthesized.scamType].
 * - Visual severity cues: risk-colored small icon (`setColorized`) mapping via
 *   [RiskVisual], CRITICAL=red / HIGH=orange / MEDIUM=amber / LOW=gray.
 * - Repeated analyses of the same conversation are de-duplicated so they do not
 *   spam the user during a single call.
 */
class AlertManager(
    context: Context
) {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    private var lastAlertKey: String? = null
    private var lastAlertAtMs: Long = 0L

    init {
        createChannel()
    }

    @SuppressLint("MissingPermission")
    fun showAlert(result: FraudSynthesized) {
        val dangerous = result.isScam || result.riskLevel != RiskLevel.LOW
        if (!dangerous) return
        if (!notificationsEnabled()) return

        val now = System.currentTimeMillis()
        if (isDuplicateOfLatest(alertKey(result), lastAlertKey, now - lastAlertAtMs)) return
        lastAlertKey = alertKey(result)
        lastAlertAtMs = now

        val notification = buildNotification(result)
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked after startup; degrade silently.
        }
    }

    fun cancelActive() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(result: FraudSynthesized): android.app.Notification {
        val risk = result.riskLevel
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        val contentIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                appContext,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        val bigText = buildString {
            append(result.rationale)
            if (result.scamType.label != "None") {
                append("\n\nCategoría: ").append(result.scamType.label)
            }
        }

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scam_alert)
            .setColor(RiskVisual.argb(risk))
            .setColorized(true)
            .setContentTitle("${RiskVisual.label(risk)} - Posible fraude")
            .setContentText(result.rationale)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSubText(result.scamType.label)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return notificationManager.areNotificationsEnabled()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.channel_scam_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.channel_scam_alerts_description)
            enableVibration(true)
            setBypassDnd(true)
            setShowBadge(true)
        }
        appContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AlertManager"
        private const val CHANNEL_ID = "scam_alerts"
        private const val NOTIFICATION_ID = 1
        private const val DEDUPE_WINDOW_MS = 60_000L

        /** Signature identifying an alert so the same scare is not re-notified repeatedly. */
        fun alertKey(result: FraudSynthesized): String =
            "${result.riskLevel}|${result.scamType}|${result.rationale}"

        /**
         * True when a recent alert should be suppressed because the current one is
         * identical and was shown recently (sliding-window re-analysis).
         */
        fun isDuplicateOfLatest(
            key: String,
            latestKey: String?,
            elapsedSinceLatestMs: Long,
            windowMs: Long = DEDUPE_WINDOW_MS
        ): Boolean = latestKey != null && key == latestKey && elapsedSinceLatestMs < windowMs
    }
}