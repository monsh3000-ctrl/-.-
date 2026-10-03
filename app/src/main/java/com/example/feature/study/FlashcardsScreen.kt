package com.example.feature.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.ui.theme.*

@Composable
fun FlashcardsScreen(
    onNavigateBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler {
        viewModel.promptAbandonSession()
    }

    if (state.showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAbandonDialog() },
            title = { Text("Leave Flashcard Session?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You will not earn screen-time rewards unless you complete the session.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAbandonDialog() }) {
                    Text("CONTINUE STUDYING", color = ElectricIndigoLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmAbandonSession(onNavigateBack) }) {
                    Text("ABANDON", color = CoralRed)
                }
            },
            containerColor = SlateSurface
        )
    }

    var cardIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "card_flip"
    )

    val currentCard = state.flashcards.getOrNull(cardIndex)

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
                IconButton(onClick = { viewModel.promptAbandonSession() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "FLASHCARDS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                StatusBadge(
                    text = "${cardIndex + 1}/${state.flashcards.size.coerceAtLeast(1)}",
                    color = ElectricIndigoLight
                )
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Progress Bar
            val progress = (cardIndex + 1).toFloat() / state.flashcards.size.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = ElectricIndigo,
                trackColor = SlateElevated
            )

            if (currentCard == null) {
                Text("No flashcards available in this deck.", color = TextMuted)
            } else {
                // Interactive 3D Flappable Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 12f * density
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(SlateSurface)
                        .border(1.dp, if (isFlipped) NeonEmerald.copy(alpha = 0.5f) else ElectricIndigo.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { isFlipped = !isFlipped }
                        .testTag("flashcard_flip_box"),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotation <= 90f) {
                        // Front Side
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StatusBadge(text = "CONCEPT / QUESTION", color = ElectricIndigoLight)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = currentCard.front,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    lineHeight = 28.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Flip, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tap to flip card", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            }
                        }
                    } else {
                        // Back Side (Rotated 180 so it appears upright)
                        Column(
                            modifier = Modifier
                                .graphicsLayer { rotationY = 180f }
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StatusBadge(text = "DEFINITION / ANSWER", color = NeonEmerald)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = currentCard.back,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    lineHeight = 24.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Self-Grading Buttons (Only visible once flipped)
                if (isFlipped) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Rate your recall:",
                            style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    advanceCard(
                                        cardIndex = cardIndex,
                                        totalCards = state.flashcards.size,
                                        onAdvance = {
                                            cardIndex++
                                            isFlipped = false
                                        },
                                        onFinish = { viewModel.finishFlashcards(onCompleted) }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                            ) {
                                Text("Retry")
                            }

                            OutlinedButton(
                                onClick = {
                                    advanceCard(
                                        cardIndex = cardIndex,
                                        totalCards = state.flashcards.size,
                                        onAdvance = {
                                            cardIndex++
                                            isFlipped = false
                                        },
                                        onFinish = { viewModel.finishFlashcards(onCompleted) }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                            ) {
                                Text("Hard")
                            }

                            Button(
                                onClick = {
                                    advanceCard(
                                        cardIndex = cardIndex,
                                        totalCards = state.flashcards.size,
                                        onAdvance = {
                                            cardIndex++
                                            isFlipped = false
                                        },
                                        onFinish = { viewModel.finishFlashcards(onCompleted) }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
                            ) {
                                Text("Easy")
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Tap the card to reveal the answer and grade your recall.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun advanceCard(
    cardIndex: Int,
    totalCards: Int,
    onAdvance: () -> Unit,
    onFinish: () -> Unit
) {
    if (cardIndex + 1 < totalCards) {
        onAdvance()
    } else {
        onFinish()
    }
}
