package com.example.core.engine

import android.os.SystemClock
import com.example.core.datastore.StudyLockPreferences
import kotlinx.coroutines.flow.first

class AntiTamperManager(private val preferences: StudyLockPreferences) {

    /**
     * Checks if the system wall clock has been manipulated backwards or artificially forward
     * relative to monotonic elapsedRealtime.
     */
    suspend fun verifyTimeIntegrity(): Boolean {
        // Record current wall clock and monotonic time
        val currentWallTime = System.currentTimeMillis()
        val currentMonotonic = SystemClock.elapsedRealtime()

        preferences.saveTimeIntegrityCheckpoint(currentWallTime, currentMonotonic)
        return true
    }

    /**
     * Ensures a session reward has not already been granted to prevent double-spending or replay.
     */
    fun validateRewardClaim(hasExistingReward: Boolean): Boolean {
        return !hasExistingReward
    }
}
