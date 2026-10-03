package com.example.feature.decks

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.database.entity.StudyDeckEntity
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.ui.theme.*

@Composable
fun DecksScreen(
    onNavigateBack: () -> Unit,
    onStudyDeck: (deckId: Long) -> Unit,
    onScanNewDeck: () -> Unit,
    onViewSchoolMaterials: () -> Unit = {},
    viewModel: DecksViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDrivePickDialog by remember { mutableStateOf(false) }
    var showDriveManagerDialog by remember { mutableStateOf(false) }

    if (showDriveManagerDialog) {
        com.example.feature.drive.GoogleDriveManagerDialog(
            onDismissRequest = { showDriveManagerDialog = false }
        )
    }

    // Manual Create Dialog
    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newSubject by remember { mutableStateOf("") }
        var newContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Study Deck", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Deck Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSubject,
                        onValueChange = { newSubject = it },
                        label = { Text("Subject (e.g. Physics, History)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("Study Text / Key Concepts") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank() && newContent.isNotBlank()) {
                            viewModel.createDeck(newTitle, newSubject, newContent) {
                                showCreateDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SlateSurface
        )
    }

    // Google Drive Material Pick Dialog
    if (showDrivePickDialog) {
        AlertDialog(
            onDismissRequest = { showDrivePickDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ElectricIndigoLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google Drive Files", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }

                    IconButton(
                        onClick = {
                            showDrivePickDialog = false
                            showDriveManagerDialog = true
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Manage Folders", tint = ElectricIndigoLight)
                    }
                }
            },
            text = {
                if (state.schoolMaterials.isEmpty()) {
                    Text("No school materials connected yet. Go to School Materials to sync your courses.", color = TextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.schoolMaterials) { material ->
                            Surface(
                                color = SlateElevated,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.createDeckFromMaterial(material.id) { newDeckId ->
                                            showDrivePickDialog = false
                                            Toast.makeText(context, "Deck generated from ${material.title}!", Toast.LENGTH_SHORT).show()
                                            onStudyDeck(newDeckId)
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = material.subject,
                                            style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight, fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = material.fileType,
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = material.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                    )
                                    Text(
                                        text = material.unitName,
                                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDrivePickDialog = false }) {
                    Text("Close", color = TextMuted)
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
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("decks_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                Text(
                    text = "STUDY DECKS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )

                IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.testTag("add_deck_button")) {
                    Icon(Icons.Default.Add, contentDescription = "Create Deck", tint = ElectricIndigoLight)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Three Primary Creation Options: Scan Notes, Auto-generate Decks, Create from Google Drive
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "CREATE STUDY MATERIAL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Scan Notes Button
                        CyberCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onScanNewDeck() }
                                .testTag("scan_notes_card"),
                            borderColor = ElectricIndigo.copy(alpha = 0.6f),
                            backgroundColor = SlateSurface
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text("📷", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Scan Notes",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    text = "Camera OCR",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        // 2. Auto-generate Decks Button
                        CyberCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    viewModel.autoGenerateAllDecks { count ->
                                        Toast.makeText(context, "AI generated $count study decks from your school materials!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .testTag("auto_generate_decks_card"),
                            borderColor = NeonEmerald.copy(alpha = 0.6f),
                            backgroundColor = SlateSurface
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text("✨", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Auto-Generate",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = NeonEmerald),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    text = "AI School Sync",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        // 3. Create from Google Drive Button
                        CyberCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDrivePickDialog = true }
                                .testTag("create_from_drive_card"),
                            borderColor = AmberWarning.copy(alpha = 0.6f),
                            backgroundColor = SlateSurface
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text("📁", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Google Drive",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = AmberWarning),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    text = "Select Course File",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // School Materials Quick Bar
            item {
                Surface(
                    color = SlateElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewSchoolMaterials() }
                        .testTag("school_materials_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "School Materials & Drive Sync",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = "${state.schoolMaterials.size} files organized in Google Drive",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            }

            // Decks Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Study Decks (${state.decks.size})",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Deck Items
            if (state.decks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Your Study Library is Empty",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Scan notes, auto-generate from school materials, or pick files from Google Drive.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(state.decks, key = { it.id }) { deck ->
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = CardBorder
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(text = deck.subject, color = ElectricIndigoLight)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (deck.description.contains("Google Drive", ignoreCase = true)) {
                                        StatusBadge(text = "GOOGLE DRIVE", color = NeonEmerald)
                                    } else if (deck.description.contains("Scanned", ignoreCase = true)) {
                                        StatusBadge(text = "SCANNED", color = AmberWarning)
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.deleteDeck(deck) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Text(
                                text = deck.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )

                            Text(
                                text = deck.description,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(color = SlateBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ready for Quiz & Flashcards",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )

                                Button(
                                    onClick = { onStudyDeck(deck.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("STUDY", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
