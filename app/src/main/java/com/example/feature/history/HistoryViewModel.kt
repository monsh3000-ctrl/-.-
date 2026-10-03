package com.example.feature.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.StudySessionEntity
import com.example.core.repository.StudyLockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sessions: List<StudySessionEntity> = emptyList(),
    val totalStudyMinutes: Long = 0,
    val totalEarnedMinutes: Long = 0,
    val isLoading: Boolean = true
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudyLockRepository(application)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            repository.database.studySessionDao().getCompletedSessions().collect { list ->
                val totalDurationSecs = list.sumOf { it.durationSeconds }
                val totalEarnedSecs = list.sumOf { it.earnedSeconds }
                _uiState.value = HistoryUiState(
                    sessions = list,
                    totalStudyMinutes = totalDurationSecs / 60,
                    totalEarnedMinutes = totalEarnedSecs / 60,
                    isLoading = false
                )
            }
        }
    }
}
