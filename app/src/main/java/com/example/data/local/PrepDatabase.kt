package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        InterviewPresetEntity::class,
        InterviewSessionEntity::class,
        SessionTurnEntity::class,
        SavedQuestionEntity::class,
        CompanyBriefEntity::class,
        PrepPlanTaskEntity::class,
        DailyChallengeEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PrepDatabase : RoomDatabase() {
    abstract fun prepDao(): PrepDao

    companion object {
        @Volatile
        private var INSTANCE: PrepDatabase? = null

        fun getInstance(context: Context): PrepDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrepDatabase::class.java,
                    "preppilot_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
