package com.example.core.model

enum class QuizLength(val questionCount: Int, val maxRewardMinutes: Int, val label: String) {
    SHORT(3, 5, "Short (3 Qs · Up to 5m)"),
    STANDARD(5, 15, "Standard (5 Qs · Up to 15m)"),
    DEEP_DIVE(10, 30, "Deep Dive (10 Qs · Up to 30m)");

    val maxRewardSeconds: Long get() = maxRewardMinutes * 60L
}

enum class StudyMode(val label: String) {
    QUIZ("Multiple Choice Quiz"),
    FLASHCARDS("Interactive Flashcards")
}

data class QuestionItem(
    val id: Long = 0,
    val deckId: Long,
    val prompt: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctIndex: Int, // 0..3
    val explanation: String
) {
    val options: List<String> get() = listOf(optionA, optionB, optionC, optionD)
}

enum class FlashcardRating {
    RETRY, HARD, EASY
}

data class FlashcardItem(
    val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val reviewCount: Int = 0,
    val easeFactor: Float = 2.5f
)

data class StudySessionSummary(
    val sessionId: Long,
    val deckTitle: String,
    val mode: StudyMode,
    val totalQuestions: Int,
    val correctCount: Int,
    val accuracyPercent: Int,
    val durationSeconds: Long,
    val earnedSeconds: Long,
    val completedAt: Long
)

data class RewardCalculationResult(
    val baseRewardSeconds: Long,
    val accuracyBonusSeconds: Long,
    val streakMultiplier: Float,
    val totalEarnedSeconds: Long,
    val cappedAtLimit: Boolean,
    val explanation: String
)

enum class FocusTimerState {
    STOPPED,
    RUNNING,
    PAUSED,
    COMPLETED
}

data class FocusSessionState(
    val state: FocusTimerState = FocusTimerState.STOPPED,
    val targetDurationMinutes: Int = 25,
    val totalSeconds: Long = 25 * 60L,
    val remainingSeconds: Long = 25 * 60L,
    val elapsedSeconds: Long = 0L,
    val subject: String = "General Study",
    val preventAppSwitching: Boolean = true,
    val earnedRewardMinutes: Int = 0
)

