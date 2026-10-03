package com.example.feature.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.StatusBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenTimeBankScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBlockedApps: () -> Unit,
    viewModel: ScreenTimeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val balance = state.balance
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    var showAdjustDialog by remember { mutableStateOf(false) }

    if (showAdjustDialog) {
        var minutesStr by remember { mutableStateOf("15") }
        var reasonStr by remember { mutableStateOf("Manual Study Credit") }

        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("Add Manual Adjustment", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minutesStr,
                        onValueChange = { minutesStr = it },
                        label = { Text("Minutes (positive or negative)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reasonStr,
                        onValueChange = { reasonStr = it },
                        label = { Text("Reason / Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = minutesStr.toIntOrNull() ?: 0
                        if (mins != 0) {
                            viewModel.addAdjustment(mins, reasonStr)
                        }
                        showAdjustDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SlateSurface
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
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("bank_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                Text(
                    text = "SCREEN TIME BANK",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.refreshData() }, modifier = Modifier.testTag("sync_bank_usage_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Balance", tint = ElectricIndigoLight)
                    }
                    IconButton(onClick = { showAdjustDialog = true }, modifier = Modifier.testTag("adjust_balance_button")) {
                        Icon(Icons.Default.Tune, contentDescription = "Adjust", tint = ElectricIndigoLight)
                    }
                }
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
            // Balance Hero
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = ElectricIndigo.copy(alpha = 0.5f),
                    backgroundColor = SlateSurface
                ) {
                    Text(
                        text = "AUTHORITATIVE BALANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${balance.remainingMinutes}",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = if (balance.currentBalanceSeconds > 0) NeonEmerald else CoralRed
                            )
                        )
                        Text(
                            text = "m ${balance.remainingSecondsRemainder}s",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Today Earned", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text("+${balance.todayEarnedMinutes}m", style = MaterialTheme.typography.titleSmall.copy(color = NeonEmerald, fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("Today Used", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                            Text("-${balance.todayUsedMinutes}m", style = MaterialTheme.typography.titleSmall.copy(color = CoralRed, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Quick Link to Blocked Apps
            item {
                OutlinedButton(
                    onClick = onNavigateToBlockedApps,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Protected Apps (${state.blockedApps.count { it.isBlocked }} Active)", color = TextPrimary)
                }
            }

            // Google Mobile Ads / AdSense Monetization Banner
            item {
                com.example.core.ads.AdBannerView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }

            // Transaction History Ledger Header
            item {
                Text(
                    text = "TRANSACTION LEDGER (${state.transactions.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (state.transactions.isEmpty()) {
                item {
                    Text(
                        text = "No screen-time transactions recorded yet. Complete a study session to earn time.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
            } else {
                items(state.transactions, key = { it.id }) { tx ->
                    val isCredit = tx.amountSeconds > 0
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = CardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateFormat.format(Date(tx.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )
                            }

                            val minutesFormatted = tx.amountSeconds / 60
                            val secondsRemainder = Math.abs(tx.amountSeconds % 60)
                            Text(
                                text = if (isCredit) "+${minutesFormatted}m ${secondsRemainder}s" else "${minutesFormatted}m ${secondsRemainder}s",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCredit) NeonEmerald else CoralRed
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
