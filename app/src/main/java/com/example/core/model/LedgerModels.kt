package com.example.core.model

enum class TransactionType {
    STUDY_REWARD,
    SCREEN_TIME_USED,
    MANUAL_ADJUSTMENT,
    EXPIRATION,
    REVERSAL
}

data class ScreenTimeBalance(
    val currentBalanceSeconds: Long,
    val todayEarnedSeconds: Long,
    val todayUsedSeconds: Long,
    val totalEarnedAllTimeSeconds: Long
) {
    val remainingMinutes: Long get() = (currentBalanceSeconds / 60).coerceAtLeast(0)
    val remainingSecondsRemainder: Long get() = (currentBalanceSeconds % 60).coerceAtLeast(0)
    val todayEarnedMinutes: Long get() = (todayEarnedSeconds / 60).coerceAtLeast(0)
    val todayUsedMinutes: Long get() = (todayUsedSeconds / 60).coerceAtLeast(0)
}

data class BlockedAppInfo(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val category: String = "Social & Entertainment",
    val usageTimeSecondsToday: Long = 0L
)
