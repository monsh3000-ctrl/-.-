package com.example.core.engine

import com.example.core.model.QuizLength
import com.example.core.model.RewardCalculationResult
import kotlin.math.roundToLong

object RewardCalculator {

    /**
     * Calculates deterministic screen time reward based on quiz length,
     * correct answer ratio, completion, and current study streak.
     *
     * Rules:
     * - Short (3 Qs): max 5 min (300s)
     * - Standard (5 Qs): max 15 min (900s)
     * - Deep Dive (10 Qs): max 30 min (1800s)
     * Performance: 8/10 on Deep Dive yields ~22 minutes.
     */
    fun calculateReward(
        quizLength: QuizLength,
        correctCount: Int,
        totalQuestions: Int,
        isCompleted: Boolean,
        currentStreak: Int = 0,
        dailyRemainingCapSeconds: Long = Long.MAX_VALUE
    ): RewardCalculationResult {
        if (!isCompleted || totalQuestions <= 0 || correctCount <= 0) {
            return RewardCalculationResult(
                baseRewardSeconds = 0L,
                accuracyBonusSeconds = 0L,
                streakMultiplier = 1.0f,
                totalEarnedSeconds = 0L,
                cappedAtLimit = false,
                explanation = if (!isCompleted) "Session was abandoned before completion; no reward earned."
                else "Zero correct answers; study the material and retry to earn time."
            )
        }

        val accuracy = (correctCount.toFloat() / totalQuestions).coerceIn(0f, 1f)
        val maxSeconds = quizLength.maxRewardSeconds

        // Base reward for completing the session (30% of max)
        val completionComponent = (maxSeconds * 0.30f).roundToLong()

        // Performance bonus proportional to accuracy (up to 70% of max)
        val accuracyComponent = (maxSeconds * 0.70f * accuracy).roundToLong()

        val rawEarned = (completionComponent + accuracyComponent).coerceAtMost(maxSeconds)

        // Streak bonus: +3% per consecutive day, capped at +20% (1.20x)
        val streakBonusRatio = (currentStreak * 0.03f).coerceIn(0f, 0.20f)
        val streakMultiplier = 1.0f + streakBonusRatio

        val streakEnhancedSeconds = (rawEarned * streakMultiplier).roundToLong().coerceAtMost(maxSeconds)

        val finalEarnedSeconds = streakEnhancedSeconds.coerceAtMost(dailyRemainingCapSeconds)
        val wasCapped = finalEarnedSeconds < streakEnhancedSeconds

        val accuracyPercent = (accuracy * 100).toInt()
        val earnedMinutes = finalEarnedSeconds / 60

        val explanation = buildString {
            append("$correctCount/$totalQuestions correct ($accuracyPercent% accuracy). ")
            append("Base: ${rawEarned / 60}m. ")
            if (currentStreak > 0) {
                append("Streak bonus (${currentStreak}d): +${((streakMultiplier - 1f) * 100).toInt()}%. ")
            }
            append("Total: +$earnedMinutes minutes.")
            if (wasCapped) {
                append(" (Capped by your daily maximum limit).")
            }
        }

        return RewardCalculationResult(
            baseRewardSeconds = rawEarned,
            accuracyBonusSeconds = (streakEnhancedSeconds - rawEarned).coerceAtLeast(0L),
            streakMultiplier = streakMultiplier,
            totalEarnedSeconds = finalEarnedSeconds,
            cappedAtLimit = wasCapped,
            explanation = explanation
        )
    }
}
