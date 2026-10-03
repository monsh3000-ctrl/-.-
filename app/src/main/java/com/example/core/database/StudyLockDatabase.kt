package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.database.dao.*
import com.example.core.database.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        StudyDeckEntity::class,
        StudyMaterialEntity::class,
        QuestionEntity::class,
        FlashcardEntity::class,
        StudySessionEntity::class,
        StudyAnswerEntity::class,
        ScreenTimeTransactionEntity::class,
        BlockedAppEntity::class,
        SchoolConnectionEntity::class,
        SchoolMaterialEntity::class,
        CourseEntity::class,
        DriveFileEntity::class,
        ProcessingJobEntity::class,
        RewardLedgerEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class StudyLockDatabase : RoomDatabase() {
    abstract fun studyDeckDao(): StudyDeckDao
    abstract fun studyContentDao(): StudyContentDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun schoolConnectionDao(): SchoolConnectionDao
    abstract fun schoolMaterialDao(): SchoolMaterialDao
    abstract fun courseDao(): CourseDao
    abstract fun driveFileDao(): DriveFileDao
    abstract fun processingJobDao(): ProcessingJobDao
    abstract fun rewardLedgerDao(): RewardLedgerDao

    companion object {
        @Volatile
        private var INSTANCE: StudyLockDatabase? = null

        fun getInstance(context: Context): StudyLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyLockDatabase::class.java,
                    "studylock.db"
                ).fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedDefaultData(getInstance(context))
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedDefaultData(db: StudyLockDatabase) {
            // Seed default distractor apps for screen-time protection list
            val defaultApps = listOf(
                BlockedAppEntity("com.zhiliaoapp.musically", "TikTok", isBlocked = true),
                BlockedAppEntity("com.instagram.android", "Instagram", isBlocked = true),
                BlockedAppEntity("com.google.android.youtube", "YouTube", isBlocked = true),
                BlockedAppEntity("com.twitter.android", "X (Twitter)", isBlocked = true),
                BlockedAppEntity("com.reddit.frontpage", "Reddit", isBlocked = true),
                BlockedAppEntity("com.snapchat.android", "Snapchat", isBlocked = true)
            )
            db.blockedAppDao().insertDefaults(defaultApps)
        }
    }
}
