package com.checkscam.calldetection

data class SupportedApp(
    val packageName: String,
    val displayName: String,
    val incomingCallKeywords: List<String>,
    val activeCallKeywords: List<String>
)

object SupportedApps {

    val apps: List<SupportedApp> = listOf(
        SupportedApp(
            packageName = "com.whatsapp",
            displayName = "WhatsApp",
            incomingCallKeywords = listOf("incoming call", "llamada entrante", "chiamata in entrata"),
            activeCallKeywords = listOf("on a call", "in a call", "en llamada", "in通话")
        ),
        SupportedApp(
            packageName = "org.telegram.messenger",
            displayName = "Telegram",
            incomingCallKeywords = listOf("incoming call", "llamada entrante"),
            activeCallKeywords = listOf("on a call", "in a call", "en llamada")
        ),
        SupportedApp(
            packageName = "us.zoom.videomeetings",
            displayName = "Zoom",
            incomingCallKeywords = listOf("incoming call", "llamada entrante", "join meeting"),
            activeCallKeywords = listOf("meeting in progress", "in a meeting", "connected")
        ),
        SupportedApp(
            packageName = "com.google.android.apps.tachyon",
            displayName = "Google Meet",
            incomingCallKeywords = listOf("incoming call", "llamada entrante", "meeting request"),
            activeCallKeywords = listOf("in a meeting", "meeting in progress", "connected")
        )
    )

    private val packageMap: Map<String, SupportedApp> = apps.associateBy { it.packageName }

    fun isSupported(packageName: String?): Boolean = packageName in packageMap

    fun getApp(packageName: String?): SupportedApp? = packageMap[packageName]

    fun getSupportedPackages(): Set<String> = packageMap.keys

    fun matchIncomingCallKeyword(packageName: String, notificationText: String): Boolean {
        val app = packageMap[packageName] ?: return false
        val lowerText = notificationText.lowercase()
        return app.incomingCallKeywords.any { lowerText.contains(it.lowercase()) }
    }

    fun matchActiveCallKeyword(packageName: String, notificationText: String): Boolean {
        val app = packageMap[packageName] ?: return false
        val lowerText = notificationText.lowercase()
        return app.activeCallKeywords.any { lowerText.contains(it.lowercase()) }
    }
}
