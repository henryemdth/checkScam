package com.checkscam.calldetection

/**
 * Volatile wiring holder decoupling the OS-bound detection services from
 * [CallDetectionService] lifecycle.
 *
 * [NativeCallAccessibilityService] and [ThirdPartyCallDetector] connect
 * asynchronously (system bind). By the time [CallDetectionService.start] runs,
 * `getInstance()` may still be null, so direct wiring is silently dropped.
 * The services re-read this holder when their connect callbacks fire, so the
 * link always lands regardless of bind timing.
 */
object CallStateWire {

    @Volatile
    var stateManager: CallStateManager? = null

    @Volatile
    var notificationTextSink: ((String, String) -> Unit)? = null

    fun clear() {
        stateManager = null
        notificationTextSink = null
    }
}