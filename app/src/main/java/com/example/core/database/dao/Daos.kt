package com.example.core.database.dao

import androidx.room.*
import com.example.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDeckDao {
    @Query("SELECT * FROM study_decks ORDER BY updatedAt DESC")
    fun getAllDecks(): Flow<List<StudyDeckEntity>>

    @Query("SELECT * FROM study_decks WHERE id = :id LIMIT 1")
    suspend fun getDeckById(id: Long): StudyDeckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: StudyDeckEntity): Long

    @Update
    suspend fun updateDeck(deck: StudyDeckEntity)

    @Delete
    suspend fun deleteDeck(deck: StudyDeckEntity)

    @Query("SELECT COUNT(*) FROM study_decks")
    suspend fun getDeckCount(): Int
}

@Dao
interface StudyContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("SELECT * FROM questions WHERE deckId = :deckId")
    suspend fun getQuestionsForDeck(deckId: Long): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>)

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId")
    suspend fun getFlashcardsForDeck(deckId: Long): List<FlashcardEntity>

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Query("SELECT COUNT(*) FROM questions WHERE deckId = :deckId")
    suspend fun getQuestionCountForDeck(deckId: Long): Int

    @Query("SELECT COUNT(*) FROM flashcards WHERE deckId = :deckId")
    suspend fun getFlashcardCountForDeck(deckId: Long): Int
}

@Dao
interface StudySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySessionEntity): Long

    @Update
    suspend fun updateSession(session: StudySessionEntity)

    @Query("SELECT * FROM study_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE isCompleted = 0 ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveUnfinishedSession(): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedSessions(): Flow<List<StudySessionEntity>>

    @Insert
    suspend fun insertAnswer(answer: StudyAnswerEntity)

    @Query("SELECT * FROM study_answers WHERE sessionId = :sessionId")
    suspend fun getAnswersForSession(sessionId: Long): List<StudyAnswerEntity>

    @Query("SELECT SUM(durationSeconds) FROM study_sessions WHERE isCompleted = 1 AND completedAt >= :sinceTimestamp")
    suspend fun getStudyDurationSecondsSince(sinceTimestamp: Long): Long?

    @Query("SELECT * FROM study_sessions WHERE isCompleted = 1 AND completedAt >= :sinceTimestamp")
    suspend fun getSessionsSince(sinceTimestamp: Long): List<StudySessionEntity>
}

@Dao
interface ScreenTimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: ScreenTimeTransactionEntity): Long

    @Query("SELECT COALESCE(SUM(amountSeconds), 0) FROM screen_time_transactions")
    fun getBalanceFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountSeconds), 0) FROM screen_time_transactions")
    suspend fun getCurrentBalance(): Long

    @Query("SELECT * FROM screen_time_transactions ORDER BY timestamp DESC LIMIT 100")
    fun getRecentTransactions(): Flow<List<ScreenTimeTransactionEntity>>

    @Query("SELECT COALESCE(SUM(amountSeconds), 0) FROM screen_time_transactions WHERE amountSeconds > 0 AND timestamp >= :sinceTimestamp")
    suspend fun getEarnedSecondsSince(sinceTimestamp: Long): Long

    @Query("SELECT COALESCE(SUM(ABS(amountSeconds)), 0) FROM screen_time_transactions WHERE amountSeconds < 0 AND timestamp >= :sinceTimestamp")
    suspend fun getUsedSecondsSince(sinceTimestamp: Long): Long

    @Query("SELECT COUNT(*) FROM screen_time_transactions WHERE sourceSessionId = :sessionId AND type = 'STUDY_REWARD'")
    suspend fun hasRewardForSession(sessionId: Long): Int

    @Query("DELETE FROM screen_time_transactions WHERE description LIKE 'Reconciled%'")
    suspend fun deleteReconciledTransactions()
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getAllBlockedAppsFlow(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isBlocked = 1")
    suspend fun getActiveBlockedApps(): List<BlockedAppEntity>

    @Query("SELECT isBlocked FROM blocked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun isAppBlocked(packageName: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(apps: List<BlockedAppEntity>)

    @Query("UPDATE blocked_apps SET isBlocked = :isBlocked WHERE packageName = :packageName")
    suspend fun setBlockedStatus(packageName: String, isBlocked: Boolean)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)
}

@Dao
interface SchoolConnectionDao {
    @Query("SELECT * FROM school_connections LIMIT 1")
    fun getActiveConnectionFlow(): Flow<SchoolConnectionEntity?>

    @Query("SELECT * FROM school_connections LIMIT 1")
    suspend fun getActiveConnection(): SchoolConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(connection: SchoolConnectionEntity): Long

    @Query("DELETE FROM school_connections")
    suspend fun clearConnection()

    @Query("UPDATE school_connections SET isAutoSyncEnabled = :enabled")
    suspend fun setAutoSyncEnabled(enabled: Boolean)

    @Query("UPDATE school_connections SET lastSyncAt = :timestamp")
    suspend fun updateLastSync(timestamp: Long)
}

@Dao
interface SchoolMaterialDao {
    @Query("SELECT * FROM school_materials ORDER BY importedAt DESC")
    fun getAllMaterialsFlow(): Flow<List<SchoolMaterialEntity>>

    @Query("SELECT * FROM school_materials ORDER BY importedAt DESC")
    suspend fun getAllMaterialsList(): List<SchoolMaterialEntity>

    @Query("SELECT * FROM school_materials WHERE subject = :subject ORDER BY importedAt DESC")
    fun getMaterialsBySubjectFlow(subject: String): Flow<List<SchoolMaterialEntity>>

    @Query("SELECT DISTINCT subject FROM school_materials ORDER BY subject ASC")
    fun getAllSubjectsFlow(): Flow<List<String>>

    @Query("SELECT * FROM school_materials WHERE id = :id LIMIT 1")
    suspend fun getMaterialById(id: Long): SchoolMaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(materials: List<SchoolMaterialEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: SchoolMaterialEntity): Long

    @Update
    suspend fun updateMaterial(material: SchoolMaterialEntity)

    @Delete
    suspend fun deleteMaterial(material: SchoolMaterialEntity)

    @Query("DELETE FROM school_materials WHERE id = :id")
    suspend fun deleteMaterialById(id: Long)

    @Query("DELETE FROM school_materials")
    suspend fun clearAllMaterials()

    @Query("SELECT COUNT(*) FROM school_materials")
    suspend fun getMaterialCount(): Int

    @Query("UPDATE school_materials SET isDeckGenerated = :isGenerated WHERE id = :id")
    suspend fun setDeckGenerated(id: Long, isGenerated: Boolean)

    @Query("UPDATE school_materials SET isSyncIncluded = :included WHERE id = :id")
    suspend fun setSyncIncluded(id: Long, included: Boolean)

    @Query("SELECT * FROM school_materials WHERE courseId = :courseId ORDER BY importedAt DESC")
    fun getMaterialsForCourseFlow(courseId: Long): Flow<List<SchoolMaterialEntity>>
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY name ASC")
    fun getAllCoursesFlow(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY name ASC")
    suspend fun getAllCourses(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getCourseById(id: Long): CourseEntity?

    @Query("SELECT * FROM courses WHERE courseExternalId = :externalId LIMIT 1")
    suspend fun getCourseByExternalId(externalId: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM courses")
    suspend fun clearCourses()

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun getCourseCount(): Int
}

@Dao
interface DriveFileDao {
    @Query("SELECT * FROM drive_files ORDER BY uploadedAt DESC")
    fun getAllDriveFilesFlow(): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE courseName = :courseName ORDER BY uploadedAt DESC")
    fun getDriveFilesForCourseFlow(courseName: String): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getByDriveFileId(driveFileId: String): DriveFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriveFile(driveFile: DriveFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriveFiles(driveFiles: List<DriveFileEntity>)

    @Query("DELETE FROM drive_files WHERE id = :id")
    suspend fun deleteDriveFile(id: Long)

    @Query("DELETE FROM drive_files")
    suspend fun clearAll()
}

@Dao
interface ProcessingJobDao {
    @Query("SELECT * FROM processing_jobs ORDER BY createdAt DESC")
    fun getAllJobsFlow(): Flow<List<ProcessingJobEntity>>

    @Query("SELECT * FROM processing_jobs WHERE status IN ('QUEUED', 'PROCESSING') ORDER BY createdAt DESC")
    fun getActiveJobsFlow(): Flow<List<ProcessingJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ProcessingJobEntity): Long

    @Query("UPDATE processing_jobs SET status = :status, progress = :progress, message = :message, error = :error, completedAt = :completedAt WHERE id = :id")
    suspend fun updateJobStatus(id: Long, status: String, progress: Float, message: String, error: String? = null, completedAt: Long? = null)

    @Query("DELETE FROM processing_jobs WHERE id = :id")
    suspend fun deleteJob(id: Long)
}

@Dao
interface RewardLedgerDao {
    @Query("SELECT * FROM reward_ledger ORDER BY timestamp DESC")
    fun getAllLedgerEntriesFlow(): Flow<List<RewardLedgerEntity>>

    @Query("SELECT COALESCE(SUM(amountSeconds), 0) FROM reward_ledger WHERE rewardCategory = 'STUDY_REWARD'")
    fun getTotalStudyRewardSecondsFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountSeconds), 0) FROM reward_ledger WHERE rewardCategory = 'AD_REWARD'")
    fun getTotalAdRewardSecondsFlow(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: RewardLedgerEntity): Long
}
