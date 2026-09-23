package com.checkscam.app.observability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pipeline stages surfaced by the live diagnostic console (AGENTS.md §5.6).
 */
enum class LogStage(val tag: String) {
    AUDIO("AUDIO"),
    STT("STT"),
    DIARIZATION("DIAR"),
    CLASSIFIER("LLM"),
    ALERTS("ALERT"),
    NOTIFICATIONS("NOTIF"),
    SYSTEM("SYS")
}

/**
 * Single immutable line emitted into the diagnostic terminal.
 */
data class LogEntry(
    val stage: LogStage,
    val message: String,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

/**
 * Non-blocking, thread-safe ring buffer of [LogEntry] events for the live
 * diagnostic console.
 *
 * Writes never suspend ([StateFlow.value] assignment is immediate), so it is
 * safe to call from the audio-capture / STT / inference threads without risking
 * buffer drops or UI jank. Oldest entries are dropped when [capacity] is
 * exceeded.
 */
class AppLogBuffer(
    private val capacity: Int = DEFAULT_CAPACITY
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(stage: LogStage, message: String) {
        val entry = LogEntry(stage = stage, message = message)
        synchronized(this) {
            val current = _entries.value
            val updated = if (current.size >= capacity) {
                current.drop(current.size - capacity + 1) + entry
            } else {
                current + entry
            }
            _entries.value = updated
        }
    }

    fun clear() {
        synchronized(this) { _entries.value = emptyList() }
    }

    companion object {
        const val DEFAULT_CAPACITY = 250
    }
}