package com.example.core.focustimer

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.core.database.StudyLockDatabase
import com.example.core.database.entity.StudySessionEntity
import com.example.core.datastore.StudyLockPreferences
import com.example.core.model.FocusSessionState
import com.example.core.model.FocusTimerState
import com.example.core.repository.StudyLockRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Global singleton managing focused study sessions, timer tickers,
 * screen time reward banking, and app switching lock enforcements.
 */
object FocusSessionManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    private val _sessionState = MutableStateFlow(FocusSessionState())
    val sessionState: StateFlow<FocusSessionState> = _sessionState.asStateFlow()

    @Volatile
    var isAppSwitchingPrevented: Boolean = true
        private set

    @Volatile
    var currentActiveSubject: String = "General Study"
        private set

    fun isFocusSessionRunning(): Boolean {
        return _sessionState.value.state == FocusTimerState.RUNNING
    }

    fun startTimer(
        context: Context,
        durationMinutes: Int,
        subject: String,
        preventAppSwitching: Boolean
    ) {
        tickerJob?.cancel()
        currentActiveSubject = subject
        isAppSwitchingPrevented = preventAppSwitching

        val totalSecs = durationMinutes * 60L
        _sessionState.value = FocusSessionState(
            state = FocusTimerState.RUNNING,
            targetDurationMinutes = durationMinutes,
            totalSeconds = totalSecs,
            remainingSeconds = totalSecs,
            elapsedSeconds = 0L,
            subject = subject,
            preventAppSwitching = preventAppSwitching,
            earnedRewardMinutes = 0
        )

        triggerHaptic(context, 100)

        tickerJob = scope.launch {
            while (isActive && _sessionState.value.remainingSeconds > 0) {
                delay(1000L)
                val current = _sessionState.value
                if (current.state == FocusTimerState.RUNNING) {
                    val newRemaining = (current.remainingSeconds - 1).coerceAtLeast(0L)
                    val newElapsed = current.elapsedSeconds + 1
                    val earnedMinutes = (newElapsed / 120).toInt() // 1 min screen time per 2 min studied

                    _sessionState.value = current.copy(
                        remainingSeconds = newRemaining,
                        elapsedSeconds = newElapsed,
                        earnedRewardMinutes = earnedMinutes
                    )

                    if (newRemaining == 0L) {
                        onTimerFinished(context.applicationContext)
                        break
                    }
                }
            }
        }
    }

    fun pauseTimer(context: Context) {
        if (_sessionState.value.state == FocusTimerState.RUNNING) {
            _sessionState.value = _sessionState.value.copy(state = FocusTimerState.PAUSED)
            triggerHaptic(context, 80)
        }
    }

    fun resumeTimer(context: Context) {
        if (_sessionState.value.state == FocusTimerState.PAUSED) {
            _sessionState.value = _sessionState.value.copy(state = FocusTimerState.RUNNING)
            triggerHaptic(context, 80)
        }
    }

    /**
     * Stops the timer and banks any earned screen time into the ScreenTimeBank.
     * Returns the number of reward seconds banked.
     */
    suspend fun stopTimer(context: Context, bankEarnedReward: Boolean = true): Long = withContext(Dispatchers.IO) {
        tickerJob?.cancel()
        tickerJob = null

        val current = _sessionState.value
        val elapsed = current.elapsedSeconds
        _sessionState.value = current.copy(state = FocusTimerState.STOPPED)

        if (!bankEarnedReward || elapsed < 60L) {
            return@withContext 0L
        }

        // Calculate screen time reward (1 min screen time per 2 min study, minimum 60s study)
        val baseEarnedSeconds = elapsed / 2L

        val repo = StudyLockRepository(context)
        val streak = repo.preferences.currentStreak.first()
        val streakMultiplier = 1f + (streak * 0.03f).coerceAtMost(0.20f)
        val nextSessionBoost = repo.preferences.consumeNextSessionBoost()
        val totalMultiplier = streakMultiplier * nextSessionBoost
        val totalEarnedSeconds = (baseEarnedSeconds * totalMultiplier).toLong()

        if (totalEarnedSeconds > 0) {
            val boostTag = if (nextSessionBoost > 1.0f) " [🔥 ${nextSessionBoost}x Ad Boost Applied!]" else ""
            val note = "Focused Study: ${current.subject} (${elapsed / 60}m ${elapsed % 60}s)$boostTag"
            repo.addScreenTimeReward(totalEarnedSeconds, note)

            // Save to study sessions so daily goal ring & weekly chart update
            val db = StudyLockDatabase.getInstance(context)
            val now = System.currentTimeMillis()
            db.studySessionDao().insertSession(
                StudySessionEntity(
                    deckId = null,
                    deckTitle = "Focused Study: ${current.subject}",
                    mode = "FOCUS_TIMER",
                    totalQuestions = (elapsed / 60).toInt().coerceAtLeast(1),
                    correctCount = (elapsed / 60).toInt().coerceAtLeast(1),
                    durationSeconds = elapsed,
                    earnedSeconds = totalEarnedSeconds,
                    isCompleted = true,
                    startedAt = now - (elapsed * 1000L),
                    completedAt = now
                )
            )
        }

        triggerHaptic(context, 150)
        return@withContext totalEarnedSeconds
    }

    fun resetTimer() {
        tickerJob?.cancel()
        tickerJob = null
        val defaultMinutes = 25
        _sessionState.value = FocusSessionState(
            state = FocusTimerState.STOPPED,
            targetDurationMinutes = defaultMinutes,
            totalSeconds = defaultMinutes * 60L,
            remainingSeconds = defaultMinutes * 60L,
            elapsedSeconds = 0L,
            subject = currentActiveSubject,
            preventAppSwitching = isAppSwitchingPrevented,
            earnedRewardMinutes = 0
        )
    }

    private suspend fun onTimerFinished(context: Context) = withContext(Dispatchers.IO) {
        val current = _sessionState.value
        _sessionState.value = current.copy(state = FocusTimerState.COMPLETED)

        // Award full session reward
        val elapsed = current.elapsedSeconds
        val baseEarnedSeconds = elapsed / 2L
        val repo = StudyLockRepository(context)
        val streak = repo.preferences.currentStreak.first()
        val streakMultiplier = 1f + (streak * 0.03f).coerceAtMost(0.20f)
        val nextSessionBoost = repo.preferences.consumeNextSessionBoost()
        val totalMultiplier = streakMultiplier * nextSessionBoost
        val totalEarnedSeconds = (baseEarnedSeconds * totalMultiplier).toLong()

        val boostTag = if (nextSessionBoost > 1.0f) " [🔥 ${nextSessionBoost}x Ad Boost Applied!]" else ""
        val note = "Completed Focus Session: ${current.subject} (${current.targetDurationMinutes}m)$boostTag"
        repo.addScreenTimeReward(totalEarnedSeconds, note)

        val db = StudyLockDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        db.studySessionDao().insertSession(
            StudySessionEntity(
                deckId = null,
                deckTitle = "Focused Study: ${current.subject}",
                mode = "FOCUS_TIMER",
                totalQuestions = current.targetDurationMinutes,
                correctCount = current.targetDurationMinutes,
                durationSeconds = elapsed,
                earnedSeconds = totalEarnedSeconds,
                isCompleted = true,
                startedAt = now - (elapsed * 1000L),
                completedAt = now
            )
        )

        triggerHaptic(context, 300)
    }

    private fun triggerHaptic(context: Context, millis: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(millis)
                }
            }
        } catch (_: Exception) {}
    }
}
