package com.example.feature.onboarding

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import com.example.core.auth.GoogleAuthManager
import com.example.core.auth.UserSession
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.core.drive.GoogleAccountProfile
import com.example.core.drive.GoogleDriveAuthManager
import com.example.core.repository.StudyLockRepository
import com.example.core.school.SchoolPlatformDetector
import com.example.core.school.SchoolSyncManager
import com.example.core.school.model.PlatformDetectionResult
import com.example.core.school.model.SchoolAuthType
import com.example.core.sync.SchoolSyncWorker
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { StudyLockRepository(context) }
    val googleAuthManager = remember { GoogleAuthManager(context) }
    val driveAuthManager = remember { GoogleDriveAuthManager(context) }
    val platformDetector = remember { SchoolPlatformDetector() }
    val syncManager = remember { SchoolSyncManager(context, repository.database, repository.preferences, repository.contentGenerator) }

    // 0: Welcome
    // 1: Google Sign In
    // 2: Enter School Website
    // 3: Platform Detection & Authentication
    // 4: Connect Google Drive (Optional)
    // 5: Ready to Learn
    var currentStep by remember { mutableIntStateOf(0) }

    // User session state
    val userSession by googleAuthManager.currentSessionFlow.collectAsState(initial = null)
    var isSigningInGoogle by remember { mutableStateOf(false) }

    // School connection state
    var schoolWebsiteUrl by remember { mutableStateOf("") }
    var isDetectingSchool by remember { mutableStateOf(false) }
    var detectionResult by remember { mutableStateOf<PlatformDetectionResult?>(null) }
    var schoolAuthToken by remember { mutableStateOf("") }
    var schoolStudentUsername by remember { mutableStateOf("") }
    var isConnectingSchool by remember { mutableStateOf(false) }
    var schoolConnectionSuccess by remember { mutableStateOf(false) }
    var connectionProgressMessage by remember { mutableStateOf("") }

    // Google Drive state
    var driveProfile by remember { mutableStateOf<GoogleAccountProfile?>(null) }
    var isConnectingDrive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        driveProfile = driveAuthManager.getCurrentProfile()
    }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STUDYLOCK ONBOARDING",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = ElectricIndigoLight,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                Text(
                    text = "STEP ${currentStep + 1} OF 6",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        },
        bottomBar = {
            Surface(
                color = SlateSurface,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0 && currentStep < 5) {
                        TextButton(
                            onClick = { currentStep-- },
                            modifier = Modifier.testTag("onboarding_back_button")
                        ) {
                            Text("Back", color = TextSecondary)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    when (currentStep) {
                        0 -> {
                            GlowingButton(
                                text = "GET STARTED",
                                onClick = { currentStep = 1 },
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                modifier = Modifier.testTag("onboarding_welcome_next_button")
                            )
                        }
                        1 -> {
                            Button(
                                onClick = { currentStep = 2 },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                modifier = Modifier.testTag("onboarding_step1_next_button")
                            ) {
                                Text(if (userSession != null) "Continue" else "Skip For Now")
                            }
                        }
                        2 -> {
                            GlowingButton(
                                text = if (isDetectingSchool) "DETECTING..." else "ANALYZE & CONNECT",
                                onClick = {
                                    if (schoolWebsiteUrl.isNotBlank()) {
                                        isDetectingSchool = true
                                        coroutineScope.launch {
                                            val (_, result) = platformDetector.detectPlatform(schoolWebsiteUrl)
                                            detectionResult = result
                                            isDetectingSchool = false
                                            currentStep = 3
                                        }
                                    } else {
                                        Toast.makeText(context, "Please enter your school website URL", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                icon = Icons.Default.Search,
                                modifier = Modifier.testTag("analyze_school_button")
                            )
                        }
                        3 -> {
                            Button(
                                onClick = {
                                    if (schoolConnectionSuccess || detectionResult?.supportsAutomatedSync == false) {
                                        currentStep = 4
                                    } else {
                                        // Attempt connect
                                        isConnectingSchool = true
                                        coroutineScope.launch {
                                            val res = syncManager.connectAndIngestSchool(
                                                schoolUrl = schoolWebsiteUrl,
                                                authTokenOrPassword = schoolAuthToken,
                                                studentUsername = schoolStudentUsername
                                            ) { msg, _ ->
                                                connectionProgressMessage = msg
                                            }
                                            isConnectingSchool = false
                                            if (res.isSuccess) {
                                                schoolConnectionSuccess = true
                                                currentStep = 4
                                            } else {
                                                Toast.makeText(context, "Connection failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                modifier = Modifier.testTag("onboarding_step3_next_button")
                            ) {
                                Text(if (isConnectingSchool) "Connecting..." else if (schoolConnectionSuccess || detectionResult?.supportsAutomatedSync == false) "Continue" else "Connect Portal")
                            }
                        }
                        4 -> {
                            Button(
                                onClick = { currentStep = 5 },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                modifier = Modifier.testTag("onboarding_step4_next_button")
                            ) {
                                Text(if (driveProfile?.isConnected == true) "Continue" else "Skip For Now")
                            }
                        }
                        5 -> {
                            GlowingButton(
                                text = "ENTER STUDYLOCK",
                                onClick = {
                                    coroutineScope.launch {
                                        repository.preferences.setOnboardingCompleted(true)
                                        // Schedule background sync
                                        SchoolSyncWorker.schedulePeriodicSync(context)
                                        onFinished()
                                    }
                                },
                                icon = Icons.Default.Check,
                                modifier = Modifier.fillMaxWidth().testTag("enter_app_button")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            when (currentStep) {
                0 -> WelcomeStep()
                1 -> GoogleSignInStep(
                    userSession = userSession,
                    isSigningIn = isSigningInGoogle,
                    onSignInClicked = {
                        isSigningInGoogle = true
                        coroutineScope.launch {
                            val res = googleAuthManager.signInWithGoogle(context)
                            isSigningInGoogle = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Welcome, ${res.getOrNull()?.displayName}!", Toast.LENGTH_SHORT).show()
                                currentStep = 2
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Sign-in failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
                2 -> SchoolWebsiteEntryStep(
                    url = schoolWebsiteUrl,
                    onUrlChange = { schoolWebsiteUrl = it },
                    isDetecting = isDetectingSchool
                )
                3 -> PlatformDetectionAndAuthStep(
                    url = schoolWebsiteUrl,
                    detectionResult = detectionResult,
                    token = schoolAuthToken,
                    onTokenChange = { schoolAuthToken = it },
                    username = schoolStudentUsername,
                    onUsernameChange = { schoolStudentUsername = it },
                    isConnecting = isConnectingSchool,
                    progressMessage = connectionProgressMessage,
                    isSuccess = schoolConnectionSuccess
                )
                4 -> GoogleDriveConnectionStep(
                    driveProfile = driveProfile,
                    isConnecting = isConnectingDrive,
                    onConnectClicked = {
                        isConnectingDrive = true
                        coroutineScope.launch {
                            val res = driveAuthManager.signInWithCredentialManager(context)
                            isConnectingDrive = false
                            if (res.isSuccess) {
                                driveProfile = driveAuthManager.getCurrentProfile()
                                Toast.makeText(context, "Google Drive Connected!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Drive connection cancelled or failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                5 -> ReadyStep(
                    userSession = userSession,
                    schoolConnected = schoolWebsiteUrl.isNotBlank(),
                    driveConnected = driveProfile?.isConnected == true
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 0: Welcome
// ---------------------------------------------------------------------------
@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(ElectricIndigoGlow),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.School, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(46.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Welcome to StudyLock",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, color = TextPrimary),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Universal Academic Companion · Learn & Unlock Screen Time",
            style = MaterialTheme.typography.titleSmall.copy(color = ElectricIndigoLight, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        CyberCard(modifier = Modifier.fillMaxWidth(), borderColor = CardBorder) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "StudyLock connects to your school portal and Google Drive to automatically organize your coursework, generate AI study decks, and award legitimate screen time.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 22.sp)
                )

                HorizontalDivider(color = SlateBorder)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Connect Any School Portal or LMS", style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Dynamic Google Drive Course Organization", style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("AI-Grounded Study Decks from Real Materials", style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1: Google Sign In
// ---------------------------------------------------------------------------
@Composable
private fun GoogleSignInStep(
    userSession: UserSession?,
    isSigningIn: Boolean,
    onSignInClicked: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Sign in with Google",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Use your student or personal Google account to securely back up your progress, study streak, and Google Drive materials.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, textAlign = TextAlign.Center)
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (userSession != null && userSession.isAuthenticated) {
            Surface(
                color = NeonEmeraldGlow,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NeonEmerald),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Signed in as ${userSession.displayName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Text(text = userSession.email, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                    }
                }
            }
        } else {
            Button(
                onClick = onSignInClicked,
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                border = BorderStroke(1.dp, ElectricIndigo),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ElectricIndigoLight)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Signing in...", color = TextPrimary)
                } else {
                    Icon(Icons.Default.Login, contentDescription = null, tint = ElectricIndigoLight)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Continue with Google", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2: Enter School Website (NO predefined dropdown)
// ---------------------------------------------------------------------------
@Composable
private fun SchoolWebsiteEntryStep(
    url: String,
    onUrlChange: (String) -> Unit,
    isDetecting: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter your school website",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Type the web link you use to check your courses, homework, or syllabus (e.g. Canvas, Moodle, Classroom, Blackboard, or your institution's portal).",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("School Website URL") },
            placeholder = { Text("https://school.example.com") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = ElectricIndigoLight) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("school_website_url_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricIndigo,
                unfocusedBorderColor = SlateBorder,
                focusedLabelColor = ElectricIndigoLight
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Supported Platforms & Integration Mechanisms:", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
                Text("• Instructure Canvas LMS (REST API v1)\n• Google Classroom (OAuth API)\n• Moodle (Web Services REST)\n• Blackboard Learn\n• Custom School Portals & Manual Document Import", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 20.sp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3: Platform Detection & Authentication
// ---------------------------------------------------------------------------
@Composable
private fun PlatformDetectionAndAuthStep(
    url: String,
    detectionResult: PlatformDetectionResult?,
    token: String,
    onTokenChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    isConnecting: Boolean,
    progressMessage: String,
    isSuccess: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "School Platform Detection",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Text(text = url, style = MaterialTheme.typography.bodySmall.copy(color = ElectricIndigoLight))
        }

        item {
            val result = detectionResult
            if (result != null) {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (result.supportsAutomatedSync) NeonEmerald else AmberWarning
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = result.platformInfo.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            StatusBadge(
                                text = if (result.supportsAutomatedSync) "OFFICIAL API" else "MANUAL / DIRECT",
                                color = if (result.supportsAutomatedSync) NeonEmerald else AmberWarning,
                                backgroundColor = if (result.supportsAutomatedSync) NeonEmeraldGlow else AmberWarning.copy(alpha = 0.15f)
                            )
                        }

                        if (result.limitationReason != null) {
                            Text(
                                text = result.limitationReason,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 20.sp)
                            )
                        } else {
                            Text(
                                text = "Ready to discover enrolled courses and academic material via ${result.platformInfo.statusDescription}.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }
        }

        if (detectionResult?.supportsAutomatedSync == true) {
            item {
                CyberCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Authentication Credentials", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Text(
                            text = "Provide your personal access token or login token generated from your school's settings menu.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )

                        OutlinedTextField(
                            value = token,
                            onValueChange = onTokenChange,
                            label = { Text("Personal Access Token / API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("school_auth_token_input")
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = onUsernameChange,
                            label = { Text("Student Username / Email (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (isConnecting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ElectricIndigoLight)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(progressMessage.ifBlank { "Connecting..." }, style = MaterialTheme.typography.bodySmall.copy(color = ElectricIndigoLight))
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Surface(
                    color = SlateElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = ElectricIndigoLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Direct File & Drive Ingestion Ready", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        }
                        Text(
                            text = "You can seamlessly import your lecture slides, readings, worksheets, and syllabi manually from device storage or Google Drive.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 4: Connect Google Drive
// ---------------------------------------------------------------------------
@Composable
private fun GoogleDriveConnectionStep(
    driveProfile: GoogleAccountProfile?,
    isConnecting: Boolean,
    onConnectClicked: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connect Google Drive",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "StudyLock organizes your course files dynamically into structured Drive folders (Study App/School Materials/[Course Name]).",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, textAlign = TextAlign.Center)
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (driveProfile?.isConnected == true) {
            Surface(
                color = NeonEmeraldGlow,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NeonEmerald),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Google Drive Connected", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Text(text = driveProfile.email, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                    }
                }
            }
        } else {
            Button(
                onClick = onConnectClicked,
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                border = BorderStroke(1.dp, ElectricIndigo),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("connect_drive_button")
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ElectricIndigoLight)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Connecting Drive...", color = TextPrimary)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = ElectricIndigoLight)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Connect Google Drive", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 5: Ready to Learn
// ---------------------------------------------------------------------------
@Composable
private fun ReadyStep(
    userSession: UserSession?,
    schoolConnected: Boolean,
    driveConnected: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(NeonEmeraldGlow),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "StudyLock is configured and ready. You can study decks, log focused deep-work time, and manage course materials anytime.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, textAlign = TextAlign.Center)
        )

        Spacer(modifier = Modifier.height(24.dp))

        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (userSession != null) "Google Account: ${userSession.displayName}" else "Local Guest Mode", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (schoolConnected) "School Integration Configured" else "Manual Course Ingestion Ready", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (driveConnected) "Google Drive Synced" else "Local Storage Ready", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
                }
            }
        }
    }
}
