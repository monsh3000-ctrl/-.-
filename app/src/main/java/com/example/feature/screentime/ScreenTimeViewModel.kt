package com.example.feature.screentime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.BlockedAppEntity
import com.example.core.database.entity.ScreenTimeTransactionEntity
import com.example.core.model.ScreenTimeBalance
import com.example.core.repository.StudyLockRepository
import com.example.service.usage.UsageTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ScreenTimeBankUiState(
    val balance: ScreenTimeBalance = ScreenTimeBalance(0, 0, 0, 0),
    val transactions: List<ScreenTimeTransactionEntity> = emptyList(),
    val blockedApps: List<BlockedAppEntity> = emptyList(),
    val appUsageTodaySeconds: Map<String, Long> = emptyMap(),
    val totalDeviceScreenTimeTodaySeconds: Long = 0L,
    val isGlobalBlockingEnabled: Boolean = true,
    val hasUsagePermission: Boolean = false,
    val isLoading: Boolean = true
)

class ScreenTimeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudyLockRepository(application)
    private val usageTracker = UsageTracker(application)

    private val _uiState = MutableStateFlow(ScreenTimeBankUiState())
    val uiState: StateFlow<ScreenTimeBankUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.balanceSecondsFlow,
                repository.recentTransactionsFlow,
                repository.allBlockedAppsFlow,
                repository.preferences.isBlockingEnabled
            ) { _, transactions, apps, globalBlocking ->
                val balance = repository.getScreenTimeBalance()
                val pkgNames = apps.map { it.packageName }
                val usageMap = usageTracker.getTodayUsageForPackages(pkgNames)
                val deviceScreenTime = usageTracker.getTodayTotalDeviceScreenTimeSeconds()

                ScreenTimeBankUiState(
                    balance = balance,
                    transactions = transactions,
                    blockedApps = apps,
                    appUsageTodaySeconds = usageMap,
                    totalDeviceScreenTimeTodaySeconds = deviceScreenTime,
                    isGlobalBlockingEnabled = globalBlocking,
                    hasUsagePermission = usageTracker.isPermissionGranted(),
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            val balance = repository.getScreenTimeBalance()
            _uiState.value = _uiState.value.copy(
                balance = balance,
                hasUsagePermission = usageTracker.isPermissionGranted()
            )
        }
    }

    fun setAppBlocked(packageName: String, isBlocked: Boolean) {
        viewModelScope.launch {
            repository.setAppBlocked(packageName, isBlocked)
        }
    }

    fun setGlobalBlocking(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setBlockingEnabled(enabled)
        }
    }

    fun addCustomBlockedApp(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.addCustomBlockedApp(packageName, appName)
        }
    }

    fun addAdjustment(minutes: Int, reason: String) {
        viewModelScope.launch {
            repository.addManualAdjustment(minutes * 60L, reason)
        }
    }
}
