package com.example.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.repository.StudyLockRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OcrReviewScreen(
    extractedText: String,
    onNavigateBack: () -> Unit,
    onProceedToStudy: (deckId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { StudyLockRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf("Scanned Notes") }
    var subject by remember { mutableStateOf("General Science") }
    var textContent by remember { mutableStateOf(extractedText) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("review_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "REVIEW EXTRACTED TEXT",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Review and refine your study material before generating quiz questions or flashcards.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            // Deck Name & Subject Fields
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Deck Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deck_title_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricIndigo,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject / Course") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deck_subject_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricIndigo,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Extracted Material Text Area
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CardBorder
            ) {
                Text(
                    text = "MATERIAL CONTENT (${textContent.length} chars)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ElectricIndigoLight,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .testTag("material_content_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricIndigo,
                        unfocusedBorderColor = SlateElevated,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isSaving) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ElectricIndigo
                )
            }

            GlowingButton(
                text = if (isSaving) "GENERATING DECK..." else "SAVE DECK & PROCEED",
                onClick = {
                    if (textContent.isNotBlank() && !isSaving) {
                        isSaving = true
                        coroutineScope.launch {
                            val deckId = repository.createDeck(
                                title = title.ifBlank { "Study Deck" },
                                subject = subject.ifBlank { "General" },
                                description = "Created via Scanner OCR",
                                sourceText = textContent
                            )
                            // Generate questions & flashcards
                            repository.generateAndSaveDeckContent(
                                deckId = deckId,
                                sourceText = textContent,
                                questionCount = 5,
                                flashcardCount = 5
                            )
                            isSaving = false
                            onProceedToStudy(deckId)
                        }
                    }
                },
                enabled = textContent.isNotBlank() && !isSaving,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                testTag = "save_deck_proceed_button",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
