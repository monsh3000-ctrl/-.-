package com.example.feature.focustimer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.StudyLockDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PostStudySummaryViewModelTest {

    private lateinit var application: Application
    private lateinit var viewModel: PostStudySummaryViewModel
    private lateinit var database: StudyLockDatabase

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = StudyLockDatabase.getInstance(application)
        viewModel = PostStudySummaryViewModel(application)
    }

    @Test
    fun testInitSessionDataAndNotesManipulation() {
        viewModel.initSessionData(
            durationSeconds = 1500L, // 25 minutes
            subject = "Biology",
            earnedSeconds = 750L // 12.5 minutes
        )

        val state = viewModel.uiState.value
        assertEquals(1500L, state.durationSeconds)
        assertEquals("Biology", state.subject)
        assertEquals(750L, state.earnedSeconds)
        assertTrue(state.noteTitle.contains("Biology"))

        // Update note title and content
        viewModel.updateTitle("Cell Membrane & Transport")
        viewModel.updateContent("Osmosis is diffusion of water across a semipermeable membrane.")
        viewModel.appendTag("#KeyConcept")

        val updatedState = viewModel.uiState.value
        assertEquals("Cell Membrane & Transport", updatedState.noteTitle)
        assertTrue(updatedState.noteContent.contains("Osmosis"))
        assertTrue(updatedState.noteContent.contains("#KeyConcept"))
    }

    @Test
    fun testSaveNoteToCoursework() = runBlocking {
        viewModel.initSessionData(
            durationSeconds = 3600L,
            subject = "Chemistry",
            earnedSeconds = 1800L
        )
        viewModel.updateTitle("Stoichiometry Laws")
        viewModel.updateContent("Mole ratios govern balanced chemical equations and product yield calculations.")

        var savedCallbackTriggered = false
        viewModel.saveNoteToCoursework {
            savedCallbackTriggered = true
        }

        // Wait brief moment for coroutine
        Thread.sleep(200)

        val materials = database.schoolMaterialDao().getAllMaterialsList()
        val found = materials.find { it.title == "Stoichiometry Laws" }
        assertNotNull(found)
        assertEquals("Chemistry", found?.subject)
        assertTrue(found?.fullTextContent?.contains("Mole ratios") == true)
    }

    @Test
    fun testConvertNotesToFlashcardDeck() = runBlocking {
        viewModel.initSessionData(
            durationSeconds = 1800L,
            subject = "History",
            earnedSeconds = 900L
        )
        viewModel.updateTitle("American Revolution Notes")
        viewModel.updateContent("The Declaration of Independence was adopted on July 4, 1776 by the Continental Congress.")

        var createdDeckId: Long? = null
        viewModel.convertNotesToFlashcardDeck { id ->
            createdDeckId = id
        }

        // Wait for coroutine and idle main looper
        for (i in 1..20) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (createdDeckId != null) break
            Thread.sleep(50)
        }

        val allDecks = database.studyDeckDao().getAllDecks().first()
        val foundDeck = allDecks.find { it.title == "American Revolution Notes" }
        assertNotNull("Deck should be created in database", foundDeck)
        assertEquals("American Revolution Notes", foundDeck?.title)
    }

    @Test
    fun testRewardedInterstitialPromptTriggerAndRewardClaiming() = runBlocking {
        // Deep-work session of 25 minutes (1500 seconds)
        viewModel.initSessionData(
            durationSeconds = 1500L,
            subject = "Physics",
            earnedSeconds = 750L
        )

        // Verifies the prompt is automatically triggered after completing deep work
        assertTrue(viewModel.uiState.value.showRewardedInterstitialPrompt)
        assertFalse(viewModel.uiState.value.rewardClaimed)

        // User claims reward from rewarded interstitial
        viewModel.claimRewardBonus()

        for (i in 1..20) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (viewModel.uiState.value.rewardClaimed) break
            Thread.sleep(50)
        }

        assertTrue(viewModel.uiState.value.rewardClaimed)
        assertFalse(viewModel.uiState.value.showRewardedInterstitialPrompt)

        val prefs = com.example.core.datastore.StudyLockPreferences(application)
        assertTrue(prefs.nextSessionBoostActive.first())
        assertEquals(1.5f, prefs.nextSessionBoostMultiplier.first(), 0.01f)

        // Consume boost on next study session
        val consumedMultiplier = prefs.consumeNextSessionBoost()
        assertEquals(1.5f, consumedMultiplier, 0.01f)
        assertFalse(prefs.nextSessionBoostActive.first())
    }
}
