package com.example.feature.screentime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.ui.theme.*

class StudyLockBlockerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_IS_FOCUS_LOCKED = "extra_is_focus_locked"
        const val EXTRA_FOCUS_SUBJECT = "extra_focus_subject"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "This application"
        val isFocusLocked = intent.getBooleanExtra(EXTRA_IS_FOCUS_LOCKED, false)
        val focusSubject = intent.getStringExtra(EXTRA_FOCUS_SUBJECT) ?: "Active Study"

        val appName = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(blockedPkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            blockedPkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

        setContent {
            MyApplicationTheme {
                BlockerScreen(
                    appName = appName,
                    isFocusLocked = isFocusLocked,
                    focusSubject = focusSubject,
                    onStartStudy = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("NAVIGATE_TO", if (isFocusLocked) "FOCUS_TIMER" else "STUDY_STUDIO")
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onDismiss = {
                        // Navigate back to user launcher home screen
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun BlockerScreen(
    appName: String,
    isFocusLocked: Boolean = false,
    focusSubject: String = "Active Study",
    onStartStudy: () -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(ObsidianBg, SlateSurface, ObsidianBg)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glowing Icon
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(if (isFocusLocked) ElectricIndigoGlow else CoralRedGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFocusLocked) Icons.Default.HourglassTop else Icons.Default.Lock,
                        contentDescription = if (isFocusLocked) "Focus Session Active" else "Screen Time Locked",
                        tint = if (isFocusLocked) ElectricIndigoLight else CoralRed,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isFocusLocked) "FOCUS SESSION IN PROGRESS" else "SCREEN TIME LOCKED",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isFocusLocked) {
                        "App switching to $appName is locked to protect your focus while studying $focusSubject."
                    } else {
                        "Your earned screen time has been exhausted for $appName."
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (isFocusLocked) ElectricIndigo.copy(alpha = 0.5f) else CardBorder
                ) {
                    Text(
                        text = if (isFocusLocked) "DISTRACTION SHIELD ENGAGED" else "THE STUDYLOCK CONTRACT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ElectricIndigoLight,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isFocusLocked) {
                            "To switch apps, either complete your study session timer or open StudyLock to pause it."
                        } else {
                            "Mindless scrolling is locked. Spend 5 to 15 minutes reviewing your material to unlock screen time."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                GlowingButton(
                    text = if (isFocusLocked) "RETURN TO FOCUS TIMER" else "START STUDY SESSION",
                    onClick = onStartStudy,
                    icon = if (isFocusLocked) Icons.Default.Timer else Icons.Default.School,
                    color = ElectricIndigo,
                    containerColor = ElectricIndigo,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Close Application")
                }
            }
        }
    }
}
