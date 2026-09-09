package com.checkscam.app

import android.os.Debug
import android.util.Log

/**
 * Logcat benchmarking for the on-device pipeline (AGENTS.md §7), used by the
 * instrumented end-to-end tests and available for production telemetry-less
 * inspection. Pure logging: no network, no persistence.
 */
object PerformanceLogger {

    private const val TAG = "CheckScamPerf"
    private const val GB_FACTOR = 1024.0 * 1024.0 * 1024.0
    private const val MB_FACTOR = 1024.0 * 1024.0

    private data class Session(
        val tag: String,
        val startMs: Long,
        val lastChunkAtMs: Long = 0L
    )

    private val sessions = mutableMapOf<String, Session>()
    private val samples = mutableMapOf<String, MutableList<Long>>()

    /** Starts measuring a stage identified by [tag]. */
    fun markStart(tag: String) {
        sessions[tag] = Session(tag, System.currentTimeMillis())
        samples.getOrPut(tag) { mutableListOf() }.clear()
        Log.d(TAG, "START $tag")
    }

    /**
     * Flags the "end of spoken sentence" for a streaming stage: all latency
     * measured from this instant counts as TTFT (end of sentence -> first output).
     */
    fun markSentenceEnd(tag: String) {
        val session = sessions[tag]
        if (session == null) {
            Log.w(TAG, "markSentenceEnd before markStart: $tag")
            return
        }
        sessions[tag] = session.copy(lastChunkAtMs = System.currentTimeMillis())
    }

    /** Records a completed result; elapsed-from-sentence-end is the TTFT metric. */
    fun markResult(tag: String) {
        val session = sessions[tag]
        if (session == null) {
            Log.w(TAG, "markResult before markStart: $tag")
            return
        }
        val now = System.currentTimeMillis()
        val total = now - session.startMs
        val ttft = if (session.lastChunkAtMs > 0L) {
            now - session.lastChunkAtMs
        } else {
            total
        }
        samples.getOrPut(tag) { mutableListOf() }.add(ttft)
        Log.d(TAG, "STAGE_COMPLETE $tag totalMs=$total")
    }

    /**
     * Samples the current process heap plus PSS (device-side memory), e.g. right
     * before/after the GGUF model load and during inference.
     */
    fun markPeakMemory(moment: String) {
        val runtime = Runtime.getRuntime()
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()).toDouble() / MB_FACTOR
        val totalHeapMb = runtime.totalMemory().toDouble() / MB_FACTOR
        val pssMb = try {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            memInfo.getMemoryStat("summary.totalPss")?.toDoubleOrNull()?.div(1024.0)
                ?: (memInfo.totalPss / 1024.0)
        } catch (e: Throwable) {
            -1.0
        }
        Log.d(
            TAG,
            "MEMORY moment=$moment heapTotalMb=${"%.1f".format(totalHeapMb)} " +
                "heapUsedMb=${"%.1f".format(usedHeapMb)} pssMb=${if (pssMb >= 0) "%.1f".format(pssMb) else "n/a"}"
        )
    }

    /**
     * Prints a compact leaderboard. [peakPssMb] is the highest PSS seen across
     * the run; exceeding [warnAboveMb] produces a prominent soft warning
     * (budget check: 771 MB GGUF + whisper + app ≤ 1.6 GB, not a hard assert).
     */
    fun logLeaderboard(peakPssMb: Double, warnAboveMb: Double = 1600.0) {
        Log.d(TAG, "----- CheckScam benchmark leaderboard -----")
        for ((tag, ttfTs) in samples) {
            val avg = ttfTs.average()
            val max = ttfTs.maxOrNull() ?: 0L
            Log.d(
                TAG,
                "  $tag: samples=${ttfTs.size} avgTtftMs=${"%.0f".format(avg)} " +
                    "maxTtftMs=$max"
            )
        }
        Log.d(TAG, "  peakPssMb=${"%.1f".format(peakPssMb)} (budget=${warnAboveMb}MB)")
        val over = peakPssMb > warnAboveMb
        if (over) {
            Log.w(
                TAG,
                "WARN: peak memory ${"%.1f".format(peakPssMb)}MB exceeds soft budget " +
                    "${warnAboveMb}MB on this device; 1.6 GB margin tolerated."
            )
        }
        Log.d(TAG, "----- end leaderboard -----")
    }
}