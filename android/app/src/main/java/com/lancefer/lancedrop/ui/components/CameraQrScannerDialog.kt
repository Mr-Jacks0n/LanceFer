package com.lancefer.lancedrop.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.lancefer.lancedrop.ui.theme.ElectricBlue
import com.lancefer.lancedrop.utils.QrCodeAnalyzer
import com.lancefer.lancedrop.utils.SoundEffectManager
import java.util.concurrent.Executors

@Composable
fun CameraQrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Animation du laser de scan et du target-lock
    val infiniteTransition = rememberInfiniteTransition(label = "LaserTransition")
    val laserFraction by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserY"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (hasCameraPermission) {
                // Vue Caméra CameraX native
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(
                                        cameraExecutor,
                                        QrCodeAnalyzer { rawPayload ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            SoundEffectManager.playPairingTone()
                                            onQrScanned(rawPayload)
                                        }
                                    )
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                val cam = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                camera = cam
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay Viseur avec fenêtre découpée, réticules en coin et faisceau laser
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val boxSize = canvasWidth * 0.72f
                    val left = (canvasWidth - boxSize) / 2f
                    val top = (canvasHeight - boxSize) / 2f
                    val right = left + boxSize
                    val bottom = top + boxSize
                    val cornerLength = 32.dp.toPx()
                    val strokeWidth = 4.dp.toPx()

                    // Fond assombri semi-transparent
                    drawRect(color = Color(0x99000000))

                    // Découpe centrale transparente pour le viseur
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(24f, 24f),
                        blendMode = BlendMode.Clear
                    )

                    // Halo d'ambiance / Target-lock subtil
                    drawRoundRect(
                        color = ElectricBlue.copy(alpha = 0.15f * (0.8f + 0.2f * laserFraction)),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(24f, 24f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // 4 Réticules d'angles en L (AirDrop / Modern Viewfinder Style)
                    val reticleColor = ElectricBlue
                    val cap = StrokeCap.Round

                    // Coin Supérieur Gauche
                    drawLine(reticleColor, Offset(left, top + cornerLength), Offset(left, top), strokeWidth, cap = cap)
                    drawLine(reticleColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth, cap = cap)

                    // Coin Supérieur Droit
                    drawLine(reticleColor, Offset(right - cornerLength, top), Offset(right, top), strokeWidth, cap = cap)
                    drawLine(reticleColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth, cap = cap)

                    // Coin Inférieur Gauche
                    drawLine(reticleColor, Offset(left, bottom - cornerLength), Offset(left, bottom), strokeWidth, cap = cap)
                    drawLine(reticleColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth, cap = cap)

                    // Coin Inférieur Droit
                    drawLine(reticleColor, Offset(right - cornerLength, bottom), Offset(right, bottom), strokeWidth, cap = cap)
                    drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth, cap = cap)

                    // Faisceau laser animé avec halo
                    val laserY = top + (boxSize * laserFraction)
                    drawLine(
                        color = ElectricBlue.copy(alpha = 0.35f),
                        start = Offset(left + 16f, laserY),
                        end = Offset(right - 16f, laserY),
                        strokeWidth = 6.dp.toPx()
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(left + 24f, laserY),
                        end = Offset(right - 24f, laserY),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Autorisation de la caméra requise pour scanner le QR Code",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            // Barre Supérieure : Titre et Bouton Fermer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                }

                Text(
                    text = "Scanner le QR Code du PC",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Bouton Torche
                IconButton(
                    onClick = {
                        isTorchOn = !isTorchOn
                        camera?.cameraControl?.enableTorch(isTorchOn)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Lampe torche",
                        tint = if (isTorchOn) Color.Yellow else Color.White
                    )
                }
            }

            // Bandeau d'aide inférieur
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Alignez le QR Code affiché sur votre écran d'ordinateur dans le cadre",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
