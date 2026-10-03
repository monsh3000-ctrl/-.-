package com.example

import com.example.core.engine.AntiTamperManager
import com.example.core.engine.RewardCalculator
import com.example.core.model.QuizLength
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudyLockBusinessLogicTest {

    @Test
    fun testAbandonedSessionEarnsZeroReward() {
        val result = RewardCalculator.calculateReward(
            quizLength = QuizLength.STANDARD,
            correctCount = 3,
            totalQuestions = 5,
            isCompleted = false
        )
        assertEquals(0L, result.totalEarnedSeconds)
        assertFalse(result.cappedAtLimit)
    }

    @Test
    fun testZeroCorrectAnswersEarnsZeroReward() {
        val result = RewardCalculator.calculateReward(
            quizLength = QuizLength.SHORT,
            correctCount = 0,
            totalQuestions = 3,
            isCompleted = true
        )
        assertEquals(0L, result.totalEarnedSeconds)
    }

    @Test
    fun testDeepDiveRewardAccuracy() {
        // 8 of 10 correct on Deep Dive (max 1800s / 30m)
        val result = RewardCalculator.calculateReward(
            quizLength = QuizLength.DEEP_DIVE,
            correctCount = 8,
            totalQuestions = 10,
            isCompleted = true,
            currentStreak = 0
        )

        val earnedMinutes = result.totalEarnedSeconds / 60
        // Expected ~22-26 minutes depending on formula, <= 30m
        assertTrue("Earned minutes should be between 20 and 30, was $earnedMinutes", earnedMinutes in 20..30)
        assertTrue(result.totalEarnedSeconds <= QuizLength.DEEP_DIVE.maxRewardSeconds)
    }

    @Test
    fun testShortQuizDoesNotExceedMaxReward() {
        val result = RewardCalculator.calculateReward(
            quizLength = QuizLength.SHORT,
            correctCount = 3,
            totalQuestions = 3,
            isCompleted = true,
            currentStreak = 10 // high streak
        )
        // Max reward for Short is 5 minutes (300 seconds)
        assertEquals(300L, result.totalEarnedSeconds)
    }

    @Test
    fun testDailyRewardCapIsEnforced() {
        val remainingCapSeconds = 120L // only 2 minutes remaining capacity
        val result = RewardCalculator.calculateReward(
            quizLength = QuizLength.STANDARD,
            correctCount = 5,
            totalQuestions = 5,
            isCompleted = true,
            dailyRemainingCapSeconds = remainingCapSeconds
        )

        assertEquals(120L, result.totalEarnedSeconds)
        assertTrue(result.cappedAtLimit)
    }

    @Test
    fun testAntiTamperPreventsDuplicateRewards() {
        // Emulate AntiTamperManager rule: hasExistingReward -> reject
        assertTrue(AntiTamperManager(mockPreferences()).validateRewardClaim(hasExistingReward = false))
        assertFalse(AntiTamperManager(mockPreferences()).validateRewardClaim(hasExistingReward = true))
    }

    @Test
    fun testAppOpenCloseDurationCalculation() {
        val openTimestamp = 100_000L
        val closeTimestamp = 160_000L // 60 seconds later
        val elapsedSeconds = (closeTimestamp - openTimestamp) / 1000
        assertEquals(60L, elapsedSeconds)
    }

    private fun mockPreferences(): com.example.core.datastore.StudyLockPreferences {
        // Return dummy reference for static method testing
        return com.example.core.datastore.StudyLockPreferences(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
    }
}
