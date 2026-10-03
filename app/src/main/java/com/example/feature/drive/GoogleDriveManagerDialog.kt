package com.example.feature.drive

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.StatusBadge
import com.example.core.drive.DriveNode
import com.example.core.drive.GoogleAccountProfile
import com.example.core.drive.GoogleDriveAuthManager
import com.example.core.drive.GoogleDriveFolderManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GoogleDriveManagerDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { GoogleDriveAuthManager(context) }
    val folderManager = remember { GoogleDriveFolderManager(context) }

    var profile by remember { mutableStateOf<GoogleAccountProfile?>(null) }
    val driveNodes by folderManager.driveNodes.collectAsState()

    var showAccountInputDialog by remember { mutableStateOf(false) }
    var showOAuthConsentDialog by remember { mutableStateOf(false) }
    var inputEmail by remember { mutableStateOf("") }
    var inputDisplayName by remember { mutableStateOf("") }

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedParentSubjectId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        profile = authManager.getCurrentProfile()
    }

    if (showOAuthConsentDialog) {
        GoogleDriveOAuthConsentDialog(
            initialEmail = profile?.email ?: "",
            onDismissRequest = { showOAuthConsentDialog = false },
            onAuthorizedSuccess = { updated ->
                profile = updated
                coroutineScope.launch {
                    folderManager.sortExistingFilesAndFolders()
                }
            }
        )
    }

    // Modal to type custom Google account if user wants to enter student email directly
    if (showAccountInputDialog) {
        AlertDialog(
            onDismissRequest = { showAccountInputDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = ElectricIndigoLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Link Google Drive Account", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter your school Google account or personal Gmail address to synchronize the StudyLock folder hierarchy.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Google Account Email") },
                        placeholder = { Text("student@school.edu or name@gmail.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = ElectricIndigoLight) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("drive_email_input")
                    )

                    OutlinedTextField(
                        value = inputDisplayName,
                        onValueChange = { inputDisplayName = it },
                        label = { Text("Student Name (Optional)") },
                        placeholder = { Text("e.g. Alex Chen") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("drive_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputEmail.isNotBlank()) {
                            coroutineScope.launch {
                                val updated = authManager.linkCustomGoogleAccount(inputEmail, inputDisplayName)
                                profile = updated
                                folderManager.refreshHierarchy()
                                showAccountInputDialog = false
                                Toast.makeText(context, "Linked Google Drive: ${updated.email}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("Link Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountInputDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SlateSurface
        )
    }

    // Add Folder Dialog
    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("Create Folder in StudyLock", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Create a new Subject or Unit folder inside your Google Drive StudyLock directory.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name") },
                        placeholder = { Text("e.g. Physics or Unit 1: Kinematics") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            coroutineScope.launch {
                                if (selectedParentSubjectId != null) {
                                    folderManager.createUnitFolder(selectedParentSubjectId!!, newFolderName)
                                } else {
                                    folderManager.createSubjectFolder(newFolderName)
                                }
                                newFolderName = ""
                                selectedParentSubjectId = null
                                showAddFolderDialog = false
                                Toast.makeText(context, "Created folder in Google Drive!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SlateSurface
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp)),
            color = ObsidianBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricIndigoGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = ElectricIndigoLight)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Google Drive Manager",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            Text(
                                text = "StudyLock Folder Architecture",
                                style = MaterialTheme.typography.labelSmall.copy(color = ElectricIndigoLight)
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Account Connection Card
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (profile?.isConnected == true) NeonEmerald else ElectricIndigo,
                    backgroundColor = SlateSurface
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (profile?.isConnected == true) (profile?.displayName ?: "Google User") else "No Account Linked",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = if (profile?.isConnected == true) (profile?.email ?: "") else "Link your Google account to sync folders",
                                    style = MaterialTheme.typography.labelSmall.copy(color = if (profile?.isConnected == true) NeonEmerald else TextMuted)
                                )
                            }

                            if (profile?.isConnected == true) {
                                StatusBadge(text = "LINKED", color = NeonEmerald)
                            } else {
                                StatusBadge(text = "UNLINKED", color = TextMuted)
                            }
                        }

                        HorizontalDivider(color = SlateBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    showOAuthConsentDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (profile?.isConnected == true) "Switch Account" else "Link with Google",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (profile?.isConnected == true) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            authManager.disconnect()
                                            profile = authManager.getCurrentProfile()
                                            Toast.makeText(context, "Google Drive disconnected", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(0.7f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("Unlink", fontSize = 12.sp, color = CoralRed)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Folder Structure Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FOLDER STRUCTURE ON DRIVE",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    folderManager.autoCreateAndSortDriveStructure(profile?.email ?: "student@gmail.com")
                                    Toast.makeText(context, "Auto-sorted all folders and files!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Sort", color = NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = {
                                selectedParentSubjectId = null
                                showAddFolderDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = ElectricIndigoLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ New Folder", color = ElectricIndigoLight, fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = { folderManager.openFolderInGoogleDrive(context) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open in Drive", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Interactive Folder Tree
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateSurface)
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(driveNodes, key = { it.id }) { node ->
                        DriveNodeRow(
                            node = node,
                            onDelete = {
                                coroutineScope.launch {
                                    folderManager.deleteItem(node.id)
                                    Toast.makeText(context, "Deleted from Google Drive structure", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpen = {
                                folderManager.openFolderInGoogleDrive(context, node.id)
                            },
                            onAddSubfolder = {
                                selectedParentSubjectId = node.id
                                showAddFolderDialog = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Done Button
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text("DONE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DriveNodeRow(
    node: DriveNode,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onAddSubfolder: () -> Unit
) {
    val depth = when {
        node.parentId == null -> 0
        node.parentId == "root_studylock" -> 1
        node.parentId == "folder_school_materials" -> 2
        node.isFolder -> 3
        else -> 4
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpen() }
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .padding(start = (depth * 14).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (node.isFolder) {
                    if (depth == 0) "☁️" else "📁"
                } else {
                    if (node.mimeType.contains("pdf")) "📄" else "📝"
                },
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (node.isFolder) FontWeight.Bold else FontWeight.Normal,
                    color = if (node.isFolder) TextPrimary else ElectricIndigoLight
                )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (node.isFolder && depth in 2..3) {
                IconButton(
                    onClick = onAddSubfolder,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add subfolder", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            if (depth >= 3) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
