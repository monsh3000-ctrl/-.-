package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.StudyMode
import com.example.core.model.TransactionType

@Entity(tableName = "study_decks")
data class StudyDeckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val subject: String,
    val description: String,
    val sourceText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_materials",
    foreignKeys = [
        ForeignKey(
            entity = StudyDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId")]
)
data class StudyMaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val rawOcrText: String,
    val normalizedText: String,
    val capturedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = StudyDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId")]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val prompt: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctIndex: Int,
    val explanation: String
) {
    val options: List<String> get() = listOf(optionA, optionB, optionC, optionD)
}

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = StudyDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId")]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val reviewCount: Int = 0,
    val easeFactor: Float = 2.5f,
    val nextReviewAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = StudyDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("deckId")]
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long?,
    val deckTitle: String,
    val mode: String, // QUIZ or FLASHCARDS
    val totalQuestions: Int,
    val correctCount: Int,
    val durationSeconds: Long,
    val earnedSeconds: Long,
    val isCompleted: Boolean,
    val startedAt: Long,
    val completedAt: Long?
)

@Entity(
    tableName = "study_answers",
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class StudyAnswerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val questionId: Long,
    val selectedOptionIndex: Int,
    val isCorrect: Boolean,
    val answeredAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "screen_time_transactions",
    indices = [Index("timestamp"), Index("sourceSessionId")]
)
data class ScreenTimeTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val amountSeconds: Long, // Positive for rewards, negative for usage
    val type: String, // STUDY_REWARD, SCREEN_TIME_USED, MANUAL_ADJUSTMENT, etc.
    val sourceSessionId: Long? = null,
    val description: String
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "school_connections")
data class SchoolConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val platformName: String, // Canvas LMS, Google Classroom, Blackboard, etc.
    val schoolUrl: String,
    val username: String,
    val isKeepSignedIn: Boolean = true,
    val isConnected: Boolean = true,
    val connectedAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long = System.currentTimeMillis(),
    val isAutoSyncEnabled: Boolean = true
)

@Entity(
    tableName = "school_materials",
    indices = [Index("subject"), Index("fileType"), Index("courseId")]
)
data class SchoolMaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long? = null,
    val title: String,
    val originalFileName: String,
    val fileType: String, // PDF, DOCX, PPTX, TXT, IMAGE, WORKSHEET, SLIDES, READING
    val subject: String, // Course name or subject
    val unitName: String, // Section or unit
    val driveFolderPath: String, // e.g. "Study App/School Materials/Physics"
    val driveFileId: String? = null,
    val sourceUrl: String? = null,
    val downloadUrl: String? = null,
    val localFilePath: String? = null,
    val contentSummary: String,
    val fullTextContent: String,
    val processingStatus: String = "COMPLETED", // QUEUED, PROCESSING, COMPLETED, FAILED
    val importedAt: Long = System.currentTimeMillis(),
    val isDeckGenerated: Boolean = false,
    val isSyncIncluded: Boolean = true
)

@Entity(
    tableName = "courses",
    indices = [Index("code"), Index("sourcePlatform")]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseExternalId: String = "",
    val name: String,
    val code: String = "",
    val teacher: String = "",
    val term: String = "",
    val sourcePlatform: String = "School Portal",
    val materialCount: Int = 0,
    val lastSyncAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "drive_files",
    indices = [Index("driveFileId"), Index("courseName")]
)
data class DriveFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val driveFileId: String,
    val fileName: String,
    val mimeType: String,
    val folderPath: String,
    val courseName: String,
    val materialId: Long? = null,
    val localUri: String? = null,
    val fileSize: Long = 0L,
    val isUploaded: Boolean = true,
    val uploadedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "processing_jobs",
    indices = [Index("status"), Index("type")]
)
data class ProcessingJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // OCR, AI_DECK_GEN, SYNC, DRIVE_UPLOAD, PARSE
    val targetTitle: String,
    val status: String, // QUEUED, PROCESSING, COMPLETED, FAILED, REQUIRES_USER_ACTION
    val progress: Float = 0f,
    val message: String = "",
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(
    tableName = "reward_ledger",
    indices = [Index("timestamp"), Index("rewardCategory")]
)
data class RewardLedgerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountSeconds: Long,
    val rewardCategory: String, // STUDY_REWARD, AD_REWARD, USAGE
    val sourceId: String? = null,
    val description: String,
    val verifiedLegitimate: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
