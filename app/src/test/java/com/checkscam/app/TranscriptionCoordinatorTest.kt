package com.checkscam.app

import com.checkscam.stt.WhisperEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the "STT init failed Nx" infinite retry loop:
 * a double start() (INCOMING then IN_PROGRESS) used to spawn an orphaned
 * init coroutine that hit WhisperEngine's non-idempotent initialize and
 * retried forever.
 */
class TranscriptionCoordinatorTest {

    private class FakeWhisperEngine(var alwaysFail: Boolean = false) : WhisperEngine {
        var initCalls = 0

        override fun initialize(modelPath: String) {
            initCalls++
            if (alwaysFail) throw IllegalStateException("WhisperEngine already initialized")
        }

        override fun transcribeChunk(pcmData: ShortArray): String = ""

        override fun reset() {}

        override fun release() {}
    }

    private fun coordinator(engine: FakeWhisperEngine): TranscriptionCoordinator {
        return TranscriptionCoordinator(
            null,
            chunksProvider = { emptyFlow() },
            engine = engine,
            initRetryMs = 50L
        )
    }

    @Test
    fun singleStartInitializesEngineOnce() = runBlocking {
        val engine = FakeWhisperEngine()
        val tc = coordinator(engine)
        tc.start()
        delay(300)
        assertEquals("engine must initialize exactly once", 1, engine.initCalls)
        tc.stop()
    }

    @Test
    fun doubleStartDoesNotSpawnSecondInitLoop() = runBlocking {
        val engine = FakeWhisperEngine()
        val tc = coordinator(engine)
        // Call state can emit INCOMING then IN_PROGRESS; both map to start().
        tc.start()
        tc.start()
        delay(300)
        // Regardless of coroutine scheduling, the engine's idempotent init means
        // the second start reuses the first initialization instead of re-initing
        // (or infinitely retrying "already initialized").
        assertEquals("engine must initialize exactly once despite double start", 1, engine.initCalls)
        tc.stop()
    }

    @Test
    fun stopCancelsFailingRetryLoop() = runBlocking {
        val engine = FakeWhisperEngine(alwaysFail = true)
        val tc = coordinator(engine)
        tc.start()
        delay(300)
        val attemptsBeforeStop = engine.initCalls
        assertTrue("init loop should keep retrying while active", attemptsBeforeStop >= 2)
        tc.stop()
        delay(250)
        assertEquals("stop() must cancel the retry loop", attemptsBeforeStop, engine.initCalls)
    }
}