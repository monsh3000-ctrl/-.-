package com.example.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.StatusBadge
import com.example.core.permissions.PermissionManager
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onManageSchoolMaterials: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showDeleteMaterialsDialog by remember { mutableStateOf(false) }
    var showDriveManagerDialog by remember { mutableStateOf(false) }
    var showOAuthConsentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    if (showOAuthConsentDialog) {
        com.example.feature.drive.GoogleDriveOAuthConsentDialog(
            initialEmail = state.googleDriveAccount,
            onDismissRequest = { showOAuthConsentDialog = false },
            onAuthorizedSuccess = { _ ->
                viewModel.refreshPermissions()
            }
        )
    }

    if (showDriveManagerDialog) {
        com.example.feature.drive.GoogleDriveManagerDialog(
            onDismissRequest = { showDriveManagerDialog = false }
        )
    }

    // Disconnect School Dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect School Account?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will stop automatic synchronization of new assignments and teacher files. Your existing study decks will remain on your device.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.disconnectSchool()
                        showDisconnectDialog = false
                        Toast.makeText(context, "School account disconnected", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SlateSurface
        )
    }

    // Delete Materials Dialog
    if (showDeleteMaterialsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMaterialsDialog = false },
            title = { Text("Delete Imported Materials?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will remove the local cache of imported school files. You can re-sync them from your school platform or Google Drive anytime.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearImportedMaterials()
                        showDeleteMaterialsDialog = false
                        Toast.makeText(context, "Imported materials cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMaterialsDialog = false }) {
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                Text(
                    text = "SETTINGS & INTEGRATIONS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
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
            // School Platform & Google Drive Section
            item {
                Text(
                    text = "CONNECTED SCHOOL & CLOUD",
                    style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                )
            }

            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = ElectricIndigo.copy(alpha = 0.5f),
                    backgroundColor = SlateSurface
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = state.schoolConnection?.platformName ?: "School Platform",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = state.schoolConnection?.schoolUrl ?: "https://canvas.instructure.com",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                            StatusBadge(text = "CONNECTED", color = NeonEmerald)
                        }

                        Surface(
                            color = ObsidianBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Account: ${state.schoolConnection?.username ?: "student@school.edu"}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Google Drive: ${if (state.googleDriveAccount.isNotBlank()) state.googleDriveAccount else "Not Linked (Tap below to link)"}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight)
                                )
                                Text(
                                    text = "Folder: Google Drive → StudyLock → School Materials",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                                )
                            }
                        }

                        if (state.isGoogleDriveConnected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showDriveManagerDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                                    modifier = Modifier.weight(1.2f).testTag("manage_drive_folders_button")
                                ) {
                                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Manage Folders", fontSize = 12.sp, color = TextPrimary)
                                }

                                OutlinedButton(
                                    onClick = { showOAuthConsentDialog = true },
                                    modifier = Modifier.weight(0.8f)
                                ) {
                                    Text("Switch", fontSize = 12.sp, color = ElectricIndigoLight)
                                }
                            }
                        } else {
                            Button(
                                onClick = { showOAuthConsentDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                modifier = Modifier.fillMaxWidth().testTag("link_google_drive_button")
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Link with Google Account", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // Auto-Sync Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic Synchronization",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = "Periodically checks school platform for new homework, PDFs, and worksheets uploaded by teachers.",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, lineHeight = 16.sp)
                                )
                            }

                            Switch(
                                checked = state.isAutoSyncEnabled,
                                onCheckedChange = { viewModel.setAutoSync(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ElectricIndigo,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SlateElevated
                                )
                            )
                        }

                        HorizontalDivider(color = SlateBorder)

                        // Action Buttons: Review Materials, Disconnect
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showDisconnectDialog = true }) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, tint = CoralRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Disconnect School", color = CoralRed, fontSize = 12.sp)
                            }

                            Button(
                                onClick = onManageSchoolMaterials,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Review Materials (${state.importedMaterialCount})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // AI Study Deck Generation & Management
            item {
                CyberCard(modifier = Modifier.fillMaxWidth(), borderColor = CardBorder) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "AI-GENERATED CONTENT MANAGEMENT",
                            style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight, fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = "Use Gemini AI to analyze all collected school materials from Google Drive and regenerate study decks.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.regenerateStudyDecks { count ->
                                        Toast.makeText(context, "Regenerated study decks for $count materials!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Regenerate Decks", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showDeleteMaterialsDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear Files", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Daily Study Goal
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CardBorder
                ) {
                    Text(
                        text = "DAILY STUDY GOAL",
                        style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${state.dailyGoalMinutes} Minutes / Day",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            val isSelected = state.dailyGoalMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDailyGoal(mins) },
                                label = { Text("${mins}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricIndigo,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Bank Cap
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CardBorder
                ) {
                    Text(
                        text = "DAILY REWARD CAP",
                        style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${state.dailyMaxRewardMinutes} Minutes Max",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(60, 90, 120, 180).forEach { mins ->
                            val isSelected = state.dailyMaxRewardMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDailyMaxReward(mins) },
                                label = { Text("${mins}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricIndigo,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Global Protection Toggle
            item {
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
                                text = "Screen Time Guard",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            Text(
                                text = "Redirects to study when locked apps are opened without balance.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }

                        Switch(
                            checked = state.isBlockingEnabled,
                            onCheckedChange = { viewModel.setBlockingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElectricIndigo,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SlateElevated
                            )
                        )
                    }
                }
            }
        }
    }
}
