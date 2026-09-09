package com.checkscam.alerts

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AlertRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ScamHistoryDatabase : RoomDatabase() {

    abstract fun alertHistoryDao(): AlertHistoryDao

    companion object {
        @Volatile
        private var instance: ScamHistoryDatabase? = null

        fun getInstance(context: Context): ScamHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScamHistoryDatabase::class.java,
                    "scam_history.db"
                ).build().also { instance = it }
            }
    }
}