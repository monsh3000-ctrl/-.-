package com.example.core.repository

import android.content.Context
import com.example.core.database.StudyLockDatabase
import com.example.core.database.entity.*
import com.example.core.datastore.StudyLockPreferences
import com.example.core.engine.AntiTamperManager
import com.example.core.engine.DefaultStudyContentGenerator
import com.example.core.engine.RewardCalculator
import com.example.core.engine.StudyContentGenerator
import com.example.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

class StudyLockRepository(
    private val context: Context,
    val database: StudyLockDatabase = StudyLockDatabase.getInstance(context),
    val preferences: StudyLockPreferences = StudyLockPreferences(context),
    val contentGenerator: StudyContentGenerator = DefaultStudyContentGenerator(),
    val antiTamperManager: AntiTamperManager = AntiTamperManager(preferences)
) {
    private val rewardMutex = Mutex()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                database.screenTimeDao().deleteReconciledTransactions()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun purgeReconciledTransactions() {
        database.screenTimeDao().deleteReconciledTransactions()
    }
    // --- Screen Time Ledger ---
    val balanceSecondsFlow: Flow<Long> = database.screenTimeDao().getBalanceFlow()
        .map { it.coerceAtLeast(0L) }

    val recentTransactionsFlow: Flow<List<ScreenTimeTransactionEntity>> =
        database.screenTimeDao().getRecentTransactions()

    suspend fun getScreenTimeBalance(): ScreenTimeBalance {
        val currentBalance = database.screenTimeDao().getCurrentBalance().coerceAtLeast(0L)
        val startOfDay = getStartOfDayTimestamp()
        val todayEarned = database.screenTimeDao().getEarnedSecondsSince(startOfDay)
        val todayUsed = database.screenTimeDao().getUsedSecondsSince(startOfDay)
        val allTimeEarned = database.screenTimeDao().getEarnedSecondsSince(0L)
        return ScreenTimeBalance(
            currentBalanceSeconds = currentBalance,
            todayEarnedSeconds = todayEarned,
            todayUsedSeconds = todayUsed,
            totalEarnedAllTimeSeconds = allTimeEarned
        )
    }

    suspend fun deductScreenTime(seconds: Long, description: String = "App usage"): Boolean {
        if (seconds <= 0) return true
        val currentBalance = database.screenTimeDao().getCurrentBalance()
        if (currentBalance <= 0) return false

        val deductAmount = seconds.coerceAtMost(currentBalance)
        database.screenTimeDao().insertTransaction(
            ScreenTimeTransactionEntity(
                amountSeconds = -deductAmount,
                type = TransactionType.SCREEN_TIME_USED.name,
                description = description
            )
        )
        return true
    }

    suspend fun addManualAdjustment(seconds: Long, description: String) {
        database.screenTimeDao().insertTransaction(
            ScreenTimeTransactionEntity(
                amountSeconds = seconds,
                type = TransactionType.MANUAL_ADJUSTMENT.name,
                description = description
            )
        )
    }

    suspend fun addScreenTimeReward(seconds: Long, description: String) {
        val now = System.currentTimeMillis()
        database.screenTimeDao().insertTransaction(
            ScreenTimeTransactionEntity(
                timestamp = now,
                amountSeconds = seconds,
                type = TransactionType.STUDY_REWARD.name,
                sourceSessionId = null,
                description = description
            )
        )
        val currentStreak = preferences.currentStreak.first()
        val lastDate = preferences.lastStudyDate.first()
        val isConsecutive = isConsecutiveDay(lastDate, now)
        val isSameDay = isSameDay(lastDate, now)
        val newStreak = when {
            isSameDay -> currentStreak
            isConsecutive -> currentStreak + 1
            else -> 1
        }
        preferences.updateStreak(newStreak, now)
    }

    // --- Study Decks ---
    val allDecksFlow: Flow<List<StudyDeckEntity>> = database.studyDeckDao().getAllDecks()

    suspend fun createDeck(
        title: String,
        subject: String,
        description: String,
        sourceText: String
    ): Long {
        val deckId = database.studyDeckDao().insertDeck(
            StudyDeckEntity(
                title = title,
                subject = subject,
                description = description,
                sourceText = sourceText
            )
        )
        database.studyContentDao().insertQuestions(emptyList())
        return deckId
    }

    suspend fun getDeckById(deckId: Long): StudyDeckEntity? {
        return database.studyDeckDao().getDeckById(deckId)
    }

    suspend fun deleteDeck(deck: StudyDeckEntity) {
        database.studyDeckDao().deleteDeck(deck)
    }

    // --- Study Content ---
    suspend fun getQuestionsForDeck(deckId: Long): List<QuestionEntity> {
        return database.studyContentDao().getQuestionsForDeck(deckId)
    }

    suspend fun getFlashcardsForDeck(deckId: Long): List<FlashcardEntity> {
        return database.studyContentDao().getFlashcardsForDeck(deckId)
    }

    suspend fun generateAndSaveDeckContent(
        deckId: Long,
        sourceText: String,
        questionCount: Int = 5,
        flashcardCount: Int = 5
    ) {
        val generatedQuestions = contentGenerator.generateQuestions(deckId, sourceText, questionCount).getOrNull()
        if (!generatedQuestions.isNullOrEmpty()) {
            val entities = generatedQuestions.map { q ->
                QuestionEntity(
                    deckId = deckId,
                    prompt = q.prompt,
                    optionA = q.optionA,
                    optionB = q.optionB,
                    optionC = q.optionC,
                    optionD = q.optionD,
                    correctIndex = q.correctIndex,
                    explanation = q.explanation
                )
            }
            database.studyContentDao().insertQuestions(entities)
        }

        val generatedCards = contentGenerator.generateFlashcards(deckId, sourceText, flashcardCount).getOrNull()
        if (!generatedCards.isNullOrEmpty()) {
            val cardEntities = generatedCards.map { c ->
                FlashcardEntity(
                    deckId = deckId,
                    front = c.front,
                    back = c.back
                )
            }
            database.studyContentDao().insertFlashcards(cardEntities)
        }
    }

    // --- Sessions & Rewards ---
    suspend fun startStudySession(
        deckId: Long,
        deckTitle: String,
        mode: StudyMode,
        totalQuestions: Int
    ): Long {
        val sessionId = database.studySessionDao().insertSession(
            StudySessionEntity(
                deckId = deckId,
                deckTitle = deckTitle,
                mode = mode.name,
                totalQuestions = totalQuestions,
                correctCount = 0,
                durationSeconds = 0,
                earnedSeconds = 0,
                isCompleted = false,
                startedAt = System.currentTimeMillis(),
                completedAt = null
            )
        )
        preferences.setActiveSessionId(sessionId)
        return sessionId
    }

    suspend fun recordAnswer(
        sessionId: Long,
        questionId: Long,
        selectedIndex: Int,
        isCorrect: Boolean
    ) {
        database.studySessionDao().insertAnswer(
            StudyAnswerEntity(
                sessionId = sessionId,
                questionId = questionId,
                selectedOptionIndex = selectedIndex,
                isCorrect = isCorrect
            )
        )
    }

    suspend fun completeStudySession(
        sessionId: Long,
        quizLength: QuizLength,
        correctCount: Int,
        totalQuestions: Int,
        durationSeconds: Long
    ): RewardCalculationResult = rewardMutex.withLock {
        // Prevent duplicate reward award
        val session = database.studySessionDao().getSessionById(sessionId)
        val alreadyRewarded = database.screenTimeDao().hasRewardForSession(sessionId) > 0
        if (alreadyRewarded || session?.isCompleted == true) {
            return@withLock RewardCalculationResult(
                baseRewardSeconds = 0,
                accuracyBonusSeconds = 0,
                streakMultiplier = 1.0f,
                totalEarnedSeconds = 0,
                cappedAtLimit = false,
                explanation = "Reward for this session has already been claimed."
            )
        }

        val currentStreak = preferences.currentStreak.first()
        val maxDailyRewardMinutes = preferences.dailyMaxRewardMinutes.first()
        val todayEarnedSeconds = database.screenTimeDao().getEarnedSecondsSince(getStartOfDayTimestamp())
        val remainingDailyCapacity = (maxDailyRewardMinutes * 60L - todayEarnedSeconds).coerceAtLeast(0L)

        val rewardResult = RewardCalculator.calculateReward(
            quizLength = quizLength,
            correctCount = correctCount,
            totalQuestions = totalQuestions,
            isCompleted = true,
            currentStreak = currentStreak,
            dailyRemainingCapSeconds = remainingDailyCapacity
        )

        val now = System.currentTimeMillis()

        // Update session in DB as completed immediately
        if (session != null) {
            database.studySessionDao().updateSession(
                session.copy(
                    correctCount = correctCount,
                    durationSeconds = durationSeconds,
                    earnedSeconds = rewardResult.totalEarnedSeconds,
                    isCompleted = true,
                    completedAt = now
                )
            )
        }

        // Insert transactional ledger entry if earned > 0
        if (rewardResult.totalEarnedSeconds > 0) {
            database.screenTimeDao().insertTransaction(
                ScreenTimeTransactionEntity(
                    timestamp = now,
                    amountSeconds = rewardResult.totalEarnedSeconds,
                    type = TransactionType.STUDY_REWARD.name,
                    sourceSessionId = sessionId,
                    description = "Completed ${session?.deckTitle ?: "Quiz"} (${rewardResult.totalEarnedSeconds / 60}m earned)"
                )
            )
        }

        // Advance study streak
        val lastDate = preferences.lastStudyDate.first()
        val isConsecutive = isConsecutiveDay(lastDate, now)
        val isSameDay = isSameDay(lastDate, now)
        val newStreak = when {
            isSameDay -> currentStreak
            isConsecutive -> currentStreak + 1
            else -> 1
        }
        preferences.updateStreak(newStreak, now)

        // Clear active session
        preferences.setActiveSessionId(null)

        rewardResult
    }

    suspend fun abandonSession(sessionId: Long) {
        val session = database.studySessionDao().getSessionById(sessionId)
        if (session != null && !session.isCompleted) {
            database.studySessionDao().updateSession(
                session.copy(
                    isCompleted = false,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
        preferences.setActiveSessionId(null)
    }

    suspend fun getActiveUnfinishedSession(): StudySessionEntity? {
        val storedId = preferences.activeSessionId.first() ?: return null
        val session = database.studySessionDao().getSessionById(storedId)
        return if (session != null && !session.isCompleted) session else null
    }

    // --- Blocked Apps ---
    val allBlockedAppsFlow: Flow<List<BlockedAppEntity>> =
        database.blockedAppDao().getAllBlockedAppsFlow()

    suspend fun setAppBlocked(packageName: String, isBlocked: Boolean) {
        database.blockedAppDao().setBlockedStatus(packageName, isBlocked)
    }

    suspend fun addCustomBlockedApp(packageName: String, appName: String) {
        database.blockedAppDao().insertOrUpdate(
            BlockedAppEntity(packageName = packageName, appName = appName, isBlocked = true)
        )
    }

    suspend fun isAppProtected(packageName: String): Boolean {
        val blockingGloballyEnabled = preferences.isBlockingEnabled.first()
        if (!blockingGloballyEnabled) return false
        return database.blockedAppDao().isAppBlocked(packageName) == true
    }

    // --- Weekly Study Stats ---
    suspend fun getWeeklyStudyMinutes(): List<Pair<String, Int>> {
        val calendar = Calendar.getInstance()
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val result = mutableListOf<Pair<String, Int>>()

        // Find Monday of the current week
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        for (i in 0..6) {
            val startDay = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val endDay = calendar.timeInMillis

            val sessions = database.studySessionDao().getSessionsSince(startDay)
                .filter { (it.completedAt ?: 0L) < endDay }
            val minutes = (sessions.sumOf { it.durationSeconds } / 60).toInt()
            result.add(Pair(days[i], minutes))
        }

        return result
    }

    private fun getStartOfDayTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        if (t1 == 0L || t2 == 0L) return false
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isConsecutiveDay(lastTime: Long, currentTime: Long): Boolean {
        if (lastTime == 0L) return false
        val c1 = Calendar.getInstance().apply { timeInMillis = lastTime }
        val c2 = Calendar.getInstance().apply { timeInMillis = currentTime }
        c1.add(Calendar.DAY_OF_YEAR, 1)
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    // --- School & Google Drive Integration ---
    val schoolSyncManager: com.example.core.school.SchoolSyncManager =
        com.example.core.school.SchoolSyncManager(context, database, preferences, contentGenerator)

    val activeSchoolConnectionFlow: Flow<SchoolConnectionEntity?> =
        database.schoolConnectionDao().getActiveConnectionFlow()

    val allSchoolMaterialsFlow: Flow<List<SchoolMaterialEntity>> =
        database.schoolMaterialDao().getAllMaterialsFlow()

    val allSubjectsFlow: Flow<List<String>> =
        database.schoolMaterialDao().getAllSubjectsFlow()

    suspend fun connectSchool(
        platformName: String,
        url: String,
        username: String,
        password: String = "",
        keepSignedIn: Boolean,
        onProgress: suspend (String, Float) -> Unit = { _, _ -> }
    ): Result<com.example.core.school.model.PlatformDetectionResult> {
        return schoolSyncManager.connectAndIngestSchool(
            schoolUrl = url,
            authTokenOrPassword = password,
            studentUsername = username,
            onProgress = onProgress
        )
    }

    suspend fun disconnectSchool() {
        schoolSyncManager.disconnectSchool()
    }

    suspend fun autoGenerateDeckFromSchoolMaterial(materialId: Long): Long? {
        val material = database.schoolMaterialDao().getMaterialById(materialId) ?: return null
        return schoolSyncManager.autoGenerateDeckFromMaterial(material)
    }

    suspend fun autoGenerateAllDecksFromSchoolMaterials(): Int {
        val materials = database.schoolMaterialDao().getAllMaterialsList()
        var createdCount = 0
        for (material in materials) {
            schoolSyncManager.autoGenerateDeckFromMaterial(material)
            createdCount++
        }
        return createdCount
    }

    suspend fun syncNewSchoolMaterials(): List<SchoolMaterialEntity> {
        return schoolSyncManager.syncNewMaterials()
    }

    suspend fun deleteSchoolMaterial(materialId: Long) {
        database.schoolMaterialDao().deleteMaterialById(materialId)
    }

    suspend fun toggleMaterialSync(materialId: Long, included: Boolean) {
        database.schoolMaterialDao().setSyncIncluded(materialId, included)
    }
}
