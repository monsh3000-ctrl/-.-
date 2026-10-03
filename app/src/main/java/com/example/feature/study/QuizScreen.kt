package com.example.feature.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.ui.theme.*

@Composable
fun QuizScreen(
    onNavigateBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    // Active Session Commitment: BackHandler prompts confirmation
    BackHandler {
        viewModel.promptAbandonSession()
    }

    if (state.showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAbandonDialog() },
            title = {
                Text("Leave Study Session?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Your progress will be saved, but this session will not generate a screen-time reward unless completed.\n\nAre you sure you want to abandon?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissAbandonDialog() },
                    modifier = Modifier.testTag("continue_studying_button")
                ) {
                    Text("CONTINUE STUDYING", color = ElectricIndigoLight, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.confirmAbandonSession(onNavigateBack) },
                    modifier = Modifier.testTag("confirm_abandon_button")
                ) {
                    Text("ABANDON SESSION", color = CoralRed)
                }
            },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary
        )
    }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.promptAbandonSession() },
                    modifier = Modifier.testTag("quiz_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Leave Session",
                        tint = TextPrimary
                    )
                }

                Text(
                    text = state.deck?.title ?: "Study Session",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                StatusBadge(
                    text = "${state.currentQuestionIndex + 1}/${state.questions.size}",
                    color = ElectricIndigoLight
                )
            }
        },
        modifier = modifier
    ) { padding ->
        val currentQuestion = state.questions.getOrNull(state.currentQuestionIndex)

        if (currentQuestion == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricIndigo)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress Bar
                item {
                    val progress = (state.currentQuestionIndex + 1).toFloat() / state.questions.size.coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ElectricIndigo,
                        trackColor = SlateElevated
                    )
                }

                // Question Card
                item {
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = CardBorder
                    ) {
                        Text(
                            text = "QUESTION ${state.currentQuestionIndex + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricIndigoLight,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentQuestion.prompt,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                lineHeight = 24.sp
                            )
                        )
                    }
                }

                // Options List
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for ((index, optionText) in currentQuestion.options.withIndex()) {
                            val isSelected = state.selectedOptionIndex == index
                            val isCorrect = index == currentQuestion.correctIndex
                            val isAnswered = state.isAnswerSubmitted

                            val (borderColor, containerColor) = when {
                                !isAnswered && isSelected -> Pair(ElectricIndigo, SlateElevated)
                                isAnswered && isCorrect -> Pair(NeonEmerald, NeonEmeraldGlow)
                                isAnswered && isSelected && !isCorrect -> Pair(CoralRed, CoralRedGlow)
                                else -> Pair(CardBorder, SlateSurface)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isAnswered) {
                                        viewModel.selectOption(index)
                                    }
                                    .testTag("option_${index}_button"),
                                color = containerColor
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val optionLabel = ('A' + index).toString()
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isAnswered && isCorrect) NeonEmerald
                                                else if (isAnswered && isSelected && !isCorrect) CoralRed
                                                else SlateElevated
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isAnswered && isCorrect) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        } else if (isAnswered && isSelected && !isCorrect) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        } else {
                                            Text(
                                                text = optionLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Text(
                                        text = optionText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Explanation & Next Button
                item {
                    AnimatedVisibility(
                        visible = state.isAnswerSubmitted,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CyberCard(
                                modifier = Modifier.fillMaxWidth(),
                                borderColor = if (state.selectedOptionIndex == currentQuestion.correctIndex) NeonEmerald.copy(alpha = 0.5f) else CoralRed.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = if (state.selectedOptionIndex == currentQuestion.correctIndex) "CORRECT!" else "INCORRECT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (state.selectedOptionIndex == currentQuestion.correctIndex) NeonEmerald else CoralRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentQuestion.explanation,
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }

                            val isLast = state.currentQuestionIndex + 1 >= state.questions.size

                            GlowingButton(
                                text = if (state.isFinishing) "PROCESSING REWARD..." else if (isLast) "COMPLETE & VIEW REWARD" else "NEXT QUESTION",
                                onClick = {
                                    viewModel.nextQuestion(onCompleted)
                                },
                                enabled = !state.isFinishing,
                                testTag = "next_question_button",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
