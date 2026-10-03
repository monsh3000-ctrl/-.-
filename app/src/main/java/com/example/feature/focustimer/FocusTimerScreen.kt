package com.example.feature.focustimer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.core.model.FocusTimerState
import com.example.ui.theme.*

@Composable
fun FocusTimerScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToBank: () -> Unit = {},
    onNavigateToSummary: (durationSeconds: Long, subject: String, earnedSeconds: Long) -> Unit = { _, _, _ -> },
    viewModel: FocusTimerViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val availableSubjects by viewModel.availableSubjects.collectAsState(initial = emptyList())
    val lastBankedSeconds by viewModel.lastBankedRewardSeconds.collectAsState()
    val nextSessionBoostActive by viewModel.nextSessionBoostActive.collectAsState(initial = false)
    val nextSessionBoostMultiplier by viewModel.nextSessionBoostMultiplier.collectAsState(initial = 1.5f)

    var selectedDurationMinutes by remember { mutableIntStateOf(25) }
    var selectedSubject by remember { mutableStateOf("Biology") }
    var preventAppSwitching by remember { mutableStateOf(true) }
    var customSubjectInput by remember { mutableStateOf("") }
    var showCustomSubjectDialog by remember { mutableStateOf(false) }

    // Pre-populate subject options
    val subjectsList = remember(availableSubjects) {
        val list = mutableListOf("Biology", "Chemistry", "History", "Economics", "Mathematics", "English Literature")
        availableSubjects.forEach { if (!list.contains(it)) list.add(0, it) }
        list
    }

    // Custom subject dialog
    if (showCustomSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSubjectDialog = false },
            title = { Text("Custom Focus Subject") },
            text = {
                OutlinedTextField(
                    value = customSubjectInput,
                    onValueChange = { customSubjectInput = it },
                    label = { Text("Topic / Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customSubjectInput.isNotBlank()) {
                            selectedSubject = customSubjectInput.trim()
                        }
                        showCustomSubjectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("Set Subject")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSubjectDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SlateSurface
        )
    }

    val isRunning = sessionState.state == FocusTimerState.RUNNING
    val isPaused = sessionState.state == FocusTimerState.PAUSED
    val isStopped = sessionState.state == FocusTimerState.STOPPED
    val isCompleted = sessionState.state == FocusTimerState.COMPLETED

    val currentTotal = if (isStopped) (selectedDurationMinutes * 60L) else sessionState.totalSeconds
    val currentRemaining = if (isStopped) (selectedDurationMinutes * 60L) else sessionState.remainingSeconds
    val progress = if (currentTotal > 0) {
        (currentRemaining.toFloat() / currentTotal.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val minutesPart = currentRemaining / 60
    val secondsPart = currentRemaining % 60
    val timeFormatted = String.format("%02d:%02d", minutesPart, secondsPart)

    val timerColor by animateColorAsState(
        targetValue = when (sessionState.state) {
            FocusTimerState.RUNNING -> ElectricIndigo
            FocusTimerState.PAUSED -> AmberWarning
            FocusTimerState.COMPLETED -> NeonEmerald
            FocusTimerState.STOPPED -> ElectricIndigoLight
        },
        animationSpec = tween(400)
    )

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("focus_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "FOCUSED STUDY TIMER",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                }

                StatusBadge(
                    text = if (isRunning) "Shield Engaged" else if (isPaused) "Paused" else "Distraction Blocker",
                    icon = if (isRunning) Icons.Default.Security else Icons.Default.Lock,
                    color = if (isRunning) NeonEmerald else ElectricIndigoLight,
                    backgroundColor = if (isRunning) NeonEmeraldGlow else ElectricIndigoGlow
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Active Rewarded Next-Session Boost Banner
            if (nextSessionBoostActive) {
                item {
                    Surface(
                        color = AmberWarning.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AmberWarning),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AmberWarning),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = ObsidianBg,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🔥 ${nextSessionBoostMultiplier}x Reward Multiplier Active!",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "Earn 50% bonus screen time on this session thanks to your previous deep-work reward.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                }
            }

            // Hero Circular Timer Card
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = timerColor.copy(alpha = 0.5f),
                    backgroundColor = SlateSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isStopped) "TARGET: $selectedSubject" else "STUDYING: ${sessionState.subject}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Large Circular Countdown Display
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(230.dp)
                        ) {
                            CircularProgressMeter(
                                progress = progress,
                                modifier = Modifier.size(220.dp),
                                progressColor = timerColor,
                                trackColor = SlateElevated,
                                strokeWidth = 12.dp
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = timeFormatted,
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 2.sp,
                                            color = TextPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = when (sessionState.state) {
                                            FocusTimerState.RUNNING -> "ACTIVE FOCUS"
                                            FocusTimerState.PAUSED -> "SESSION PAUSED"
                                            FocusTimerState.COMPLETED -> "GOAL REACHED!"
                                            FocusTimerState.STOPPED -> "READY TO START"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = timerColor,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    if (isRunning || isPaused) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "+${sessionState.earnedRewardMinutes}m screen time earned",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = NeonEmerald,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Status Note
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (preventAppSwitching) Icons.Default.Shield else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isRunning) NeonEmerald else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRunning) {
                                    "App switcher locked · Instagram & games blocked"
                                } else if (isPaused) {
                                    "Timer paused · App switching temporarily unlocked"
                                } else {
                                    "Earns 1 min screen time per 2 min studied"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isRunning) NeonEmerald else TextMuted
                                )
                            )
                        }
                    }
                }
            }

            // Reward Banked Celebration Banner
            if (lastBankedSeconds != null && lastBankedSeconds!! > 0) {
                item {
                    val minutesBanked = (lastBankedSeconds!! / 60).coerceAtLeast(1)
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = NeonEmerald,
                        backgroundColor = SlateElevated
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "REWARD DEPOSITED TO BANK!",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = NeonEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "+$minutesBanked minutes added to your unlocked screen time ledger.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                                    )
                                }
                                Button(
                                    onClick = onNavigateToBank,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                                    modifier = Modifier.testTag("view_bank_button")
                                ) {
                                    Text("View Bank", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    val duration = sessionState.elapsedSeconds.coerceAtLeast(sessionState.totalSeconds)
                                    onNavigateToSummary(duration, sessionState.subject, lastBankedSeconds ?: (duration / 2L))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("open_post_study_summary_banner_button")
                            ) {
                                Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Post-Study Summary & Log Notes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else if (isCompleted) {
                item {
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = NeonEmerald,
                        backgroundColor = SlateElevated
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "FOCUS SESSION COMPLETED!",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NeonEmerald)
                                )
                            }
                            Text(
                                text = "Great job focusing on ${sessionState.subject}! Review session stats and log key notes via OCR or manual input.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = TextAlign.Center)
                            )
                            Button(
                                onClick = {
                                    val duration = sessionState.elapsedSeconds.coerceAtLeast(sessionState.totalSeconds)
                                    val earned = (duration / 2L).coerceAtLeast(300L)
                                    onNavigateToSummary(duration, sessionState.subject, earned)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("view_completed_summary_button")
                            ) {
                                Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Post-Study Summary & Log Notes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Primary Control Buttons
            item {
                when (sessionState.state) {
                    FocusTimerState.STOPPED, FocusTimerState.COMPLETED -> {
                        GlowingButton(
                            text = if (isCompleted) "START NEW FOCUS SESSION" else "START FOCUS SESSION",
                            onClick = {
                                viewModel.startTimer(
                                    durationMinutes = selectedDurationMinutes,
                                    subject = selectedSubject,
                                    preventAppSwitching = preventAppSwitching
                                )
                            },
                            icon = Icons.Default.PlayArrow,
                            color = ElectricIndigo,
                            containerColor = ElectricIndigo,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "start_focus_timer_button"
                        )
                    }

                    FocusTimerState.RUNNING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.pauseTimer() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("pause_timer_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.horizontalGradient(listOf(AmberWarning, AmberWarning))
                                )
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pause", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.stopTimer(bankReward = true) { duration, subject, earned ->
                                        onNavigateToSummary(duration, subject, earned)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("stop_timer_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("End & Bank", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    FocusTimerState.PAUSED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.resumeTimer() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("resume_timer_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.stopTimer(bankReward = true) { duration, subject, earned ->
                                        onNavigateToSummary(duration, subject, earned)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("stop_paused_timer_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Finish & Bank", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Configuration Section (Only editable when stopped or completed)
            if (isStopped || isCompleted) {
                // Preset Duration Picker
                item {
                    Text(
                        text = "SELECT DURATION",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            15 to "15m Sprint",
                            25 to "25m Pomodoro",
                            45 to "45m Deep Work",
                            60 to "60m Block"
                        ).forEach { (minutes, label) ->
                            val isSelected = selectedDurationMinutes == minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) ElectricIndigo else SlateElevated)
                                    .border(
                                        1.dp,
                                        if (isSelected) ElectricIndigoLight else SlateBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedDurationMinutes = minutes }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${minutes}m",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else TextPrimary
                                        )
                                    )
                                    Text(
                                        text = label.substringAfter("m "),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextMuted
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Focus Subject Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FOCUS SUBJECT / TOPIC",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "+ Custom",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricIndigoLight,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.clickable { showCustomSubjectDialog = true }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(subjectsList) { subject ->
                            val isSelected = selectedSubject == subject
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedSubject = subject },
                                label = { Text(subject) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricIndigo,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Strict App Switching Blocker Toggle
                item {
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (preventAppSwitching) ElectricIndigo.copy(alpha = 0.5f) else SlateBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (preventAppSwitching) NeonEmerald else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Strict Focus Shield",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Locks app switching and blocks TikTok, YouTube, and games until your session ends or is paused.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = preventAppSwitching,
                                onCheckedChange = { preventAppSwitching = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ElectricIndigo
                                )
                            )
                        }
                    }
                }
            }

            // Focus Rules & Tips Card
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CardBorder
                ) {
                    Text(
                        text = "THE POMODORO REWARD CONTRACT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricIndigoLight,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 25m studying earns 12m+ screen time into your ledger.\n• If you need to check an emergency message, hit Pause first.\n• Leaving the app during active sessions triggers the Distraction Blocker immediately.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        }
    }
}
