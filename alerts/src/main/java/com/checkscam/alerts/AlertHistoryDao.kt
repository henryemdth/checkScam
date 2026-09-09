package com.checkscam.alerts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertHistoryDao {

    @Query("SELECT * FROM scam_history ORDER BY occurredAtEpochMs DESC")
    fun observeAll(): Flow<List<AlertRecordEntity>>

    @Query("SELECT * FROM scam_history ORDER BY occurredAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AlertRecordEntity>

    @Insert
    suspend fun insert(entity: AlertRecordEntity)

    @Query("DELETE FROM scam_history")
    suspend fun clear()
}