package com.example.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.designsystem.CircularProgressMeter
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    onStartStudy: () -> Unit,
    onNavigateToDecks: () -> Unit,
    onNavigateToBank: () -> Unit,
    onNavigateToBlockedApps: () -> Unit,
    onNavigateToFocusTimer: () -> Unit = {},
    onResumeSession: (sessionId: Long, deckId: Long, mode: String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STUDYLOCK",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Earned Screen Time Companion",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                StatusBadge(
                    text = "${state.currentStreak} Day Streak",
                    icon = Icons.Default.LocalFireDepartment,
                    color = NeonEmerald,
                    backgroundColor = NeonEmeraldGlow
                )
            }
        }

        // Next Session Multiplier Boost Banner (Earned from previous deep work session)
        if (state.nextSessionBoostActive) {
            item {
                CyberCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToFocusTimer() },
                    borderColor = AmberWarning,
                    backgroundColor = SlateElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AmberWarning.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔥 ${state.nextSessionBoostMultiplier}x Session Boost Active!",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Start your next study session now to earn 50% extra screen time.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onNavigateToFocusTimer,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Study", fontSize = 12.sp, color = ObsidianBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Session Recovery Alert
        if (state.unfinishedSession != null) {
            item {
                val session = state.unfinishedSession!!
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = AmberWarning,
                    backgroundColor = SlateElevated
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Session Found",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Continue \"${session.deckTitle}\" (${session.mode}) to earn your reward.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { viewModel.abandonUnfinishedSession() },
                            modifier = Modifier.testTag("abandon_session_button")
                        ) {
                            Text("Abandon", color = CoralRed)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onResumeSession(session.id, session.deckId ?: 1L, session.mode)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                            modifier = Modifier.testTag("resume_session_button")
                        ) {
                            Text("Resume")
                        }
                    }
                }
            }
        }

        // Hero Screen-Time Balance Card
        item {
            val balance = state.balance
            val isLocked = balance.currentBalanceSeconds <= 0

            CyberCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBank() },
                borderColor = if (isLocked) CoralRed.copy(alpha = 0.5f) else ElectricIndigo.copy(alpha = 0.5f),
                backgroundColor = SlateSurface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "EARNED SCREEN TIME",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${balance.remainingMinutes}",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (isLocked) CoralRed else NeonEmerald
                                )
                            )
                            Text(
                                text = " min ${balance.remainingSecondsRemainder}s",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                ),
                                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isLocked) CoralRedGlow else NeonEmeraldGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isLocked) CoralRed else NeonEmerald,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Today Earned: +${balance.todayEarnedMinutes}m",
                        style = MaterialTheme.typography.bodySmall.copy(color = NeonEmerald, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Today Used: -${balance.todayUsedMinutes}m",
                        style = MaterialTheme.typography.bodySmall.copy(color = if (balance.todayUsedMinutes > 0) CoralRed else TextSecondary, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        // Daily Progress & Streak Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Today's Study Progress Ring
                CyberCard(
                    modifier = Modifier.weight(1f),
                    borderColor = CardBorder
                ) {
                    Text(
                        text = "DAILY STUDY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val earnedMinutes = state.balance.todayEarnedMinutes.toFloat()
                    val goalMinutes = state.dailyGoalMinutes.coerceAtLeast(1).toFloat()
                    val ratio = (earnedMinutes / goalMinutes).coerceIn(0f, 1f)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressMeter(
                            progress = ratio,
                            modifier = Modifier.size(86.dp),
                            progressColor = ElectricIndigo,
                            trackColor = SlateElevated,
                            strokeWidth = 8.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${earnedMinutes.toInt()}m",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "/${goalMinutes.toInt()}m",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )
                            }
                        }
                    }
                }

                // Streak & Milestone Card
                CyberCard(
                    modifier = Modifier.weight(1f),
                    borderColor = CardBorder
                ) {
                    Text(
                        text = "ACTIVE STREAK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.currentStreak} Days",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val bonusPercent = (state.currentStreak * 3).coerceAtMost(20)
                    Text(
                        text = "+$bonusPercent% Reward Bonus",
                        style = MaterialTheme.typography.bodySmall.copy(color = ElectricIndigoLight)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Next: ${(state.currentStreak / 7 + 1) * 7}d milestone",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }
            }
        }

        // Primary Study CTA
        item {
            GlowingButton(
                text = "START STUDY SESSION",
                onClick = onStartStudy,
                icon = Icons.Default.School,
                color = ElectricIndigo,
                containerColor = ElectricIndigo,
                testTag = "start_study_session_button",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Focused Study Timer Quick Card
        item {
            val focusSession by com.example.core.focustimer.FocusSessionManager.sessionState.collectAsState()
            val isFocusActive = focusSession.state == com.example.core.model.FocusTimerState.RUNNING || focusSession.state == com.example.core.model.FocusTimerState.PAUSED

            CyberCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToFocusTimer() }
                    .testTag("home_focus_timer_card"),
                borderColor = if (isFocusActive) NeonEmerald else ElectricIndigo.copy(alpha = 0.5f),
                backgroundColor = SlateElevated
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isFocusActive) NeonEmeraldGlow else ElectricIndigoGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFocusActive) Icons.Default.HourglassTop else Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isFocusActive) NeonEmerald else ElectricIndigoLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isFocusActive) "ACTIVE FOCUS SESSION" else "FOCUSED STUDY TIMER",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = if (isFocusActive) {
                                    "${focusSession.subject} · ${String.format("%02d:%02d", focusSession.remainingSeconds / 60, focusSession.remainingSeconds % 60)} left"
                                } else {
                                    "Pomodoro timer · Strict app switching blocker"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isFocusActive) NeonEmerald else TextSecondary
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Focus Timer",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Weekly Activity Chart
        item {
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = CardBorder
            ) {
                Text(
                    text = "THIS WEEK'S STUDY EFFORT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                val weekly = state.weeklyMinutes
                val maxMinutes = weekly.maxOfOrNull { it.second }?.coerceAtLeast(30) ?: 30

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    weekly.forEach { (day, minutes) ->
                        val barHeight = ((minutes.toFloat() / maxMinutes) * 80).coerceIn(4f, 80f)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.width(36.dp)
                        ) {
                            if (minutes > 0) {
                                Text(
                                    text = "${minutes}m",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (minutes > 0) ElectricIndigo else SlateElevated)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (minutes > 0) TextPrimary else TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }

        // Quick Navigation Tiles
        item {
            Text(
                text = "QUICK ACCESS",
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
                OutlinedCard(
                    onClick = onNavigateToDecks,
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = SlateSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = ElectricIndigoLight)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Study Decks", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Review & Create", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                    }
                }

                OutlinedCard(
                    onClick = onNavigateToBlockedApps,
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = SlateSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = CoralRed)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Blocked Apps", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Manage Protection", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                    }
                }
            }
        }
    }
}
