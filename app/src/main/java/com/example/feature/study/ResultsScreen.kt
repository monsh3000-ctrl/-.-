package com.example.feature.study

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ads.CreatorAdSenseManager
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.core.model.StudyMode
import com.example.core.repository.StudyLockRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ResultsScreen(
    onReturnHome: () -> Unit,
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { StudyLockRepository(context) }
    val adsManager = remember { CreatorAdSenseManager(context) }

    val state by viewModel.uiState.collectAsState()
    val reward = state.rewardResult
    val totalQuestions = if (state.mode == StudyMode.QUIZ) state.questions.size else state.flashcards.size
    val correctCount = if (state.mode == StudyMode.QUIZ) state.correctCount else totalQuestions
    val accuracy = if (totalQuestions > 0) ((correctCount.toFloat() / totalQuestions) * 100).toInt() else 100

    var showRewardedAdDialog by remember { mutableStateOf(false) }
    var adWatched by remember { mutableStateOf(false) }
    var adCountdown by remember { mutableIntStateOf(5) }
    var canCloseAd by remember { mutableStateOf(false) }

    // Rewarded Video Ad Modal
    if (showRewardedAdDialog) {
        LaunchedEffect(Unit) {
            canCloseAd = false
            adCountdown = 5
            while (adCountdown > 0) {
                delay(1000L)
                adCountdown--
            }
            canCloseAd = true
        }

        Dialog(
            onDismissRequest = {
                if (canCloseAd) showRewardedAdDialog = false
            },
            properties = DialogProperties(dismissOnBackPress = canCloseAd, dismissOnClickOutside = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, NeonEmerald, RoundedCornerShape(20.dp)),
                color = ObsidianBg
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(
                            text = "SPONSORED REWARD VIDEO",
                            color = NeonEmerald,
                            backgroundColor = NeonEmeraldGlow
                        )

                        if (canCloseAd) {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    adsManager.recordRewardedAdImpression()
                                    repository.addScreenTimeReward(300L, "Watched Rewarded Ad (+5m)")
                                    adWatched = true
                                    showRewardedAdDialog = false
                                    Toast.makeText(context, "Bonus +5 Minutes Screen Time Claimed!", Toast.LENGTH_LONG).show()
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Ad", tint = TextPrimary)
                            }
                        } else {
                            Text(
                                text = "Reward in ${adCountdown}s",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Live Sponsored Video Creative
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(SlateSurface, SlateElevated)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "PrepCloud Pro: AP & SAT Masterclasses",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            Text(
                                text = "High-Yield Study Tools & Interactive Practice",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    Text(
                        text = "Complete viewing to unlock an extra 5 minutes of screen time.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = TextAlign.Center)
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                adsManager.recordRewardedAdImpression()
                                repository.addScreenTimeReward(300L, "Watched Creator Rewarded Ad (+5m)")
                                adWatched = true
                                showRewardedAdDialog = false
                                Toast.makeText(context, "Bonus +5 Minutes Screen Time Claimed!", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = canCloseAd,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                        modifier = Modifier.fillMaxWidth().testTag("claim_ad_reward_button")
                    ) {
                        Text(
                            text = if (canCloseAd) "CLAIM +5 MINUTE BONUS & CLOSE" else "Reward in ${adCountdown}s...",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(NeonEmeraldGlow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Session Completed",
                    tint = NeonEmerald,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SESSION COMPLETE",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
            )

            Text(
                text = state.deck?.title ?: "Study Deck",
                style = MaterialTheme.typography.titleSmall.copy(color = ElectricIndigoLight),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Score & Reward Hero Banner
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NeonEmerald.copy(alpha = 0.5f),
                backgroundColor = SlateSurface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SCREEN TIME EARNED",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val earnedSec = reward?.totalEarnedSeconds ?: 0L
                    val earnedMin = (earnedSec / 60).coerceAtLeast(0)

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "+$earnedMin",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = NeonEmerald
                            )
                        )
                        Text(
                            text = " minutes",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            ),
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                    }

                    Text(
                        text = "Unlocked & Deposited into Screen Time Bank",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Accuracy", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text(
                                text = "$accuracy%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (accuracy >= 70) NeonEmerald else AmberWarning
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Score", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text(
                                text = "$correctCount / $totalQuestions",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Duration", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            val min = state.durationSeconds / 60
                            val sec = state.durationSeconds % 60
                            Text(
                                text = "${min}m ${sec}s",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rewarded Ad Section
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (adWatched) SlateBorder else NeonEmerald.copy(alpha = 0.8f),
                backgroundColor = SlateElevated
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SUPPORT CREATOR & BOOST REWARD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = NeonEmerald,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                        }

                        StatusBadge(
                            text = if (adWatched) "+5M CLAIMED" else "+5M BONUS",
                            color = NeonEmerald,
                            backgroundColor = NeonEmeraldGlow
                        )
                    }

                    Text(
                        text = if (adWatched) {
                            "Reward claimed! You supported the creator's AdSense and banked an extra 5 minutes."
                        } else {
                            "Watch a brief 5-second sponsored ad to earn an extra +5 minutes of unlocked screen time and support the developer."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Button(
                        onClick = { showRewardedAdDialog = true },
                        enabled = !adWatched,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            disabledContainerColor = SlateBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("watch_rewarded_ad_button")
                    ) {
                        Icon(
                            imageVector = if (adWatched) Icons.Default.Check else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (adWatched) TextMuted else Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (adWatched) "Bonus +5 Minutes Unlocked" else "Watch Ad (+5 min Bonus)",
                            color = if (adWatched) TextMuted else Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Deterministic Reward Calculation Explanation
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CardBorder
            ) {
                Text(
                    text = "REWARD CALCULATION BREAKDOWN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ElectricIndigoLight,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = reward?.explanation ?: "Rewards are deterministically calculated based on question count, accuracy, and current study streak.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            GlowingButton(
                text = "VIEW REWARD & RETURN HOME",
                onClick = onReturnHome,
                icon = Icons.Default.Home,
                testTag = "return_home_results_button",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
