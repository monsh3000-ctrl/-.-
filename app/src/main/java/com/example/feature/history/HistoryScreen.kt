package com.example.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.StatusBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("history_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                Text(
                    text = "STUDY HISTORY",
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Header Card
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CardBorder
                ) {
                    Text(
                        text = "LIFETIME REPUTATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricIndigoLight,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Sessions", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text("${state.sessions.size}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        }
                        Column {
                            Text("Study Time", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text("${state.totalStudyMinutes}m", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = ElectricIndigoLight))
                        }
                        Column {
                            Text("Earned Time", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text("+${state.totalEarnedMinutes}m", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NeonEmerald))
                        }
                    }
                }
            }

            item {
                Text(
                    text = "LOGGED SESSIONS (${state.sessions.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (state.sessions.isEmpty()) {
                item {
                    Text(
                        text = "No study sessions completed yet. Scan your first page or study a starter deck to start building your history.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            } else {
                items(state.sessions, key = { it.id }) { session ->
                    val accuracy = if (session.totalQuestions > 0) ((session.correctCount.toFloat() / session.totalQuestions) * 100).toInt() else 100
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = CardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.deckTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateFormat.format(Date(session.completedAt ?: session.startedAt)),
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )
                            }
                            StatusBadge(
                                text = "+${session.earnedSeconds / 60}m Earned",
                                color = NeonEmerald,
                                backgroundColor = NeonEmeraldGlow
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mode: ${session.mode}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                            Text(
                                text = "Score: ${session.correctCount}/${session.totalQuestions} ($accuracy%)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (accuracy >= 70) NeonEmerald else AmberWarning,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Duration: ${session.durationSeconds / 60}m ${session.durationSeconds % 60}s",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                    }
                }
            }
        }
    }
}
