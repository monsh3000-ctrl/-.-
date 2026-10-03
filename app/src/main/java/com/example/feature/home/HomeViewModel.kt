package com.example.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.StudyDeckEntity
import com.example.core.database.entity.StudySessionEntity
import com.example.core.model.ScreenTimeBalance
import com.example.core.repository.StudyLockRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val balance: ScreenTimeBalance = ScreenTimeBalance(0, 0, 0, 0),
    val dailyGoalMinutes: Int = 30,
    val currentStreak: Int = 0,
    val weeklyMinutes: List<Pair<String, Int>> = emptyList(),
    val starterDecks: List<StudyDeckEntity> = emptyList(),
    val unfinishedSession: StudySessionEntity? = null,
    val nextSessionBoostActive: Boolean = false,
    val nextSessionBoostMultiplier: Float = 1.0f,
    val isLoading: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudyLockRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshDashboard()
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            val boostFlow = combine(
                repository.preferences.nextSessionBoostActive,
                repository.preferences.nextSessionBoostMultiplier
            ) { active, mult -> active to mult }

            combine(
                repository.balanceSecondsFlow,
                repository.preferences.dailyStudyGoalMinutes,
                repository.preferences.currentStreak,
                boostFlow,
                repository.allDecksFlow
            ) { _, goalMinutes, streak, boostPair, decks ->
                val (boostActive, boostMultiplier) = boostPair
                val balance = repository.getScreenTimeBalance()
                val unfinished = repository.getActiveUnfinishedSession()
                val weekly = repository.getWeeklyStudyMinutes()

                HomeUiState(
                    balance = balance,
                    dailyGoalMinutes = goalMinutes,
                    currentStreak = streak,
                    weeklyMinutes = weekly,
                    starterDecks = decks.take(3),
                    unfinishedSession = unfinished,
                    nextSessionBoostActive = boostActive,
                    nextSessionBoostMultiplier = boostMultiplier,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            val balance = repository.getScreenTimeBalance()
            val weekly = repository.getWeeklyStudyMinutes()
            val unfinished = repository.getActiveUnfinishedSession()
            val goal = repository.preferences.dailyStudyGoalMinutes.first()
            val streak = repository.preferences.currentStreak.first()
            val decks = repository.allDecksFlow.first()

            _uiState.value = _uiState.value.copy(
                balance = balance,
                dailyGoalMinutes = goal,
                currentStreak = streak,
                weeklyMinutes = weekly,
                starterDecks = decks.take(3),
                unfinishedSession = unfinished,
                isLoading = false
            )
        }
    }

    fun abandonUnfinishedSession() {
        viewModelScope.launch {
            val session = _uiState.value.unfinishedSession
            if (session != null) {
                repository.abandonSession(session.id)
                refreshDashboard()
            }
        }
    }
}
