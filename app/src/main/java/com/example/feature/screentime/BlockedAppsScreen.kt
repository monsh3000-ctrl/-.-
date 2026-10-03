package com.example.feature.screentime

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.core.permissions.PermissionManager
import com.example.ui.theme.*

@Composable
fun BlockedAppsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScreenTimeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    val hasAccessibility = remember(state) { PermissionManager.checkAccessibilityPermission(context) }
    val hasUsage = remember(state) { PermissionManager.checkUsageStatsPermission(context) }
    val hasOverlay = remember(state) { PermissionManager.checkOverlayPermission(context) }

    if (showAddDialog) {
        var appName by remember { mutableStateOf("") }
        var pkgName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom App to Block", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text("App Name (e.g. Discord)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pkgName,
                        onValueChange = { pkgName = it },
                        label = { Text("Package Name (e.g. com.discord)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (appName.isNotBlank() && pkgName.isNotBlank()) {
                            viewModel.addCustomBlockedApp(pkgName.trim(), appName.trim())
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("Add App")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
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
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("blocked_apps_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                Text(
                    text = "PROTECTED APPS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.refreshData() }, modifier = Modifier.testTag("sync_blocked_apps_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricIndigoLight)
                    }
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_custom_app_button")) {
                        Icon(Icons.Default.Add, contentDescription = "Add App", tint = ElectricIndigoLight)
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
            // Global Protection Toggle Card
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (state.isGlobalBlockingEnabled) ElectricIndigo else SlateBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Screen Time Blocker",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            Text(
                                text = "Enforce earned time on selected distracting apps",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Switch(
                            checked = state.isGlobalBlockingEnabled,
                            onCheckedChange = { viewModel.setGlobalBlocking(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElectricIndigo
                            )
                        )
                    }
                }
            }

            // Permission Verification Banner
            item {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CardBorder
                ) {
                    Text(
                        text = "ENFORCEMENT PERMISSIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricIndigoLight,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Accessibility Service", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Instantly locks foreground apps", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                        }
                        if (hasAccessibility) {
                            StatusBadge(text = "Active", color = NeonEmerald, backgroundColor = NeonEmeraldGlow)
                        } else {
                            Button(
                                onClick = { PermissionManager.openAccessibilitySettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Enable", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Usage Access", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Monitors foreground app packages", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                        }
                        if (hasUsage) {
                            StatusBadge(text = "Granted", color = NeonEmerald, backgroundColor = NeonEmeraldGlow)
                        } else {
                            Button(
                                onClick = { PermissionManager.openUsageAccessSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Grant", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Test Blocker Experience Button
            item {
                OutlinedButton(
                    onClick = {
                        val blockerIntent = Intent(context, StudyLockBlockerActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(StudyLockBlockerActivity.EXTRA_BLOCKED_PACKAGE, "com.instagram.android")
                        }
                        context.startActivity(blockerIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Preview Screen Time Blocker UI", color = TextPrimary)
                }
            }

            // Blocked Apps List Header
            item {
                Text(
                    text = "PROTECTED APPS (${state.blockedApps.count { it.isBlocked }} ACTIVE)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Blocked Apps List
            items(state.blockedApps, key = { it.packageName }) { app ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    color = SlateSurface
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val usageSecs = state.appUsageTodaySeconds[app.packageName] ?: 0L
                        val usageMins = usageSecs / 60
                        val usageSecsRemainder = usageSecs % 60

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )
                                if (usageSecs > 0) {
                                    Text(
                                        text = " · ${usageMins}m ${usageSecsRemainder}s today",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (app.isBlocked) CoralRed else ElectricIndigoLight,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = app.isBlocked,
                            onCheckedChange = { isChecked ->
                                viewModel.setAppBlocked(app.packageName, isChecked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = if (app.isBlocked) ElectricIndigo else SlateElevated
                            )
                        )
                    }
                }
            }
        }
    }
}
