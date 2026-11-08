package com.lancefer.lancedrop.ui.screen

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.*
import com.lancefer.lancedrop.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    fileName: String = "Fichier",
    fileUri: Uri? = null,
    filePath: String? = null,
    fileSize: String = "",
    fileDate: String = "",
    onNavigateBack: () -> Unit,
    onSendToPc: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val extension = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase(Locale.ROOT) else ""
    val isImage = extension in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    val isAudio = extension in listOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
    val isVideo = extension in listOf("mp4", "mkv", "mov", "webm", "avi", "3gp")
    val isText = extension in listOf("txt", "json", "csv", "log", "xml", "html", "css", "js", "ts", "kt", "py", "md", "yaml", "yml", "sh", "bat")
    val isPdf = extension == "pdf"
    val isApk = extension == "apk"

    // Gestion du chargement de l'image réelle avec sous-échantillonnage pour économiser la RAM (< 4 Go)
    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var imageResolution by remember { mutableStateOf<String?>(null) }
    var isImageLoading by remember { mutableStateOf(isImage) }

    LaunchedEffect(fileUri, filePath) {
        if (isImage) {
            withContext(Dispatchers.IO) {
                var pfd: android.os.ParcelFileDescriptor? = null
                try {
                    pfd = if (fileUri != null) {
                        try { context.contentResolver.openFileDescriptor(fileUri, "r") } catch (_: Exception) { null }
                    } else null

                    val fd = pfd?.fileDescriptor ?: if (!filePath.isNullOrEmpty()) {
                        try { java.io.FileInputStream(java.io.File(filePath)).fd } catch (_: Exception) { null }
                    } else null

                    if (fd != null) {
                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFileDescriptor(fd, null, boundsOpts)
                        val w = boundsOpts.outWidth
                        val h = boundsOpts.outHeight
                        if (w > 0 && h > 0) {
                            imageResolution = "$w × $h px"
                        }

                        var sampleSize = 1
                        while (boundsOpts.outWidth / sampleSize > 1200 || boundsOpts.outHeight / sampleSize > 1200) {
                            sampleSize *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        val bmp = BitmapFactory.decodeFileDescriptor(fd, null, decodeOpts)
                        if (bmp != null) {
                            imageBitmap = bmp.asImageBitmap()
                        }
                    } else {
                        // Repli flux séparés : pas d'allocation massive readBytes()
                        val s1 = if (fileUri != null) context.contentResolver.openInputStream(fileUri) else if (!filePath.isNullOrEmpty()) FileInputStream(File(filePath)) else null
                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        s1?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

                        val w = boundsOpts.outWidth
                        val h = boundsOpts.outHeight
                        if (w > 0 && h > 0) imageResolution = "$w × $h px"

                        var sampleSize = 1
                        while (boundsOpts.outWidth / sampleSize > 1200 || boundsOpts.outHeight / sampleSize > 1200) {
                            sampleSize *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        val s2 = if (fileUri != null) context.contentResolver.openInputStream(fileUri) else if (!filePath.isNullOrEmpty()) FileInputStream(File(filePath)) else null
                        s2?.use {
                            val bmp = BitmapFactory.decodeStream(it, null, decodeOpts)
                            if (bmp != null) imageBitmap = bmp.asImageBitmap()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { pfd?.close() } catch (_: Exception) {}
                    isImageLoading = false
                }
            }
        }
    }

    // Gestion du lecteur audio réel
    var isPlaying by remember { mutableStateOf(false) }
    var currentAudioMs by remember { mutableIntStateOf(0) }
    var totalAudioDurationMs by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(fileUri, filePath) {
        if (isAudio) {
            val mp = MediaPlayer()
            try {
                if (fileUri != null) {
                    mp.setDataSource(context, fileUri)
                } else if (!filePath.isNullOrEmpty()) {
                    mp.setDataSource(filePath)
                }
                mp.prepare()
                totalAudioDurationMs = mp.duration
                mp.setOnCompletionListener {
                    isPlaying = false
                    currentAudioMs = 0
                }
                mediaPlayer = mp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    currentAudioMs = mp.currentPosition
                } else {
                    isPlaying = false
                }
            }
            delay(250)
        }
    }

    // Gestion de la lecture du contenu texte réel
    var textSnippet by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fileUri, filePath) {
        if (isText) {
            withContext(Dispatchers.IO) {
                try {
                    val stream = when {
                        fileUri != null -> context.contentResolver.openInputStream(fileUri)
                        !filePath.isNullOrEmpty() -> FileInputStream(File(filePath))
                        else -> null
                    }
                    stream?.bufferedReader()?.use { reader ->
                        val lines = reader.lineSequence().take(150).joinToString("\n")
                        textSnippet = lines
                    }
                } catch (e: Exception) {
                    textSnippet = "Impossible de charger le contenu texte : ${e.message}"
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Supprimer le fichier ?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Voulez-vous vraiment supprimer \"$fileName\" définitivement de cet appareil ?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        try {
                            if (!filePath.isNullOrEmpty()) {
                                File(filePath).delete()
                            } else if (fileUri != null) {
                                context.contentResolver.delete(fileUri, null, null)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LanceDropRed)
                ) {
                    Text("Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subText = when {
                            imageResolution != null -> "$fileSize • $imageResolution"
                            fileSize.isNotEmpty() -> fileSize
                            else -> "Fichier local"
                        }
                        Text(
                            text = subText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { FileHelper.openFile(context, fileUri, filePath) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Ouvrir", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // ─────────────────────────────────────────────────────────────
                // ZONE DE PRÉVISUALISATION DYNAMIQUE SELON LE TYPE
                // ─────────────────────────────────────────────────────────────
                when {
                    // Visionneuse d'Image Réelle
                    isImage -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageBitmap != null) {
                                Image(
                                    bitmap = imageBitmap!!,
                                    contentDescription = fileName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else if (isImageLoading) {
                                CircularProgressIndicator(color = LanceDropBlue)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.BrokenImage, contentDescription = null, tint = LanceDropRed, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Aperçu non disponible", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Lecteur Audio Intégré
                    isAudio -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(LanceDropGreenLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = LanceDropGreen, modifier = Modifier.size(36.dp))
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                val curSec = currentAudioMs / 1000
                                val totSec = if (totalAudioDurationMs > 0) totalAudioDurationMs / 1000 else 0
                                val progress = if (totalAudioDurationMs > 0) currentAudioMs.toFloat() / totalAudioDurationMs.toFloat() else 0f

                                Slider(
                                    value = progress,
                                    onValueChange = { frac ->
                                        val targetMs = (frac * totalAudioDurationMs).toInt()
                                        mediaPlayer?.seekTo(targetMs)
                                        currentAudioMs = targetMs
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = LanceDropGreen,
                                        activeTrackColor = LanceDropGreen
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "%02d:%02d".format(curSec / 60, curSec % 60),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "%02d:%02d".format(totSec / 60, totSec % 60),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                IconButton(
                                    onClick = {
                                        mediaPlayer?.let { mp ->
                                            if (mp.isPlaying) {
                                                mp.pause()
                                                isPlaying = false
                                            } else {
                                                mp.start()
                                                isPlaying = true
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(LanceDropGreen)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Lecture",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Lecteur Texte / Code Intégré
                    isText -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(20.dp))
                                    Text("Aperçu du contenu texte", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(12.dp)
                                ) {
                                    val scroll = rememberScrollState()
                                    Text(
                                        text = textSnippet ?: "Lecture du contenu en cours...",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.verticalScroll(scroll)
                                    )
                                }
                            }
                        }
                    }

                    // Vidéos, PDF, APK et Documents
                    else -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val (icon, iconColor, bgColor, catLabel) = when {
                                    isVideo -> Quad(Icons.Default.Movie, LanceDropPurple, LanceDropPurpleLight, "Fichier Vidéo")
                                    isPdf -> Quad(Icons.Default.PictureAsPdf, LanceDropRed, LanceDropRedLight, "Document PDF")
                                    isApk -> Quad(Icons.Default.Android, LanceDropGreen, LanceDropGreenLight, "Package Android APK")
                                    else -> Quad(Icons.Default.InsertDriveFile, LanceDropOrange, LanceDropOrangeLight, "Document")
                                }

                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(bgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(34.dp))
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = fileName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "$catLabel • $fileSize",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = { FileHelper.openFile(context, fileUri, filePath) },
                                    colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(if (isApk) "Installer l'application" else "Ouvrir avec le lecteur système", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Carte Métadonnées et Détails du fichier
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Informations sur le fichier",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        val displayLocation = when {
                            !filePath.isNullOrEmpty() -> filePath
                            fileUri != null -> fileUri.toString()
                            else -> "/Stockage/LanceDrop/"
                        }

                        DetailItemRow("Emplacement", displayLocation)
                        if (fileDate.isNotEmpty()) {
                            DetailItemRow("Date", fileDate)
                        }
                        DetailItemRow("Format / Extension", extension.uppercase(Locale.ROOT).ifEmpty { "Inconnu" })
                        DetailItemRow("Taille", fileSize.ifEmpty { "Calcul en cours" })
                        if (imageResolution != null) {
                            DetailItemRow("Résolution", imageResolution!!)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Barre d'actions inférieure
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bouton d'action principal : Envoyer au PC
                Button(
                    onClick = onSendToPc,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                        Text(
                            text = "Envoyer cet élément au PC",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Rangée d'icônes d'actions secondaires
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionIconButton(
                        icon = Icons.Default.Share,
                        label = "Partager",
                        onClick = { FileHelper.shareFile(context, fileUri, filePath) }
                    )
                    ActionIconButton(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = "Ouvrir avec",
                        onClick = { FileHelper.openFile(context, fileUri, filePath) }
                    )
                    ActionIconButton(
                        icon = Icons.Default.DeleteOutline,
                        label = "Supprimer",
                        iconColor = LanceDropRed,
                        onClick = { showDeleteDialog = true }
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun DetailItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    label: String,
    iconColor: Color = LanceDropBlue,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
