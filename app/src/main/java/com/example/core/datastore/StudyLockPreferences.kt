package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studylock_prefs")

class StudyLockPreferences(private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_DAILY_STUDY_GOAL_MINUTES = intPreferencesKey("daily_study_goal_minutes")
        val KEY_DAILY_MAX_REWARD_MINUTES = intPreferencesKey("daily_max_reward_minutes")
        val KEY_MAX_ACCUMULATED_BANK_MINUTES = intPreferencesKey("max_accumulated_bank_minutes")
        val KEY_BLOCKING_ENABLED = booleanPreferencesKey("blocking_enabled")
        val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val KEY_ACTIVE_SESSION_ID = longPreferencesKey("active_session_id")
        val KEY_LAST_CLOCK_CHECK_TIME = longPreferencesKey("last_clock_check_time")
        val KEY_LAST_ELAPSED_REALTIME = longPreferencesKey("last_elapsed_realtime")
        val KEY_CURRENT_STREAK = intPreferencesKey("current_streak")
        val KEY_LAST_STUDY_DATE = longPreferencesKey("last_study_date")
        val KEY_GOOGLE_DRIVE_CONNECTED = booleanPreferencesKey("google_drive_connected")
        val KEY_GOOGLE_DRIVE_ACCOUNT = stringPreferencesKey("google_drive_account")
        val KEY_GOOGLE_DRIVE_DISPLAY_NAME = stringPreferencesKey("google_drive_display_name")
        val KEY_GOOGLE_DRIVE_ROOT_FOLDER_ID = stringPreferencesKey("google_drive_root_folder_id")
        val KEY_SCHOOL_AUTO_SYNC_ENABLED = booleanPreferencesKey("school_auto_sync_enabled")
        val KEY_SCHOOL_PLATFORM = stringPreferencesKey("school_platform")
        val KEY_SCHOOL_URL = stringPreferencesKey("school_url")
        val KEY_SCHOOL_USERNAME = stringPreferencesKey("school_username")
        val KEY_SCHOOL_KEEP_SIGNED_IN = booleanPreferencesKey("school_keep_signed_in")
        val KEY_NEXT_SESSION_BOOST_ACTIVE = booleanPreferencesKey("next_session_boost_active")
        val KEY_NEXT_SESSION_BOOST_MULTIPLIER = floatPreferencesKey("next_session_boost_multiplier")
        val KEY_LAST_REWARDED_AD_CLAIMED_AT = longPreferencesKey("last_rewarded_ad_claimed_at")
        val KEY_TOTAL_REWARDED_ADS_WATCHED = intPreferencesKey("total_rewarded_ads_watched")

        val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        val KEY_USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val KEY_USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
        val KEY_USER_ID_TOKEN = stringPreferencesKey("user_id_token")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_LAST_LOGIN_AT = longPreferencesKey("last_login_at")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val dailyStudyGoalMinutes: Flow<Int> = context.dataStore.data.map {
        it[KEY_DAILY_STUDY_GOAL_MINUTES] ?: 30
    }

    val dailyMaxRewardMinutes: Flow<Int> = context.dataStore.data.map {
        it[KEY_DAILY_MAX_REWARD_MINUTES] ?: 120
    }

    val maxAccumulatedBankMinutes: Flow<Int> = context.dataStore.data.map {
        it[KEY_MAX_ACCUMULATED_BANK_MINUTES] ?: 180
    }

    val isBlockingEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_BLOCKING_ENABLED] ?: true
    }

    val isHapticEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_HAPTIC_ENABLED] ?: true
    }

    val activeSessionId: Flow<Long?> = context.dataStore.data.map {
        val id = it[KEY_ACTIVE_SESSION_ID] ?: -1L
        if (id > 0) id else null
    }

    val currentStreak: Flow<Int> = context.dataStore.data.map {
        it[KEY_CURRENT_STREAK] ?: 0
    }

    val lastStudyDate: Flow<Long> = context.dataStore.data.map {
        it[KEY_LAST_STUDY_DATE] ?: 0L
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDailyStudyGoalMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_DAILY_STUDY_GOAL_MINUTES] = minutes }
    }

    suspend fun setDailyMaxRewardMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_DAILY_MAX_REWARD_MINUTES] = minutes }
    }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLOCKING_ENABLED] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_ENABLED] = enabled }
    }

    suspend fun setActiveSessionId(sessionId: Long?) {
        context.dataStore.edit {
            if (sessionId != null && sessionId > 0) {
                it[KEY_ACTIVE_SESSION_ID] = sessionId
            } else {
                it.remove(KEY_ACTIVE_SESSION_ID)
            }
        }
    }

    suspend fun updateStreak(newStreak: Int, studyDate: Long) {
        context.dataStore.edit {
            it[KEY_CURRENT_STREAK] = newStreak
            it[KEY_LAST_STUDY_DATE] = studyDate
        }
    }

    suspend fun saveTimeIntegrityCheckpoint(wallClock: Long, elapsedMonotonic: Long) {
        context.dataStore.edit {
            it[KEY_LAST_CLOCK_CHECK_TIME] = wallClock
            it[KEY_LAST_ELAPSED_REALTIME] = elapsedMonotonic
        }
    }

    val isGoogleDriveConnected: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_GOOGLE_DRIVE_CONNECTED] ?: false
    }

    val googleDriveAccount: Flow<String> = context.dataStore.data.map {
        it[KEY_GOOGLE_DRIVE_ACCOUNT] ?: ""
    }

    val googleDriveDisplayName: Flow<String> = context.dataStore.data.map {
        it[KEY_GOOGLE_DRIVE_DISPLAY_NAME] ?: ""
    }

    val googleDriveRootFolderId: Flow<String> = context.dataStore.data.map {
        it[KEY_GOOGLE_DRIVE_ROOT_FOLDER_ID] ?: ""
    }

    val isSchoolAutoSyncEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SCHOOL_AUTO_SYNC_ENABLED] ?: true
    }

    val schoolPlatformName: Flow<String> = context.dataStore.data.map {
        it[KEY_SCHOOL_PLATFORM] ?: ""
    }

    val schoolUrl: Flow<String> = context.dataStore.data.map {
        it[KEY_SCHOOL_URL] ?: ""
    }

    val schoolUsername: Flow<String> = context.dataStore.data.map {
        it[KEY_SCHOOL_USERNAME] ?: ""
    }

    val schoolKeepSignedIn: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SCHOOL_KEEP_SIGNED_IN] ?: true
    }

    suspend fun setGoogleDriveConnected(
        connected: Boolean,
        accountEmail: String = "",
        displayName: String = "",
        rootFolderId: String = ""
    ) {
        context.dataStore.edit {
            it[KEY_GOOGLE_DRIVE_CONNECTED] = connected
            if (accountEmail.isNotBlank()) it[KEY_GOOGLE_DRIVE_ACCOUNT] = accountEmail
            if (displayName.isNotBlank()) it[KEY_GOOGLE_DRIVE_DISPLAY_NAME] = displayName
            if (rootFolderId.isNotBlank()) it[KEY_GOOGLE_DRIVE_ROOT_FOLDER_ID] = rootFolderId
        }
    }

    suspend fun setGoogleDriveRootFolderId(folderId: String) {
        context.dataStore.edit {
            it[KEY_GOOGLE_DRIVE_ROOT_FOLDER_ID] = folderId
        }
    }

    suspend fun disconnectGoogleDrive() {
        context.dataStore.edit {
            it[KEY_GOOGLE_DRIVE_CONNECTED] = false
            it.remove(KEY_GOOGLE_DRIVE_ACCOUNT)
            it.remove(KEY_GOOGLE_DRIVE_DISPLAY_NAME)
            it.remove(KEY_GOOGLE_DRIVE_ROOT_FOLDER_ID)
        }
    }

    suspend fun setSchoolConnection(
        platform: String,
        url: String,
        username: String,
        keepSignedIn: Boolean
    ) {
        context.dataStore.edit {
            it[KEY_SCHOOL_PLATFORM] = platform
            it[KEY_SCHOOL_URL] = url
            it[KEY_SCHOOL_USERNAME] = username
            it[KEY_SCHOOL_KEEP_SIGNED_IN] = keepSignedIn
        }
    }

    suspend fun setSchoolAutoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_SCHOOL_AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun clearSchoolConnection() {
        context.dataStore.edit {
            it.remove(KEY_SCHOOL_PLATFORM)
            it.remove(KEY_SCHOOL_URL)
            it.remove(KEY_SCHOOL_USERNAME)
        }
    }

    val nextSessionBoostActive: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_NEXT_SESSION_BOOST_ACTIVE] ?: false
    }

    val nextSessionBoostMultiplier: Flow<Float> = context.dataStore.data.map {
        it[KEY_NEXT_SESSION_BOOST_MULTIPLIER] ?: 1.0f
    }

    suspend fun activateNextSessionBoost(multiplier: Float = 1.5f) {
        context.dataStore.edit {
            it[KEY_NEXT_SESSION_BOOST_ACTIVE] = true
            it[KEY_NEXT_SESSION_BOOST_MULTIPLIER] = multiplier
            it[KEY_LAST_REWARDED_AD_CLAIMED_AT] = System.currentTimeMillis()
            val currentCount = it[KEY_TOTAL_REWARDED_ADS_WATCHED] ?: 0
            it[KEY_TOTAL_REWARDED_ADS_WATCHED] = currentCount + 1
        }
    }

    suspend fun consumeNextSessionBoost(): Float {
        var multiplier = 1.0f
        context.dataStore.edit {
            if (it[KEY_NEXT_SESSION_BOOST_ACTIVE] == true) {
                multiplier = it[KEY_NEXT_SESSION_BOOST_MULTIPLIER] ?: 1.5f
                it[KEY_NEXT_SESSION_BOOST_ACTIVE] = false
                it[KEY_NEXT_SESSION_BOOST_MULTIPLIER] = 1.0f
            }
        }
        return multiplier
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_IS_LOGGED_IN] ?: false
    }

    val userEmail: Flow<String> = context.dataStore.data.map {
        it[KEY_USER_EMAIL] ?: ""
    }

    val userDisplayName: Flow<String> = context.dataStore.data.map {
        it[KEY_USER_DISPLAY_NAME] ?: ""
    }

    val userPhotoUrl: Flow<String?> = context.dataStore.data.map {
        it[KEY_USER_PHOTO_URL]
    }

    suspend fun saveUserSession(
        email: String,
        displayName: String,
        photoUrl: String? = null,
        idToken: String? = null
    ) {
        context.dataStore.edit {
            it[KEY_USER_EMAIL] = email
            it[KEY_USER_DISPLAY_NAME] = displayName
            if (photoUrl != null) it[KEY_USER_PHOTO_URL] = photoUrl
            if (idToken != null) it[KEY_USER_ID_TOKEN] = idToken
            it[KEY_IS_LOGGED_IN] = true
            it[KEY_LAST_LOGIN_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearUserSession() {
        context.dataStore.edit {
            it[KEY_IS_LOGGED_IN] = false
            it.remove(KEY_USER_EMAIL)
            it.remove(KEY_USER_DISPLAY_NAME)
            it.remove(KEY_USER_PHOTO_URL)
            it.remove(KEY_USER_ID_TOKEN)
        }
    }
}
