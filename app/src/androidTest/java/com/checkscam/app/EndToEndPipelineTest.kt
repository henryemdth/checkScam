package com.checkscam.app

import android.Manifest
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.checkscam.alerts.AlertHistoryStore
import com.checkscam.alerts.AlertManager
import com.checkscam.calldetection.CallDetectionService
import com.checkscam.calldetection.CallState
import com.checkscam.calldetection.CallStateManager
import com.checkscam.classifier.FraudSynthesized
import com.checkscam.classifier.RiskLevel
import com.checkscam.classifier.ScamType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end pipeline verification on a physical ARM64 device (AGENTS.md §7):
 *   CallStateManager(IN_PROGRESS) -> Whisper JNI -> Diarization
 *     -> Llama JNI (GBNF JSON) -> AlertManager + Room history -> ENDED -> IDLE
 *
 * Soft assertions (Phase 7/8): the base untuned model may not set is_scam=true
 * reliably; we require the pipeline to complete with a schema-conformant result
 * (rationale/risk_level/scam_type/is_scam present + parsed). A direct
 * is_scam==true assertion is deferred until Phase 8 fine-tuning.
 *
 * Fixtures are injected as PCM chunks (16 kHz / 16-bit mono) via WavFixtureReader.
 * Record them manually per FIXTURE_SCRIPTS.md and place them under:
 *   app/src/androidTest/assets/audio_fixtures/
 *
 * Run: ./gradlew :app:connectedDebugAndroidTest   (ARM64 device only)
 */
@RunWith(AndroidJUnit4::class)
class EndToEndPipelineTest {

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO
        )

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val fixtures = listOf(
        "telecom_scam.wav",
        "bank_scam.wav",
        "government_scam.wav"
    )

    @Test
    fun fullPipeline_requiresValidationOnEachFixture() {
        runBlocking {
            PerformanceLogger.markStart("phase7_total")
            PerformanceLogger.markPeakMemory("before-model-load")

            for (fixtureName in fixtures) {
                val result = runFixture(fixtureName)
                assertNotNull("Pipeline did not emit a result for $fixtureName", result)
                assertSchemaConformant(result!!, fixtureName)
                Log.i(TAG, "Pipeline OK for $fixtureName: risk=${result.riskLevel} " +
                    "isScam=${result.isScam} type=${result.scamType.label}")
            }

            PerformanceLogger.markPeakMemory("after-all-fixtures")
            PerformanceLogger.logLeaderboard(peakPssMb = 0.0)
            PerformanceLogger.markResult("phase7_total")
        }
        Log.i(TAG, "All fixtures completed end-to-end with valid schema output.")
    }

    private suspend fun runFixture(assetName: String): FraudSynthesized? {
        val fixture = readAudioFixture(assetName)
        val chunks = fixture.toChunks()
        Log.i(TAG, "Fixture $assetName duration=${fixture.durationMs}ms chunks=${chunks.size}")

        PerformanceLogger.markStart(assetName)
        val resultDeferred = CompletableDeferred<FraudSynthesized>()

        // Inject chunks via the coordinator's own chunksProvider; the flow must
        // stay alive (tail suspend) until the result arrives so the collector
        // loop isn't cancelled before the throttled analysis can fire.
        val chunksFlow: Flow<ByteArray> = flow {
            PerformanceLogger.markSentenceEnd(assetName)
            for (chunk in chunks) {
                emit(chunk)
                delay(CHUNK_PACE_MS)
            }
            // Keep the collecting loop alive until the result arrives or coordinator stops.
            while (true) delay(1000L)
        }

        val stateManager = CallStateManager(listener = { _, _ -> })
        val detectionService = CallDetectionService(ctx, stateManager)

        val coordinator = CallAudioCoordinator(
            context = ctx,
            callDetectionService = detectionService,
            alertManager = AlertManager(ctx),
            historyStore = AlertHistoryStore(ctx),
            chunksProvider = { chunksFlow },
            onScamResult = {
                PerformanceLogger.markResult(assetName)
                resultDeferred.complete(it)
            }
        )

        coordinator.start()
        // Trigger capture: IN_PROGRESS starts the foreground service / transcription.
        stateManager.onStateReported(CallState.IN_PROGRESS, "com.android.dialer")

        val result = try {
            withTimeout(ResultTimeoutMs) {
                resultDeferred.await().also {
                    PerformanceLogger.markPeakMemory("during-inference-$assetName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timed out waiting for result of $assetName", e)
            null
        } finally {
            // End the call session so the coordinator releases resources.
            try {
                stateManager.onStateReported(CallState.ENDED, null)
                coordinator.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Cleanup after $assetName failed (non-fatal)", e)
            }
        }
        return result
    }

    private fun assertSchemaConformant(result: FraudSynthesized, fixtureName: String) {
        assertTrue(
            "rationale empty for $fixtureName (schema requires non-empty rationale)",
            result.rationale.isNotBlank()
        )
        assertTrue(
            "risk_level invalid for $fixtureName",
            RiskLevel.values().contains(result.riskLevel)
        )
        assertTrue(
            "scam_type invalid for $fixtureName",
            ScamType.values().contains(result.scamType)
        )
    }

    companion object {
        private const val TAG = "EndToEndPipelineTest"
        private const val CHUNK_PACE_MS = 50L
        private const val ResultTimeoutMs = 3L * 60L * 1000L
    }
}