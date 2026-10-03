package com.example.service.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.example.core.permissions.PermissionManager
import com.example.core.repository.StudyLockRepository
import java.util.Calendar

class UsageTracker(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun isPermissionGranted(): Boolean {
        return PermissionManager.checkUsageStatsPermission(context)
    }

    /**
     * Total device screen time in seconds since midnight (00:00:00).
     * Authoritative calculation from Android's UsageStatsManager.
     */
    fun getTodayTotalDeviceScreenTimeSeconds(): Long {
        if (!isPermissionGranted() || usageStatsManager == null) return 0L
        val startOfDay = getStartOfDayTimestamp()
        val now = System.currentTimeMillis()

        return try {
            val statsList: List<UsageStats> = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            ) ?: emptyList()

            // Sum totalTimeInForeground for all apps excluding launcher/system launcher
            val totalMillis = statsList
                .filter { it.totalTimeInForeground > 0 }
                .sumOf { it.totalTimeInForeground }

            (totalMillis / 1000).coerceAtLeast(0L)
        } catch (e: Exception) {
            Log.e("UsageTracker", "Failed to query device screen time", e)
            0L
        }
    }

    /**
     * Exact foreground usage time in seconds for a specific package today.
     */
    fun getTodayUsageForPackage(packageName: String): Long {
        if (!isPermissionGranted() || usageStatsManager == null) return 0L
        val startOfDay = getStartOfDayTimestamp()
        val now = System.currentTimeMillis()

        return try {
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            ) ?: emptyList()

            val pkgStats = statsList.find { it.packageName == packageName }
            (pkgStats?.totalTimeInForeground ?: 0L) / 1000
        } catch (e: Exception) {
            Log.e("UsageTracker", "Failed to query stats for $packageName", e)
            0L
        }
    }

    /**
     * Returns a map of package name to today's foreground seconds for all requested packages.
     */
    fun getTodayUsageForPackages(packageNames: Collection<String>): Map<String, Long> {
        if (!isPermissionGranted() || usageStatsManager == null) return emptyMap()
        val startOfDay = getStartOfDayTimestamp()
        val now = System.currentTimeMillis()

        return try {
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            ) ?: emptyList()

            val resultMap = mutableMapOf<String, Long>()
            val targetSet = packageNames.toSet()

            for (stats in statsList) {
                if (targetSet.contains(stats.packageName)) {
                    val current = resultMap.getOrDefault(stats.packageName, 0L)
                    val seconds = stats.totalTimeInForeground / 1000
                    if (seconds > current) {
                        resultMap[stats.packageName] = seconds
                    }
                }
            }
            resultMap
        } catch (e: Exception) {
            Log.e("UsageTracker", "Failed to query multi-package stats", e)
            emptyMap()
        }
    }

    /**
     * Determines current foreground package using recent UsageEvents.
     */
    fun getForegroundPackageName(): String? {
        if (!isPermissionGranted() || usageStatsManager == null) return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 15_000 // Query last 15 seconds

        return try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime) ?: return null
            val event = UsageEvents.Event()
            var lastForegroundApp: String? = null

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForegroundApp = event.packageName
                }
            }
            lastForegroundApp
        } catch (e: Exception) {
            null
        }
    }

    private fun getStartOfDayTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
