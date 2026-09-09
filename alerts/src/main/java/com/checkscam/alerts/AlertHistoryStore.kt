package com.checkscam.alerts

import android.content.Context
import com.checkscam.classifier.FraudSynthesized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Lightweight facade over the Room [ScamHistoryDatabase] for the alert
 * pipeline and the Compose history screen (AGENTS.md §2, §8 — local only).
 *
 * Only exposes domain types; Room entities/DB never leak to callers.
 */
class AlertHistoryStore(context: Context) {

    private val dao = ScamHistoryDatabase.getInstance(context).alertHistoryDao()

    /** Persisted alerts, newest first. Reactively observed by the UI. */
    val history: Flow<List<AlertRecord>> = dao.observeAll().map { list ->
        list.map { it.toRecord() }
    }

    suspend fun record(result: FraudSynthesized) {
        dao.insert(result.toEntity(System.currentTimeMillis()))
    }

    suspend fun clear() {
        dao.clear()
    }
}