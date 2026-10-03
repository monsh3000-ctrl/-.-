package com.example.core.screentime

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.example.service.accessibility.StudyLockAccessibilityService

interface ScreenTimeProvider {
    val providerType: String
    val isAvailable: Boolean
    val statusMessage: String

    fun enforceRestrictions(remainingSeconds: Long, blockedPackageNames: List<String>)
    fun releaseRestrictions()
    fun getSetupIntent(context: Context): Intent?
}

class AccessibilityScreenTimeProvider(
    private val context: Context
) : ScreenTimeProvider {

    override val providerType: String = "ACCESSIBILITY_SERVICE"

    override val isAvailable: Boolean
        get() = StudyLockAccessibilityService.isRunning

    override val statusMessage: String
        get() = if (isAvailable) {
            "Active: Monitoring foreground distractors & locking apps when balance expires."
        } else {
            "Accessibility Service permission needed to actively block target apps."
        }

    override fun enforceRestrictions(remainingSeconds: Long, blockedPackageNames: List<String>) {
        // Enforced via StudyLockAccessibilityService state
    }

    override fun releaseRestrictions() {
        // Restrictions lifted when balance > 0
    }

    override fun getSetupIntent(context: Context): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}

class UsageStatsScreenTimeProvider(
    private val context: Context
) : ScreenTimeProvider {

    override val providerType: String = "USAGE_STATS"

    override val isAvailable: Boolean
        get() {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

    override val statusMessage: String
        get() = if (isAvailable) {
            "Active: App usage access granted for tracking screen time."
        } else {
            "Usage access permission is required to monitor screen time usage."
        }

    override fun enforceRestrictions(remainingSeconds: Long, blockedPackageNames: List<String>) {}

    override fun releaseRestrictions() {}

    override fun getSetupIntent(context: Context): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }
}

class UnsupportedDeviceScreenTimeProvider : ScreenTimeProvider {

    override val providerType: String = "UNSUPPORTED"
    override val isAvailable: Boolean = false
    override val statusMessage: String =
        "Screen-time integration isn't available on this device configuration. Study decks, timers, and reward tracking remain fully operational."

    override fun enforceRestrictions(remainingSeconds: Long, blockedPackageNames: List<String>) {}
    override fun releaseRestrictions() {}
    override fun getSetupIntent(context: Context): Intent? = null
}

object ScreenTimeProviderFactory {
    fun getProvider(context: Context): ScreenTimeProvider {
        val accessibilityProvider = AccessibilityScreenTimeProvider(context)
        if (accessibilityProvider.isAvailable) return accessibilityProvider

        val usageProvider = UsageStatsScreenTimeProvider(context)
        if (usageProvider.isAvailable) return usageProvider

        return accessibilityProvider
    }
}
