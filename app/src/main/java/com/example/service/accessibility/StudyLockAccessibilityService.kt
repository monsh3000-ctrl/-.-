package com.example.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.core.repository.StudyLockRepository
import com.example.feature.screentime.StudyLockBlockerActivity
import kotlinx.coroutines.*

class StudyLockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var repository: StudyLockRepository? = null

    private var activeProtectedPackage: String? = null
    private var appOpenTimestamp: Long = 0L
    private var usageTickerJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // When screen turns off, the user has closed/locked the active session
                val currentPkg = activeProtectedPackage
                if (currentPkg != null) {
                    onAppClosed(currentPkg)
                    activeProtectedPackage = null
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        repository = StudyLockRepository(applicationContext)
        Log.i("StudyLockAccess", "StudyLock Accessibility Service Connected with Open/Close App Tracking")

        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            Log.w("StudyLockAccess", "Could not register screen receiver", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return

        // 1. Ignore transient overlays (soft keyboard, volume slider, system UI)
        // User is still inside the open app
        if (isTransientSystemPackage(packageName)) {
            return
        }

        // 2. Active Focused Study Session: Strictly prevent switching to other apps
        if (com.example.core.focustimer.FocusSessionManager.isFocusSessionRunning() &&
            com.example.core.focustimer.FocusSessionManager.isAppSwitchingPrevented
        ) {
            if (packageName != applicationContext.packageName && !isLauncherPackage(packageName)) {
                val currentPkg = activeProtectedPackage
                if (currentPkg != null) {
                    onAppClosed(currentPkg)
                    activeProtectedPackage = null
                }
                launchBlocker(
                    blockedPkg = packageName,
                    isFocusLocked = true,
                    focusSubject = com.example.core.focustimer.FocusSessionManager.currentActiveSubject
                )
                return
            }
        }

        // 3. User closed app and went to StudyLock
        if (packageName == applicationContext.packageName) {
            val currentPkg = activeProtectedPackage
            if (currentPkg != null) {
                onAppClosed(currentPkg)
                activeProtectedPackage = null
            }
            return
        }

        // 3. User closed app and returned to Home Launcher
        if (isLauncherPackage(packageName)) {
            val currentPkg = activeProtectedPackage
            if (currentPkg != null) {
                onAppClosed(currentPkg)
                activeProtectedPackage = null
            }
            return
        }

        // 4. Third-party application
        serviceScope.launch {
            val repo = repository ?: StudyLockRepository(applicationContext).also { repository = it }
            val isProtected = repo.isAppProtected(packageName)

            if (isProtected) {
                val balance = repo.database.screenTimeDao().getCurrentBalance()
                if (balance <= 0) {
                    // Balance exhausted!
                    val currentPkg = activeProtectedPackage
                    if (currentPkg != null) {
                        onAppClosed(currentPkg)
                        activeProtectedPackage = null
                    }
                    launchBlocker(packageName)
                } else {
                    if (activeProtectedPackage != packageName) {
                        // User switched from another app
                        val currentPkg = activeProtectedPackage
                        if (currentPkg != null) {
                            onAppClosed(currentPkg)
                        }
                        // Open new protected app session
                        activeProtectedPackage = packageName
                        onAppOpened(packageName, balance)
                    }
                }
            } else {
                // Switched to an unprotected app: close previous protected app
                val currentPkg = activeProtectedPackage
                if (currentPkg != null) {
                    onAppClosed(currentPkg)
                    activeProtectedPackage = null
                }
            }
        }
    }

    /**
     * Called when a protected app is opened.
     * Records exact monotonic open timestamp and monitors remaining balance second-by-second.
     */
    private fun onAppOpened(packageName: String, initialBalance: Long) {
        usageTickerJob?.cancel()
        appOpenTimestamp = SystemClock.elapsedRealtime()

        usageTickerJob = serviceScope.launch {
            var remaining = initialBalance
            while (isActive) {
                delay(1000)
                remaining -= 1L

                // If balance runs out while user is actively in the app
                if (remaining <= 0) {
                    val now = SystemClock.elapsedRealtime()
                    val duration = ((now - appOpenTimestamp) / 1000).coerceAtLeast(1L)
                    appOpenTimestamp = now

                    val repo = repository ?: StudyLockRepository(applicationContext).also { repository = it }
                    val appLabel = getAppLabel(packageName)
                    repo.deductScreenTime(duration, "$appLabel used (${duration / 60}m ${duration % 60}s)")

                    launchBlocker(packageName)
                    activeProtectedPackage = null
                    break
                }
            }
        }
    }

    /**
     * Called when a protected app is closed (switched away, went to launcher, or screen turned off).
     * Calculates the exact elapsed time and deducts it from the screen-time counter.
     */
    private fun onAppClosed(packageName: String) {
        usageTickerJob?.cancel()
        usageTickerJob = null

        val now = SystemClock.elapsedRealtime()
        val duration = if (appOpenTimestamp > 0) {
            ((now - appOpenTimestamp) / 1000).coerceAtLeast(0L)
        } else {
            0L
        }
        appOpenTimestamp = 0L

        if (duration > 0) {
            serviceScope.launch {
                val repo = repository ?: StudyLockRepository(applicationContext).also { repository = it }
                val appLabel = getAppLabel(packageName)
                repo.deductScreenTime(
                    duration,
                    "$appLabel closed (${duration / 60}m ${duration % 60}s used)"
                )
                Log.i("StudyLockAccess", "App $packageName closed after $duration seconds")
            }
        }
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun launchBlocker(
        blockedPkg: String,
        isFocusLocked: Boolean = false,
        focusSubject: String = "Active Study"
    ) {
        val intent = Intent(applicationContext, StudyLockBlockerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(StudyLockBlockerActivity.EXTRA_BLOCKED_PACKAGE, blockedPkg)
            putExtra(StudyLockBlockerActivity.EXTRA_IS_FOCUS_LOCKED, isFocusLocked)
            putExtra(StudyLockBlockerActivity.EXTRA_FOCUS_SUBJECT, focusSubject)
        }
        startActivity(intent)
    }

    private fun isTransientSystemPackage(pkg: String): Boolean {
        return pkg == "com.android.systemui" ||
                pkg == "android" ||
                pkg.contains("inputmethod", ignoreCase = true) ||
                pkg.contains("keyboard", ignoreCase = true) ||
                pkg == "com.google.android.inputmethod.latin" ||
                pkg == "com.samsung.android.honeyboard" ||
                pkg.contains("autofill", ignoreCase = true)
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(homeIntent, 0)
            if (resolveInfo?.activityInfo?.packageName == pkg) return true
        } catch (e: Exception) {
            // ignore
        }
        return pkg.contains("launcher", ignoreCase = true) ||
                pkg.contains("nexuslauncher", ignoreCase = true) ||
                pkg == "com.google.android.apps.nexuslauncher" ||
                pkg == "com.android.launcher3" ||
                pkg == "com.sec.android.app.launcher"
    }

    override fun onInterrupt() {
        val currentPkg = activeProtectedPackage
        if (currentPkg != null) {
            onAppClosed(currentPkg)
            activeProtectedPackage = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // ignore
        }
        val currentPkg = activeProtectedPackage
        if (currentPkg != null) {
            onAppClosed(currentPkg)
            activeProtectedPackage = null
        }
        serviceScope.cancel()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
    }
}
