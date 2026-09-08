package com.checkscam.app.data

import android.content.Context

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isDetectionEnabled(): Boolean = prefs.getBoolean(KEY_DETECTION_ENABLED, false)

    fun setDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DETECTION_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "checkscam_prefs"
        private const val KEY_DETECTION_ENABLED = "detection_enabled"
    }
}