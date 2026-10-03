package com.example.feature.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.entity.StudyDeckEntity
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.core.model.QuizLength
import com.example.core.model.StudyMode
import com.example.core.repository.StudyLockRepository
import com.example.ui.theme.*

@Composable
fun StudyModeSelectScreen(
    initialDeckId: Long?,
    onNavigateBack: () -> Unit,
    onStartSession: (deckId: Long, mode: StudyMode, length: QuizLength) -> Unit,
    onNavigateToFocusTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { StudyLockRepository(context) }
    val allDecks by repository.allDecksFlow.collectAsState(initial = emptyList())

    var selectedMode by remember { mutableStateOf(StudyMode.QUIZ) }
    var selectedLength by remember { mutableStateOf(QuizLength.STANDARD) }
    var selectedDeckId by remember(initialDeckId, allDecks) {
        mutableStateOf(initialDeckId ?: allDecks.firstOrNull()?.id ?: 1L)
    }

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
                    modifier = Modifier.testTag("mode_select_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "STUDY STUDIO CONFIGURATION",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Alternative: Focused Study Timer
            item {
                CyberCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToFocusTimer() }
                        .testTag("mode_select_focus_timer_banner"),
                    borderColor = NeonEmerald.copy(alpha = 0.6f),
                    backgroundColor = SlateElevated
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Prefer a Focused Pomodoro Timer?",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = "Start a 15–60m timed study session with strict distraction blocking.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = NeonEmerald
                        )
                    }
                }
            }

            // Mode Selection
            item {
                Text(
                    text = "1. CHOOSE STUDY MODE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quiz Mode
                    val isQuiz = selectedMode == StudyMode.QUIZ
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, if (isQuiz) ElectricIndigo else CardBorder, RoundedCornerShape(14.dp))
                            .clickable { selectedMode = StudyMode.QUIZ },
                        color = if (isQuiz) SlateElevated else SlateSurface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Quiz, contentDescription = null, tint = if (isQuiz) ElectricIndigo else TextMuted)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Multiple Choice", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Accuracy-based scoring", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                        }
                    }

                    // Flashcard Mode
                    val isFlashcards = selectedMode == StudyMode.FLASHCARDS
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, if (isFlashcards) ElectricIndigo else CardBorder, RoundedCornerShape(14.dp))
                            .clickable { selectedMode = StudyMode.FLASHCARDS },
                        color = if (isFlashcards) SlateElevated else SlateSurface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Style, contentDescription = null, tint = if (isFlashcards) ElectricIndigo else TextMuted)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Flashcards", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("3D flip & self-grading", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                        }
                    }
                }
            }

            // Quiz Length & Reward Tier
            item {
                Text(
                    text = "2. REWARD TIER & LENGTH",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuizLength.values().forEach { length ->
                        val isSelected = selectedLength == length
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, if (isSelected) ElectricIndigo else CardBorder, RoundedCornerShape(14.dp))
                                .clickable { selectedLength = length },
                            color = if (isSelected) SlateElevated else SlateSurface
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = length.label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${length.questionCount} Questions · Performance-weighted",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                                StatusBadge(
                                    text = "Up to ${length.maxRewardMinutes}m",
                                    color = if (isSelected) NeonEmerald else TextMuted,
                                    backgroundColor = if (isSelected) NeonEmeraldGlow else SlateSurface
                                )
                            }
                        }
                    }
                }
            }

            // Select Study Deck
            item {
                Text(
                    text = "3. SELECT STUDY DECK",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (allDecks.isEmpty()) {
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = CardBorder
                    ) {
                        Text(
                            text = "No custom decks found yet.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allDecks.forEach { deck ->
                            val isSelected = selectedDeckId == deck.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (isSelected) ElectricIndigo else CardBorder, RoundedCornerShape(12.dp))
                                    .clickable { selectedDeckId = deck.id },
                                color = if (isSelected) SlateElevated else SlateSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedDeckId = deck.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = ElectricIndigo)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(deck.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text(deck.subject, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Start Session Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                GlowingButton(
                    text = "BEGIN STUDY SESSION",
                    onClick = {
                        val deckIdToUse = if (allDecks.any { it.id == selectedDeckId }) selectedDeckId else allDecks.firstOrNull()?.id ?: 1L
                        onStartSession(deckIdToUse, selectedMode, selectedLength)
                    },
                    icon = Icons.Default.Quiz,
                    testTag = "begin_study_session_button",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
