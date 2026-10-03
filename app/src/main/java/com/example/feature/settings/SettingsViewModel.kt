package com.example.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.SchoolConnectionEntity
import com.example.core.permissions.PermissionManager
import com.example.core.permissions.SystemPermissionsState
import com.example.core.repository.StudyLockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val dailyGoalMinutes: Int = 30,
    val dailyMaxRewardMinutes: Int = 120,
    val isBlockingEnabled: Boolean = true,
    val isHapticEnabled: Boolean = true,
    val schoolConnection: SchoolConnectionEntity? = null,
    val isGoogleDriveConnected: Boolean = false,
    val googleDriveAccount: String = "",
    val isAutoSyncEnabled: Boolean = true,
    val importedMaterialCount: Int = 0,
    val permissions: SystemPermissionsState = SystemPermissionsState(
        hasCamera = false,
        hasUsageStats = false,
        hasAccessibility = false,
        hasOverlay = false,
        hasNotifications = false
    )
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudyLockRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun refreshPermissions() {
        val permState = PermissionManager.getFullPermissionState(getApplication())
        _uiState.value = _uiState.value.copy(permissions = permState)
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val studyPrefsFlow = combine(
                repository.preferences.dailyStudyGoalMinutes,
                repository.preferences.dailyMaxRewardMinutes,
                repository.preferences.isBlockingEnabled,
                repository.preferences.isHapticEnabled
            ) { goal, maxReward, blocking, haptic ->
                listOf(goal, maxReward, blocking, haptic)
            }

            val cloudFlow = combine(
                repository.preferences.isGoogleDriveConnected,
                repository.preferences.googleDriveAccount,
                repository.activeSchoolConnectionFlow,
                repository.preferences.isSchoolAutoSyncEnabled
            ) { driveConnected, driveAccount, schoolConn, autoSync ->
                listOf(driveConnected, driveAccount, schoolConn, autoSync)
            }

            combine(studyPrefsFlow, cloudFlow) { studyPrefs, cloudList ->
                val goal = studyPrefs[0] as Int
                val maxReward = studyPrefs[1] as Int
                val blocking = studyPrefs[2] as Boolean
                val haptic = studyPrefs[3] as Boolean

                val driveConnected = cloudList[0] as Boolean
                val driveAccount = cloudList[1] as String
                val schoolConn = cloudList[2] as? SchoolConnectionEntity
                val autoSync = cloudList[3] as Boolean

                val materialCount = try {
                    repository.database.schoolMaterialDao().getMaterialCount()
                } catch (e: Exception) {
                    0
                }

                val perms = try {
                    PermissionManager.getFullPermissionState(getApplication())
                } catch (e: Exception) {
                    SystemPermissionsState(
                        hasCamera = false,
                        hasUsageStats = false,
                        hasAccessibility = false,
                        hasOverlay = false,
                        hasNotifications = false
                    )
                }

                SettingsUiState(
                    dailyGoalMinutes = goal,
                    dailyMaxRewardMinutes = maxReward,
                    isBlockingEnabled = blocking,
                    isHapticEnabled = haptic,
                    schoolConnection = schoolConn,
                    isGoogleDriveConnected = driveConnected,
                    googleDriveAccount = driveAccount,
                    isAutoSyncEnabled = autoSync,
                    importedMaterialCount = materialCount,
                    permissions = perms
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch { repository.preferences.setDailyStudyGoalMinutes(minutes) }
    }

    fun setDailyMaxReward(minutes: Int) {
        viewModelScope.launch { repository.preferences.setDailyMaxRewardMinutes(minutes) }
    }

    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.preferences.setBlockingEnabled(enabled) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.preferences.setHapticEnabled(enabled) }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setSchoolAutoSyncEnabled(enabled)
            repository.database.schoolConnectionDao().setAutoSyncEnabled(enabled)
        }
    }

    fun disconnectSchool() {
        viewModelScope.launch {
            repository.disconnectSchool()
        }
    }

    fun regenerateStudyDecks(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.autoGenerateAllDecksFromSchoolMaterials()
            onComplete(count)
        }
    }

    fun clearImportedMaterials() {
        viewModelScope.launch {
            repository.database.schoolMaterialDao().clearAllMaterials()
        }
    }
}
