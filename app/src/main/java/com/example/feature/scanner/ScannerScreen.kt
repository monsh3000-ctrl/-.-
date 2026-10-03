package com.example.feature.scanner

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.core.designsystem.CyberCard
import com.example.core.designsystem.GlowingButton
import com.example.core.designsystem.StatusBadge
import com.example.core.permissions.PermissionManager
import com.example.ui.theme.*

@Composable
fun ScannerScreen(
    onNavigateBack: () -> Unit,
    onProceedToReview: () -> Unit,
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(PermissionManager.checkCameraPermission(context))
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.analyzeUri(uri)
        }
    }

    val scannerState by viewModel.scannerState.collectAsState()
    val flashEnabled by viewModel.flashEnabled.collectAsState()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }

    LaunchedEffect(flashEnabled) {
        cameraControl?.enableTorch(flashEnabled)
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
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("scanner_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Text(
                    text = "STUDY STUDIO SCANNER",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                IconButton(
                    onClick = { viewModel.toggleFlash() },
                    enabled = hasCameraPermission,
                    modifier = Modifier.testTag("toggle_flash_button")
                ) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flash",
                        tint = if (flashEnabled) AmberWarning else TextSecondary
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = scannerState) {
                is ScannerUiState.Idle, is ScannerUiState.Analyzing, is ScannerUiState.Error -> {
                    if (hasCameraPermission) {
                        // Live CameraX View
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                }

                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }

                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    imageCapture = capture

                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                    try {
                                        cameraProvider.unbindAll()
                                        val cam = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            capture
                                        )
                                        cameraControl = cam.cameraControl
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Reticle / Scan Frame Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .border(2.dp, ElectricIndigoLight.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.15f))
                            )
                        }

                        // Bottom Controls overlay
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(ObsidianBg.copy(alpha = 0.85f))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (state is ScannerUiState.Analyzing) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    color = ElectricIndigo
                                )
                                Text(
                                    text = "Analyzing text with Google ML Kit...",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = ElectricIndigoLight)
                                )
                            } else if (state is ScannerUiState.Error) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall.copy(color = CoralRed),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Photo Gallery Import
                                OutlinedIconButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("pick_image_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = "Pick Image",
                                        tint = TextPrimary
                                    )
                                }

                                // Shutter Button
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(ElectricIndigo)
                                        .clickable {
                                            val capture = imageCapture
                                            if (capture != null) {
                                                capture.takePicture(
                                                    ContextCompat.getMainExecutor(context),
                                                    object : ImageCapture.OnImageCapturedCallback() {
                                                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                                            val bitmap = imageProxy.toBitmap()
                                                            imageProxy.close()
                                                            viewModel.analyzeBitmap(bitmap)
                                                        }

                                                        override fun onError(exception: ImageCaptureException) {
                                                            viewModel.analyzeBitmap(
                                                                android.graphics.Bitmap.createBitmap(
                                                                    400,
                                                                    400,
                                                                    android.graphics.Bitmap.Config.ARGB_8888
                                                                )
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        .testTag("shutter_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = "Capture Document",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                // Manual / Text Note
                                OutlinedIconButton(
                                    onClick = {
                                        viewModel.setExtractedTextDirectly(
                                            "Cellular respiration is the biochemical pathway converting glucose into ATP through glycolysis, the citric acid cycle, and oxidative phosphorylation."
                                        )
                                        onProceedToReview()
                                    },
                                    modifier = Modifier.testTag("manual_text_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = "Manual Input",
                                        tint = TextPrimary
                                    )
                                }
                            }
                        }
                    } else {
                        // Permission Denied / Not Granted UI
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = ElectricIndigo,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Camera Access Required",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "StudyLock needs camera access to scan textbook pages, notes, and study worksheets.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            GlowingButton(
                                text = "GRANT PERMISSION",
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose Photo from Device", color = TextPrimary)
                            }
                        }
                    }
                }

                is ScannerUiState.Success -> {
                    // Show detected OCR blocks with interactive selection checkboxes
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Detected Text Blocks (${state.blocks.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Row {
                                TextButton(onClick = { viewModel.selectAllBlocks(true) }) {
                                    Text("Select All", color = ElectricIndigoLight)
                                }
                                TextButton(onClick = { viewModel.selectAllBlocks(false) }) {
                                    Text("Clear", color = TextMuted)
                                }
                            }
                        }

                        Text(
                            text = "Tap any block to include or exclude it from the study material.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.blocks, key = { it.id }) { block ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            1.dp,
                                            if (block.isSelected) ElectricIndigo else CardBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.toggleBlockSelection(block.id) },
                                    color = if (block.isSelected) SlateElevated else SlateSurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = block.isSelected,
                                            onCheckedChange = { viewModel.toggleBlockSelection(block.id) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = ElectricIndigo,
                                                checkmarkColor = Color.White
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = block.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (block.isSelected) TextPrimary else TextMuted
                                            ),
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.resetScanner() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text("Retake", color = TextSecondary)
                            }

                            GlowingButton(
                                text = "CONFIRM SELECTION",
                                onClick = onProceedToReview,
                                modifier = Modifier
                                    .weight(2f)
                                    .height(50.dp),
                                enabled = state.blocks.any { it.isSelected },
                                testTag = "confirm_ocr_selection_button"
                            )
                        }
                    }
                }
            }
        }
    }
}
