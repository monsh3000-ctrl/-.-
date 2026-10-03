package com.example.feature.study

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.FlashcardEntity
import com.example.core.database.entity.QuestionEntity
import com.example.core.database.entity.StudyDeckEntity
import com.example.core.model.*
import com.example.core.repository.StudyLockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StudyUiState(
    val deck: StudyDeckEntity? = null,
    val mode: StudyMode = StudyMode.QUIZ,
    val quizLength: QuizLength = QuizLength.STANDARD,
    val questions: List<QuestionEntity> = emptyList(),
    val flashcards: List<FlashcardEntity> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedOptionIndex: Int? = null,
    val isAnswerSubmitted: Boolean = false,
    val correctCount: Int = 0,
    val durationSeconds: Long = 0,
    val isSessionActive: Boolean = false,
    val activeSessionId: Long? = null,
    val rewardResult: RewardCalculationResult? = null,
    val currentBankBalance: Long = 0,
    val showAbandonDialog: Boolean = false,
    val isFinishing: Boolean = false,
    val isLoading: Boolean = false
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudyLockRepository(application)
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Application.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Application.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private var sessionStartTime: Long = 0L
    @Volatile
    private var isFinishingSession: Boolean = false

    /**
     * Atomically initializes deck, questions, database session, and prepares UI before navigation.
     * Guarantees activeSessionId is non-null from the very first question to prevent completion drop.
     */
    fun startNewSession(
        deckId: Long,
        mode: StudyMode = StudyMode.QUIZ,
        length: QuizLength = QuizLength.STANDARD,
        onReady: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, mode = mode, quizLength = length)
            isFinishingSession = false

            var deck = repository.getDeckById(deckId)
            if (deck == null) {
                val allDecks = repository.database.studyDeckDao().getAllDecks()
                deck = repository.database.studyDeckDao().getDeckById(1L)
            }

            var questions = repository.getQuestionsForDeck(deckId)
            var flashcards = repository.getFlashcardsForDeck(deckId)

            if (questions.isEmpty() && deck != null && deck.sourceText.isNotBlank()) {
                repository.generateAndSaveDeckContent(deckId, deck.sourceText, length.questionCount, 5)
                questions = repository.getQuestionsForDeck(deckId)
                flashcards = repository.getFlashcardsForDeck(deckId)
            }

            val cappedQuestions = questions.take(length.questionCount)
            val deckTitle = deck?.title ?: "Study Session"

            val sessionId = repository.startStudySession(
                deckId = deck?.id ?: deckId,
                deckTitle = deckTitle,
                mode = mode,
                totalQuestions = if (mode == StudyMode.QUIZ) cappedQuestions.size else flashcards.size
            )

            sessionStartTime = System.currentTimeMillis()

            _uiState.value = StudyUiState(
                deck = deck,
                mode = mode,
                quizLength = length,
                questions = cappedQuestions,
                flashcards = flashcards,
                currentQuestionIndex = 0,
                selectedOptionIndex = null,
                isAnswerSubmitted = false,
                correctCount = 0,
                isSessionActive = true,
                activeSessionId = sessionId,
                rewardResult = null,
                isFinishing = false,
                isLoading = false
            )

            onReady()
        }
    }

    fun resumeExistingSession(
        sessionId: Long,
        deckId: Long,
        mode: StudyMode,
        onReady: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            isFinishingSession = false

            val deck = repository.getDeckById(deckId)
            val questions = repository.getQuestionsForDeck(deckId)
            val flashcards = repository.getFlashcardsForDeck(deckId)

            sessionStartTime = System.currentTimeMillis()

            _uiState.value = StudyUiState(
                deck = deck,
                mode = mode,
                questions = questions,
                flashcards = flashcards,
                currentQuestionIndex = 0,
                selectedOptionIndex = null,
                isAnswerSubmitted = false,
                correctCount = 0,
                isSessionActive = true,
                activeSessionId = sessionId,
                rewardResult = null,
                isFinishing = false,
                isLoading = false
            )

            onReady()
        }
    }

    fun selectOption(optionIndex: Int) {
        if (_uiState.value.isAnswerSubmitted) return

        val state = _uiState.value
        val currentQuestion = state.questions.getOrNull(state.currentQuestionIndex) ?: return
        val isCorrect = optionIndex == currentQuestion.correctIndex

        triggerHaptic(isCorrect)

        val updatedCorrectCount = if (isCorrect) state.correctCount + 1 else state.correctCount
        _uiState.value = state.copy(
            selectedOptionIndex = optionIndex,
            isAnswerSubmitted = true,
            correctCount = updatedCorrectCount
        )

        viewModelScope.launch {
            val sessionId = state.activeSessionId
            if (sessionId != null) {
                repository.recordAnswer(
                    sessionId = sessionId,
                    questionId = currentQuestion.id,
                    selectedIndex = optionIndex,
                    isCorrect = isCorrect
                )
            }
        }
    }

    fun nextQuestion(onCompleted: () -> Unit) {
        val state = _uiState.value
        if (state.currentQuestionIndex + 1 < state.questions.size) {
            _uiState.value = state.copy(
                currentQuestionIndex = state.currentQuestionIndex + 1,
                selectedOptionIndex = null,
                isAnswerSubmitted = false
            )
        } else {
            // Final question: Complete and claim reward immediately on the first tap
            finishSession(onCompleted)
        }
    }

    fun finishFlashcards(onCompleted: () -> Unit) {
        finishSession(onCompleted)
    }

    /**
     * Completes session deterministically on the very first try.
     * Prevents double rewards using atomic state checking and database idempotency.
     */
    private fun finishSession(onCompleted: () -> Unit) {
        if (isFinishingSession) return
        isFinishingSession = true

        val state = _uiState.value

        // If this session has already been rewarded, do not duplicate reward; proceed to results
        if (state.rewardResult != null) {
            onCompleted()
            return
        }

        _uiState.value = state.copy(isFinishing = true)

        viewModelScope.launch {
            try {
                // Ensure a valid sessionId exists even in extreme edge cases
                val sessionId = state.activeSessionId ?: repository.startStudySession(
                    deckId = state.deck?.id ?: 1L,
                    deckTitle = state.deck?.title ?: "Study Quiz",
                    mode = state.mode,
                    totalQuestions = if (state.mode == StudyMode.QUIZ) state.questions.size else state.flashcards.size
                )

                val duration = ((System.currentTimeMillis() - sessionStartTime) / 1000).coerceAtLeast(10L)
                val total = if (state.mode == StudyMode.QUIZ) state.questions.size else state.flashcards.size
                val correct = if (state.mode == StudyMode.QUIZ) state.correctCount else total

                val rewardResult = repository.completeStudySession(
                    sessionId = sessionId,
                    quizLength = state.quizLength,
                    correctCount = correct,
                    totalQuestions = total,
                    durationSeconds = duration
                )

                val currentBalance = repository.database.screenTimeDao().getCurrentBalance()

                _uiState.value = state.copy(
                    isSessionActive = false,
                    activeSessionId = null, // Clear active session to prevent double submission
                    durationSeconds = duration,
                    rewardResult = rewardResult,
                    currentBankBalance = currentBalance,
                    isFinishing = false
                )

                onCompleted()
            } catch (e: Exception) {
                isFinishingSession = false
                _uiState.value = _uiState.value.copy(isFinishing = false)
                onCompleted()
            }
        }
    }

    fun promptAbandonSession() {
        _uiState.value = _uiState.value.copy(showAbandonDialog = true)
    }

    fun dismissAbandonDialog() {
        _uiState.value = _uiState.value.copy(showAbandonDialog = false)
    }

    fun confirmAbandonSession(onAbandoned: () -> Unit) {
        val sessionId = _uiState.value.activeSessionId
        viewModelScope.launch {
            if (sessionId != null) {
                repository.abandonSession(sessionId)
            }
            _uiState.value = _uiState.value.copy(
                isSessionActive = false,
                activeSessionId = null,
                showAbandonDialog = false
            )
            onAbandoned()
        }
    }

    private fun triggerHaptic(success: Boolean) {
        viewModelScope.launch {
            val enabled = repository.preferences.isHapticEnabled.first()
            if (enabled && vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = if (success) {
                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    } else {
                        VibrationEffect.createWaveform(longArrayOf(0, 70, 70, 70), -1)
                    }
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if (success) 50 else 150)
                }
            }
        }
    }
}
