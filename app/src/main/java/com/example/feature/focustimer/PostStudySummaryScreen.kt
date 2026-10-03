package com.example.feature.focustimer

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.ui.theme.*

enum class NoteInputMode {
    MANUAL,
    OCR_CAMERA
}

@Composable
fun PostStudySummaryScreen(
    durationSeconds: Long,
    subject: String,
    earnedSeconds: Long,
    onNavigateHome: () -> Unit,
    onNavigateToBank: () -> Unit,
    onNavigateToDecks: () -> Unit = {},
    viewModel: PostStudySummaryViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var selectedMode by remember { mutableStateOf(NoteInputMode.MANUAL) }

    // Initialize session data on first entry
    LaunchedEffect(durationSeconds, subject, earnedSeconds) {
        viewModel.initSessionData(durationSeconds, subject, earnedSeconds)
    }

    // Photo Capture Launcher (Camera thumbnail)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.processOcrBitmap(bitmap)
            selectedMode = NoteInputMode.OCR_CAMERA
        }
    }

    // Visual Media Picker (Zero-permission photo picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.processOcrUri(uri)
            selectedMode = NoteInputMode.OCR_CAMERA
        }
    }

    val activity = context as? Activity

    if (state.showRewardedInterstitialPrompt && !state.rewardClaimed) {
        RewardedInterstitialPromptDialog(
            durationSeconds = state.durationSeconds,
            subject = state.subject,
            userStreak = state.userStreak,
            nextMultiplier = state.nextSessionMultiplier,
            bonusSeconds = state.bonusBankedSeconds,
            isAdLoaded = state.isRewardedAdLoaded,
            onWatchAd = {
                viewModel.triggerRewardedInterstitial(activity)
            },
            onDismiss = {
                viewModel.dismissPrompt()
            }
        )
    }

    LaunchedEffect(state.adStatusMessage) {
        state.adStatusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearAdStatusMessage()
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
                IconButton(
                    onClick = onNavigateHome,
                    modifier = Modifier.testTag("summary_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return Home",
                        tint = TextPrimary
                    )
                }

                Text(
                    text = "POST-STUDY SUMMARY",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                )

                StatusBadge(
                    text = "COMPLETED",
                    color = NeonEmerald,
                    backgroundColor = NeonEmeraldGlow
                )
            }
        },
        bottomBar = {
            Surface(
                color = SlateSurface,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlowingButton(
                        text = "DONE & RETURN HOME",
                        onClick = onNavigateHome,
                        icon = Icons.Default.Check,
                        color = ElectricIndigo,
                        containerColor = ElectricIndigo,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "summary_done_home_button"
                    )

                    OutlinedButton(
                        onClick = onNavigateToBank,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricIndigoLight),
                        border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Unlocked Screen Time Bank", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // 1. Hero Session Stats Card
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonEmerald.copy(alpha = 0.8f),
                    backgroundColor = SlateElevated
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(
                                text = state.subject.uppercase(),
                                color = ElectricIndigoLight,
                                backgroundColor = ElectricIndigo.copy(alpha = 0.2f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "100% Focused",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = NeonEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // Duration Studied Display
                        Column {
                            Text(
                                text = "DURATION STUDIED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val hours = durationSeconds / 3600
                            val minutes = (durationSeconds % 3600) / 60
                            val seconds = durationSeconds % 60
                            val durationFormatted = if (hours > 0) {
                                "${hours}h ${minutes}m ${seconds}s"
                            } else {
                                "${minutes}m ${seconds}s"
                            }

                            Text(
                                text = durationFormatted,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            )
                        }

                        HorizontalDivider(color = SlateBorder)

                        // Reward Banked Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Screen Time Banked",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary
                                        )
                                    )
                                    val earnedMin = (earnedSeconds / 60).coerceAtLeast(0)
                                    Text(
                                        text = "+$earnedMin Minutes",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = NeonEmerald
                                        )
                                    )
                                }
                            }

                            Button(
                                onClick = onNavigateToBank,
                                colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Bank", fontSize = 12.sp, color = ElectricIndigoLight)
                            }
                        }
                    }
                }
            }

            // 1.5 Rewarded Interstitial Incentive Card
            item {
                if (state.rewardClaimed) {
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = NeonEmerald,
                        backgroundColor = SlateElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeonEmeraldGlow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🔥 ${state.nextSessionMultiplier}x Next Session Boost Active!",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "+${state.bonusBankedSeconds / 60}m banked. Your next study session will earn 50% extra screen time!",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                } else {
                    CyberCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openPrompt() },
                        borderColor = AmberWarning.copy(alpha = 0.8f),
                        backgroundColor = SlateElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AmberWarning.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Incentivize Next Session",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    )
                                    StatusBadge(
                                        text = "${state.nextSessionMultiplier}x BOOST",
                                        color = AmberWarning,
                                        backgroundColor = AmberWarning.copy(alpha = 0.15f)
                                    )
                                }
                                Text(
                                    text = "Watch short ad to bank +10m screen time & earn 1.5x on your next session",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.openPrompt() },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Claim", fontSize = 12.sp, color = ObsidianBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Study Notes Section Header & Input Mode Switcher
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "LOG STUDY NOTES & TAKEAWAYS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricIndigoLight,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == NoteInputMode.MANUAL,
                            onClick = { selectedMode = NoteInputMode.MANUAL },
                            label = { Text("Manual Input") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricIndigo,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = SlateElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = selectedMode == NoteInputMode.OCR_CAMERA,
                            onClick = { selectedMode = NoteInputMode.OCR_CAMERA },
                            label = { Text("Scan with OCR") },
                            leadingIcon = {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricIndigo,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = SlateElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. OCR Scanner Controls (When OCR mode is selected)
            if (selectedMode == NoteInputMode.OCR_CAMERA) {
                item {
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = ElectricIndigo.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "CAPTURE TEXTBOOK OR HANDWRITTEN NOTES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ElectricIndigoLight,
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            Text(
                                text = "Use on-device OCR to extract text directly from your textbook, notebook, or study sheet into your session notes.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { cameraLauncher.launch(null) },
                                    modifier = Modifier.weight(1f).testTag("ocr_camera_snap_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Take Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f).testTag("ocr_gallery_pick_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricIndigoLight),
                                    border = BorderStroke(1.dp, ElectricIndigoLight.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Image", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // OCR Status Feedback
                            if (state.isOcrScanning) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ElectricIndigoLight
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.ocrStatusMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                            } else if (state.ocrStatusMessage.isNotBlank()) {
                                Text(
                                    text = state.ocrStatusMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (state.lastOcrExtractedText != null) NeonEmerald else AmberWarning,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            // Extracted OCR text box
                            state.lastOcrExtractedText?.let { extracted ->
                                Surface(
                                    color = ObsidianBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "EXTRACTED TEXT PREVIEW",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = NeonEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = extracted.take(300) + if (extracted.length > 300) "..." else "",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { viewModel.appendExtractedOcrText() },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Insert into Notes", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Note Editor (Title, Tags, Content)
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CardBorder
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = state.noteTitle,
                            onValueChange = { viewModel.updateTitle(it) },
                            label = { Text("Note Title", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricIndigo,
                                unfocusedBorderColor = SlateBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        // Quick Prompt Tags
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("#KeyConcept", "#Formula", "#ExamReview", "#Question").forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SlateSurface,
                                    border = BorderStroke(1.dp, SlateBorder),
                                    modifier = Modifier.clickable { viewModel.appendTag(tag) }
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = ElectricIndigoLight,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Main Content Multi-line text field
                        OutlinedTextField(
                            value = state.noteContent,
                            onValueChange = { viewModel.updateContent(it) },
                            label = { Text("Session Notes & Key Points", color = TextSecondary) },
                            placeholder = {
                                Text(
                                    "Type your notes here or capture textbook paragraphs via OCR above...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 280.dp)
                                .testTag("note_content_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricIndigo,
                                unfocusedBorderColor = SlateBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        // Word & Char counter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val words = state.noteContent.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                            Text(
                                text = "$words words · ${state.noteContent.length} chars",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
                            )
                            if (state.isSavedToCoursework) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Saved to Coursework", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Action Cards: Save to Database, Upload to Drive, Generate Deck
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CardBorder
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SAVE & EXPORT SESSION NOTES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        // 1. Save Note to Coursework
                        Button(
                            onClick = {
                                viewModel.saveNoteToCoursework {
                                    Toast.makeText(context, "Notes saved to ${state.subject} Coursework!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = state.noteContent.isNotBlank() && !state.isSaving,
                            modifier = Modifier.fillMaxWidth().testTag("save_notes_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, if (state.isSavedToCoursework) NeonEmerald else ElectricIndigo)
                        ) {
                            Icon(
                                imageVector = if (state.isSavedToCoursework) Icons.Default.Check else Icons.Default.Save,
                                contentDescription = null,
                                tint = if (state.isSavedToCoursework) NeonEmerald else ElectricIndigoLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isSavedToCoursework) "Saved to Coursework Database" else "Save Note to School Materials",
                                color = if (state.isSavedToCoursework) NeonEmerald else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // 2. Upload to Google Drive
                        Button(
                            onClick = {
                                viewModel.uploadNoteToGoogleDrive { result ->
                                    Toast.makeText(context, "Uploaded to Google Drive: ${result.name}", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = state.noteContent.isNotBlank() && !state.isUploadingToDrive,
                            modifier = Modifier.fillMaxWidth().testTag("upload_notes_drive_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            if (state.isUploadingToDrive) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricIndigoLight)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uploading to Drive...", color = TextPrimary)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.driveUploadSuccess == true) "Uploaded to Google Drive!" else "Upload Notes to Google Drive",
                                    color = if (state.driveUploadSuccess == true) NeonEmerald else TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // 3. Convert to Flashcards & Quiz Deck
                        Button(
                            onClick = {
                                viewModel.convertNotesToFlashcardDeck { deckId ->
                                    Toast.makeText(context, "Interactive Flashcard Deck Generated!", Toast.LENGTH_SHORT).show()
                                    onNavigateToDecks()
                                }
                            },
                            enabled = state.noteContent.isNotBlank() && !state.isGeneratingDeck,
                            modifier = Modifier.fillMaxWidth().testTag("generate_deck_from_notes_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            if (state.isGeneratingDeck) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AmberWarning)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating Questions & Cards...", color = TextPrimary)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Convert Notes to Flashcard Deck", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RewardedInterstitialPromptDialog(
    durationSeconds: Long,
    subject: String,
    userStreak: Int,
    nextMultiplier: Float,
    bonusSeconds: Long,
    isAdLoaded: Boolean,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdownSeconds by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        while (countdownSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            countdownSeconds--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateElevated,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(
                        text = "DEEP-WORK MILESTONE",
                        color = AmberWarning,
                        backgroundColor = AmberWarning.copy(alpha = 0.15f)
                    )
                    Text(
                        text = if (countdownSeconds > 0) "Auto-ready ${countdownSeconds}s" else if (isAdLoaded) "Ad Ready" else "Ad Loading",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }

                Text(
                    text = "Incentivize Next Session!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Fantastic focus! You logged ${durationSeconds / 60}m on $subject. Watch a short Google AdMob sponsor message to activate your next session boost:",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )

                // Perks Card
                Surface(
                    color = SlateSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "+${bonusSeconds / 60}m Screen Time (Instant)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = "Deposited directly into your balance bank",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${nextMultiplier}x Reward Multiplier (Next Session)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = "Earn 50% more screen time on your next study session",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$userStreak Day Streak Safeguarded",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = "Keeps your daily habit momentum alive",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isAdLoaded) {
                GlowingButton(
                    text = "WATCH AD & CLAIM ${nextMultiplier}x (+${bonusSeconds / 60}M)",
                    onClick = onWatchAd,
                    icon = Icons.Default.PlayArrow,
                    color = AmberWarning,
                    containerColor = AmberWarning,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "claim_rewarded_interstitial_button"
                )
            } else {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sponsor Ad Unavailable (Continue)")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("skip_rewarded_interstitial_button")
            ) {
                Text(
                    text = "Skip For Now",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

