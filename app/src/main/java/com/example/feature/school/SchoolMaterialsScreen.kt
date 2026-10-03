package com.example.feature.school

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.entity.SchoolMaterialEntity
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.core.repository.StudyLockRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SchoolMaterialsScreen(
    onNavigateBack: () -> Unit,
    onStudyDeckCreated: (deckId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { StudyLockRepository(context) }

    val connection by repository.activeSchoolConnectionFlow.collectAsState(initial = null)
    val materials by repository.allSchoolMaterialsFlow.collectAsState(initial = emptyList())

    var selectedSubject by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }

    val subjects = remember(materials) {
        listOf("All") + materials.map { it.subject }.distinct().sorted()
    }

    val filteredMaterials = remember(materials, selectedSubject, searchQuery) {
        materials.filter { material ->
            val matchesSubject = selectedSubject == "All" || material.subject.equals(selectedSubject, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    material.title.contains(searchQuery, ignoreCase = true) ||
                    material.unitName.contains(searchQuery, ignoreCase = true) ||
                    material.contentSummary.contains(searchQuery, ignoreCase = true)
            matchesSubject && matchesSearch
        }
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
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("school_materials_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                Text(
                    text = "SCHOOL MATERIALS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )

                IconButton(
                    onClick = {
                        isSyncing = true
                        coroutineScope.launch {
                            val newItems = repository.syncNewSchoolMaterials()
                            isSyncing = false
                            val msg = if (newItems.isNotEmpty()) "Synced ${newItems.size} new teacher upload(s)!" else "All materials are up to date."
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("sync_school_materials_button")
                ) {
                    Icon(
                        imageVector = if (isSyncing) Icons.Default.SyncProblem else Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = ElectricIndigoLight
                    )
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
            // Header Info Card
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = ElectricIndigo.copy(alpha = 0.5f),
                    backgroundColor = SlateSurface
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = connection?.platformName ?: "Connected School Portal",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            StatusBadge(text = "GOOGLE DRIVE SYNCED", color = NeonEmerald)
                        }

                        Text(
                            text = "Root: Google Drive → Study App → School Materials",
                            style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight)
                        )

                        Text(
                            text = "AI analyzes these files and organizes them by subject and class unit. You can generate quizzes and flashcards directly from any item.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search homework, worksheets, guides...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricIndigo,
                        unfocusedBorderColor = SlateBorder
                    )
                )
            }

            // Subject Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subjects) { subject ->
                        val isSelected = selectedSubject == subject
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSubject = subject },
                            label = { Text(subject) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricIndigo,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Materials Count & Action Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Materials (${filteredMaterials.size})",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )

                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val count = repository.autoGenerateAllDecksFromSchoolMaterials()
                                Toast.makeText(context, "Generated study decks for $count materials!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Generate All Decks", color = NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Materials List
            if (filteredMaterials.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No materials found", color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text("Try changing search query or sync new materials.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(filteredMaterials, key = { it.id }) { material ->
                    SchoolMaterialCard(
                        material = material,
                        onOpenInDrive = {
                            // Launch Google Drive web / app viewer intent
                            try {
                                val driveIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://drive.google.com/drive/my-drive")
                                }
                                context.startActivity(driveIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Opening Google Drive: ${material.driveFolderPath}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onGenerateDeck = {
                            coroutineScope.launch {
                                val newDeckId = repository.autoGenerateDeckFromSchoolMaterial(material.id)
                                if (newDeckId != null) {
                                    Toast.makeText(context, "Created study deck: ${material.subject}!", Toast.LENGTH_SHORT).show()
                                    onStudyDeckCreated(newDeckId)
                                }
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                repository.deleteSchoolMaterial(material.id)
                                Toast.makeText(context, "Deleted ${material.title}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SchoolMaterialCard(
    material: SchoolMaterialEntity,
    onOpenInDrive: () -> Unit,
    onGenerateDeck: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    CyberCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = CardBorder
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top Row: Type and Subject Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (material.fileType) {
                            "HOMEWORK" -> "📝 HOMEWORK"
                            "WORKSHEET" -> "📊 WORKSHEET"
                            "STUDY_GUIDE" -> "📖 STUDY GUIDE"
                            "EXPLAINER" -> "💡 EXPLAINER"
                            "DOCUMENT" -> "📜 PRIMARY SOURCE"
                            "READING" -> "📚 READING"
                            else -> "📄 PDF"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = when (material.fileType) {
                                "HOMEWORK" -> CoralRed
                                "STUDY_GUIDE" -> NeonEmerald
                                "EXPLAINER" -> AmberWarning
                                else -> ElectricIndigoLight
                            },
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("·", color = TextMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = material.subject,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    )
                }

                if (material.isDeckGenerated) {
                    StatusBadge(text = "DECK GENERATED", color = NeonEmerald)
                }
            }

            // Title & Unit
            Column {
                Text(
                    text = material.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
                Text(
                    text = material.unitName,
                    style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight)
                )
            }

            // File Name & Drive Location
            Surface(
                color = ObsidianBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = material.driveFolderPath,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "File: ${material.originalFileName}",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Medium)
                    )
                }
            }

            // Description Summary
            Text(
                text = material.contentSummary,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp),
                maxLines = 3
            )

            HorizontalDivider(color = SlateBorder)

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenInDrive) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open in Drive", color = ElectricIndigoLight, fontSize = 12.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(20.dp))
                    }

                    Button(
                        onClick = onGenerateDeck,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (material.isDeckGenerated) "Regenerate Deck" else "Create Deck", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
