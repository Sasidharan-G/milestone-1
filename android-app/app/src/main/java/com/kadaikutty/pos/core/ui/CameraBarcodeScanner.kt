package com.kadaikutty.pos.core.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

private fun decodeBarcode(reader: MultiFormatReader, image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val width = image.width
    val height = image.height
    val sourceBytes = ByteArray(width * height)
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    for (row in 0 until height) {
        for (column in 0 until width) {
            sourceBytes[row * width + column] = buffer.get(row * rowStride + column * pixelStride)
        }
    }
    val (rotated, rotatedWidth, rotatedHeight) = when (image.imageInfo.rotationDegrees) {
        90 -> Triple(ByteArray(width * height).also { output ->
            for (row in 0 until height) for (column in 0 until width) output[column * height + (height - row - 1)] = sourceBytes[row * width + column]
        }, height, width)
        180 -> Triple(sourceBytes.reversedArray(), width, height)
        270 -> Triple(ByteArray(width * height).also { output ->
            for (row in 0 until height) for (column in 0 until width) output[(width - column - 1) * height + row] = sourceBytes[row * width + column]
        }, height, width)
        else -> Triple(sourceBytes, width, height)
    }
    val luminance = PlanarYUVLuminanceSource(rotated, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false)
    return runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance))).text }.getOrNull()
}

@Composable
fun CameraBarcodeScannerDialog(
    title: String = "Scan Barcode",
    continuousScan: Boolean = false,
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasPermission) {
                CameraPreviewWithAnalyzer(
                    continuousScan = continuousScan,
                    onBarcodeScanned = { barcode ->
                        triggerBeepSound()
                        onBarcodeScanned(barcode)
                        if (!continuousScan) {
                            onDismiss()
                        }
                    },
                    onDismiss = onDismiss,
                    title = title
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera Permission Required",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Camera access is needed to scan product barcodes for instant billing.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreviewWithAnalyzer(
    continuousScan: Boolean,
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var lastScannedBarcode by remember { mutableStateOf("") }
    var lastScanTime by remember { mutableLongStateOf(0L) }

    val scanner = remember {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.QR_CODE,
            )))
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanner.reset()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            val rawVal = decodeBarcode(scanner, imageProxy)?.trim()
                            if (!rawVal.isNullOrBlank()) {
                                val now = System.currentTimeMillis()
                                val cooldown = if (rawVal != lastScannedBarcode) 600L else 1500L
                                if (now - lastScanTime > cooldown) {
                                    lastScanTime = now
                                    lastScannedBarcode = rawVal
                                    previewView.post { onBarcodeScanned(rawVal) }
                                }
                            }
                        } finally {
                            scanner.reset()
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with scanning frame & animated laser beam
        ScannerOverlay()

        // Top App Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    val next = !isTorchOn
                    isTorchOn = next
                    cameraControl?.enableTorch(next)
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    tint = if (isTorchOn) Color.Yellow else Color.White
                )
            }
        }

        // Bottom Instruction Card
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (continuousScan) "Align barcode in frame • Multiple items supported" else "Point camera at product barcode",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (lastScannedBarcode.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scanned: $lastScannedBarcode",
                        color = Color(0xFF4CAF50),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val boxWidth = width * 0.78f
        val boxHeight = height * 0.32f
        val left = (width - boxWidth) / 2f
        val top = (height - boxHeight) / 2f

        // Draw dim background outside scanner frame
        drawRect(color = Color.Black.copy(alpha = 0.55f))

        // Cut transparent hole
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(24f, 24f),
            blendMode = BlendMode.Clear
        )

        // Draw corner brackets / frame
        drawRoundRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 4f)
        )

        // Animated laser line
        val laserY = top + (boxHeight * laserPosition)
        drawLine(
            color = Color(0xFF00E676),
            start = Offset(left + 16f, laserY),
            end = Offset(left + boxWidth - 16f, laserY),
            strokeWidth = 3.5f
        )
    }
}

private fun triggerBeepSound() {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    } catch (_: Exception) {
    }
}
