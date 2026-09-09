package com.checkscam.alerts

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable row for one classifier result (AGENTS.md §2 "local persistence"). */
@Entity(
    tableName = "scam_history",
    indices = [Index("occurredAtEpochMs")]
)
data class AlertRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val occurredAtEpochMs: Long,
    val riskLevel: String,
    val scamType: String,
    val sourceType: String,
    val rationale: String,
    val simulatedRawText: String,
    val isScam: Boolean
)