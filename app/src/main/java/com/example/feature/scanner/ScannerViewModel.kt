package com.example.feature.scanner

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class DetectedBlockItem(
    val id: Int,
    val text: String,
    val box: Rect?,
    val isSelected: Boolean = true
)

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data object Analyzing : ScannerUiState
    data class Success(val blocks: List<DetectedBlockItem>) : ScannerUiState
    data class Error(val message: String) : ScannerUiState
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudyLockRepository(application)
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val _scannerState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

    private val _flashEnabled = MutableStateFlow(false)
    val flashEnabled: StateFlow<Boolean> = _flashEnabled.asStateFlow()

    private val _extractedText = MutableStateFlow("")
    val extractedText: StateFlow<String> = _extractedText.asStateFlow()

    fun toggleFlash() {
        _flashEnabled.value = !_flashEnabled.value
    }

    fun analyzeBitmap(bitmap: Bitmap) {
        _scannerState.value = ScannerUiState.Analyzing
        val image = InputImage.fromBitmap(bitmap, 0)

        try {
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (visionText.textBlocks.isEmpty()) {
                        _scannerState.value = ScannerUiState.Error("No text detected. Ensure proper lighting and focus.")
                        return@addOnSuccessListener
                    }

                    val blocks = visionText.textBlocks.mapIndexed { index, block ->
                        DetectedBlockItem(
                            id = index,
                            text = block.text.trim(),
                            box = block.boundingBox,
                            isSelected = true
                        )
                    }

                    _scannerState.value = ScannerUiState.Success(blocks)
                    updateNormalizedText(blocks)
                }
                .addOnFailureListener { e ->
                    val message = if (e.message?.contains("optional module") == true || e.message?.contains("download") == true) {
                        "Text recognition model is finishing background download. Please wait a few seconds and try again."
                    } else {
                        e.localizedMessage ?: "OCR text recognition failed."
                    }
                    _scannerState.value = ScannerUiState.Error(message)
                }
        } catch (e: Exception) {
            val message = if (e.message?.contains("optional module") == true || e.message?.contains("download") == true) {
                "Text recognition model is finishing background download. Please wait a few seconds and try again."
            } else {
                e.localizedMessage ?: "Scanner encountered an error."
            }
            _scannerState.value = ScannerUiState.Error(message)
        }
    }

    fun analyzeUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _scannerState.value = ScannerUiState.Analyzing
            try {
                @Suppress("DEPRECATION")
                val bitmap = MediaStore.Images.Media.getBitmap(getApplication<Application>().contentResolver, uri)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        analyzeBitmap(bitmap)
                    }
                } else {
                    _scannerState.value = ScannerUiState.Error("Failed to decode image from storage.")
                }
            } catch (e: Exception) {
                _scannerState.value = ScannerUiState.Error(e.localizedMessage ?: "Failed to read image.")
            }
        }
    }

    fun toggleBlockSelection(id: Int) {
        val current = _scannerState.value
        if (current is ScannerUiState.Success) {
            val updated = current.blocks.map {
                if (it.id == id) it.copy(isSelected = !it.isSelected) else it
            }
            _scannerState.value = ScannerUiState.Success(updated)
            updateNormalizedText(updated)
        }
    }

    fun selectAllBlocks(select: Boolean) {
        val current = _scannerState.value
        if (current is ScannerUiState.Success) {
            val updated = current.blocks.map { it.copy(isSelected = select) }
            _scannerState.value = ScannerUiState.Success(updated)
            updateNormalizedText(updated)
        }
    }

    private fun updateNormalizedText(blocks: List<DetectedBlockItem>) {
        val selectedText = blocks.filter { it.isSelected }
            .joinToString("\n\n") { it.text }
            .replace(Regex("[ \\t]+"), " ")
            .trim()
        _extractedText.value = selectedText
    }

    fun setExtractedTextDirectly(text: String) {
        _extractedText.value = text
    }

    fun resetScanner() {
        _scannerState.value = ScannerUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        textRecognizer.close()
    }
}
