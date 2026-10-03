package com.example.feature.focustimer

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.StudyLockDatabase
import com.example.core.database.entity.SchoolMaterialEntity
import com.example.core.drive.DriveItemResult
import com.example.core.drive.GoogleDriveManager
import com.example.core.repository.StudyLockRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.Activity
import com.example.core.ads.CreatorAdSenseManager
import com.example.core.ads.RewardedInterstitialAdManager

data class PostStudySummaryUiState(
    val durationSeconds: Long = 0L,
    val subject: String = "General Study",
    val earnedSeconds: Long = 0L,
    val noteTitle: String = "",
    val noteContent: String = "",
    val isOcrScanning: Boolean = false,
    val ocrStatusMessage: String = "",
    val lastOcrExtractedText: String? = null,
    val isSaving: Boolean = false,
    val isSavedToCoursework: Boolean = false,
    val isUploadingToDrive: Boolean = false,
    val driveUploadSuccess: Boolean? = null,
    val isGeneratingDeck: Boolean = false,
    val generatedDeckId: Long? = null,
    val showRewardedInterstitialPrompt: Boolean = false,
    val isRewardedAdLoaded: Boolean = false,
    val isAdLoading: Boolean = false,
    val adStatusMessage: String? = null,
    val rewardClaimed: Boolean = false,
    val bonusBankedSeconds: Long = 600L, // 10 min bonus screen time
    val nextSessionMultiplier: Float = 1.5f,
    val userStreak: Int = 1
)

class PostStudySummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StudyLockDatabase.getInstance(application)
    private val driveManager = GoogleDriveManager(application)
    private val repository = StudyLockRepository(application)
    val adManager = RewardedInterstitialAdManager(application)
    val creatorAdSenseManager = CreatorAdSenseManager(application)

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val _uiState = MutableStateFlow(PostStudySummaryUiState())
    val uiState: StateFlow<PostStudySummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            adManager.isAdLoaded.collect { loaded ->
                _uiState.value = _uiState.value.copy(isRewardedAdLoaded = loaded)
            }
        }
        viewModelScope.launch {
            repository.preferences.currentStreak.collect { streak ->
                _uiState.value = _uiState.value.copy(userStreak = streak.coerceAtLeast(1))
            }
        }
    }

    fun initSessionData(durationSeconds: Long, subject: String, earnedSeconds: Long) {
        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
        val defaultTitle = "$subject Study Notes ($dateStr)"
        val shouldPrompt = durationSeconds >= 60L && !_uiState.value.rewardClaimed
        _uiState.value = _uiState.value.copy(
            durationSeconds = durationSeconds,
            subject = subject,
            earnedSeconds = earnedSeconds,
            noteTitle = defaultTitle,
            showRewardedInterstitialPrompt = shouldPrompt
        )
    }

    fun updateTitle(newTitle: String) {
        _uiState.value = _uiState.value.copy(noteTitle = newTitle)
    }

    fun updateContent(newContent: String) {
        _uiState.value = _uiState.value.copy(noteContent = newContent)
    }

    fun appendTag(tag: String) {
        val current = _uiState.value.noteContent
        val updated = if (current.isBlank()) tag else "$current\n$tag: "
        _uiState.value = _uiState.value.copy(noteContent = updated)
    }

    /**
     * Performs on-device ML Kit OCR on a captured camera bitmap.
     */
    fun processOcrBitmap(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            isOcrScanning = true,
            ocrStatusMessage = "Analyzing camera image with ML Kit OCR..."
        )

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    if (text.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isOcrScanning = false,
                            ocrStatusMessage = "No readable text detected in photo."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isOcrScanning = false,
                            ocrStatusMessage = "Text extracted successfully! (${text.split("\\s+".toRegex()).size} words)",
                            lastOcrExtractedText = text
                        )
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("PostStudySummary", "OCR failed", e)
                    _uiState.value = _uiState.value.copy(
                        isOcrScanning = false,
                        ocrStatusMessage = "OCR processing failed: ${e.message}"
                    )
                }
        } catch (e: Exception) {
            Log.e("PostStudySummary", "OCR error", e)
            _uiState.value = _uiState.value.copy(
                isOcrScanning = false,
                ocrStatusMessage = "Error initializing OCR"
            )
        }
    }

    /**
     * Performs on-device ML Kit OCR on a selected image URI from gallery / files.
     */
    fun processOcrUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            isOcrScanning = true,
            ocrStatusMessage = "Scanning selected photo for notes..."
        )

        try {
            val image = InputImage.fromFilePath(getApplication(), uri)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    if (text.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isOcrScanning = false,
                            ocrStatusMessage = "No text detected in selected photo."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isOcrScanning = false,
                            ocrStatusMessage = "Text extracted! (${text.split("\\s+".toRegex()).size} words)",
                            lastOcrExtractedText = text
                        )
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("PostStudySummary", "OCR failed", e)
                    _uiState.value = _uiState.value.copy(
                        isOcrScanning = false,
                        ocrStatusMessage = "Could not read text from photo: ${e.message}"
                    )
                }
        } catch (e: Exception) {
            Log.e("PostStudySummary", "OCR URI error", e)
            _uiState.value = _uiState.value.copy(
                isOcrScanning = false,
                ocrStatusMessage = "Error reading selected photo"
            )
        }
    }

    /**
     * Appends the last extracted OCR text into the main notes log.
     */
    fun appendExtractedOcrText() {
        val ocr = _uiState.value.lastOcrExtractedText ?: return
        val current = _uiState.value.noteContent
        val combined = if (current.isBlank()) ocr else "$current\n\n$ocr"
        _uiState.value = _uiState.value.copy(
            noteContent = combined,
            lastOcrExtractedText = null,
            ocrStatusMessage = "Appended OCR text to notes!"
        )
    }

    /**
     * Saves the logged notes into the Room database under School Materials.
     */
    fun saveNoteToCoursework(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        val content = state.noteContent.trim()
        if (content.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val title = state.noteTitle.ifBlank { "${state.subject} Notes" }
            val fileName = "${title.replace(" ", "_")}.txt"
            val snippet = if (content.length > 120) content.take(120) + "..." else content

            val entity = SchoolMaterialEntity(
                title = title,
                originalFileName = fileName,
                fileType = "STUDY_GUIDE",
                subject = state.subject,
                unitName = "Focus Session Notes",
                driveFolderPath = "Google Drive / StudyLock / School Materials / ${state.subject}",
                driveFileId = null,
                contentSummary = snippet,
                fullTextContent = content,
                isDeckGenerated = false,
                isSyncIncluded = true
            )

            database.schoolMaterialDao().insertMaterial(entity)

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isSavedToCoursework = true
                )
                onSaved()
            }
        }
    }

    /**
     * Uploads the logged study notes to Google Drive into the subject folder.
     */
    fun uploadNoteToGoogleDrive(onSuccess: (DriveItemResult) -> Unit = {}) {
        val state = _uiState.value
        val content = state.noteContent.trim()
        if (content.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isUploadingToDrive = true)
            val title = state.noteTitle.ifBlank { "${state.subject} Notes" }
            val fileName = "${title.replace(" ", "_")}.txt"

            val result = driveManager.uploadTextFile(
                fileName = fileName,
                content = content,
                parentFolderId = null,
                mimeType = "text/plain"
            )

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isUploadingToDrive = false,
                    driveUploadSuccess = result.isSuccess
                )
                if (result.isSuccess) {
                    onSuccess(result.getOrThrow())
                }
            }
        }
    }

    /**
     * Converts the current study notes into an interactive flashcard & quiz deck.
     */
    fun convertNotesToFlashcardDeck(onDeckCreated: (Long) -> Unit) {
        val state = _uiState.value
        val content = state.noteContent.trim()
        if (content.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isGeneratingDeck = true)

            val title = state.noteTitle.ifBlank { "${state.subject} Focus Deck" }
            val deckId = repository.createDeck(
                title = title,
                subject = state.subject,
                description = "Generated from focus session notes (${state.durationSeconds / 60}m studied)",
                sourceText = content
            )

            try {
                repository.generateAndSaveDeckContent(
                    deckId = deckId,
                    sourceText = content,
                    questionCount = 5,
                    flashcardCount = 5
                )
            } catch (e: Exception) {
                Log.e("PostStudySummary", "Error generating deck content", e)
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingDeck = false,
                    generatedDeckId = deckId
                )
                onDeckCreated(deckId)
            }
        }
    }

    fun dismissPrompt() {
        _uiState.value = _uiState.value.copy(showRewardedInterstitialPrompt = false)
    }

    fun openPrompt() {
        if (!_uiState.value.rewardClaimed) {
            _uiState.value = _uiState.value.copy(showRewardedInterstitialPrompt = true)
        }
    }

    fun triggerRewardedInterstitial(activity: Activity?) {
        _uiState.value = _uiState.value.copy(showRewardedInterstitialPrompt = false)
        if (activity == null) {
            _uiState.value = _uiState.value.copy(adStatusMessage = "Activity context is unavailable to display ad.")
            return
        }

        if (!adManager.isAdLoaded.value) {
            // Strictly follow AdMob policy: Never simulate ads or grant rewards when ad is unavailable
            _uiState.value = _uiState.value.copy(
                adStatusMessage = "Sponsor advertisement is currently unavailable. You may continue your studies."
            )
            adManager.loadAd()
            return
        }

        val shown = adManager.showAd(
            activity = activity,
            onUserEarnedReward = { rewardItem ->
                // ONLY called when Google Mobile Ads SDK confirms the user legitimately earned the reward
                claimRewardBonus()
            },
            onAdDismissed = {
                adManager.loadAd()
            }
        )
        if (!shown) {
            _uiState.value = _uiState.value.copy(
                adStatusMessage = "Ad could not be presented. Preloading next advertisement."
            )
        }
    }

    fun claimRewardBonus() {
        if (_uiState.value.rewardClaimed) return
        viewModelScope.launch(Dispatchers.IO) {
            val bonusSecs = _uiState.value.bonusBankedSeconds
            val multiplier = _uiState.value.nextSessionMultiplier

            // 1. Bank bonus screen time immediately (+10m)
            repository.addScreenTimeReward(bonusSecs, "AdMob Rewarded Bonus (+10m Screen Time)")

            // 2. Activate next session 1.5x streak multiplier
            repository.preferences.activateNextSessionBoost(multiplier)

            // 3. Increment developer AdSense earnings & impressions
            creatorAdSenseManager.recordRewardedInterstitialImpression()

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    rewardClaimed = true,
                    showRewardedInterstitialPrompt = false,
                    adStatusMessage = "Reward bonus claimed! +${bonusSecs / 60}m banked & ${multiplier}x next session boost active."
                )
            }
        }
    }

    fun clearAdStatusMessage() {
        _uiState.value = _uiState.value.copy(adStatusMessage = null)
    }
}
