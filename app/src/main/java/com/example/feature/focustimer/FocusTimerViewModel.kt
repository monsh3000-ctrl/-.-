package com.example.feature.focustimer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.StudyLockDatabase
import com.example.core.focustimer.FocusSessionManager
import com.example.core.model.FocusSessionState
import com.example.core.model.FocusTimerState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FocusTimerViewModel(application: Application) : AndroidViewModel(application) {

    val sessionState: StateFlow<FocusSessionState> = FocusSessionManager.sessionState

    private val database = StudyLockDatabase.getInstance(application)
    val availableSubjects: Flow<List<String>> = database.schoolMaterialDao().getAllSubjectsFlow()

    private val preferences = com.example.core.datastore.StudyLockPreferences(application)
    val nextSessionBoostActive: Flow<Boolean> = preferences.nextSessionBoostActive
    val nextSessionBoostMultiplier: Flow<Float> = preferences.nextSessionBoostMultiplier

    private val _lastBankedRewardSeconds = MutableStateFlow<Long?>(null)
    val lastBankedRewardSeconds: StateFlow<Long?> = _lastBankedRewardSeconds.asStateFlow()

    fun startTimer(durationMinutes: Int, subject: String, preventAppSwitching: Boolean) {
        _lastBankedRewardSeconds.value = null
        FocusSessionManager.startTimer(
            context = getApplication(),
            durationMinutes = durationMinutes,
            subject = subject,
            preventAppSwitching = preventAppSwitching
        )
    }

    fun pauseTimer() {
        FocusSessionManager.pauseTimer(getApplication())
    }

    fun resumeTimer() {
        FocusSessionManager.resumeTimer(getApplication())
    }

    fun stopTimer(
        bankReward: Boolean = true,
        onCompleted: (durationSeconds: Long, subject: String, earnedSeconds: Long) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch {
            val duration = sessionState.value.elapsedSeconds
            val subject = sessionState.value.subject
            val bankedSeconds = FocusSessionManager.stopTimer(getApplication(), bankReward)
            _lastBankedRewardSeconds.value = bankedSeconds
            onCompleted(duration, subject, bankedSeconds)
        }
    }

    fun resetTimer() {
        _lastBankedRewardSeconds.value = null
        FocusSessionManager.resetTimer()
    }
}
