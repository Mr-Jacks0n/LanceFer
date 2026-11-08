package com.lancefer.lancedrop.ui.remote

import android.content.Context
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.lancefer.lancedrop.data.remote.RemoteFileItem
import com.lancefer.lancedrop.ui.theme.LanceDropBlue
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Formate une durée en millisecondes au format mm:ss ou hh:mm:ss.
 */
fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Crée et configure un lecteur ExoPlayer avec un LoadControl optimisé pour le streaming local
 * à très faible latence (<500 ms) et support des requêtes HTTP Range RFC 7233.
 */
@OptIn(UnstableApi::class)
fun createStreamingExoPlayer(
    context: Context,
    streamUrl: String,
    token: String = ""
): ExoPlayer {
    // LoadControl à faible tamponnage pour démarrage instantané (<500 ms)
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 500,
            /* maxBufferMs = */ 2500,
            /* bufferForPlaybackMs = */ 250,
            /* bufferForPlaybackAfterRebufferMs = */ 500
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    // DataSource HTTP avec en-têtes d'authentification et gestion RFC 7233 Range
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(8000)
        .setReadTimeoutMs(15000)
        .setAllowCrossProtocolRedirects(true)

    if (token.isNotBlank()) {
        httpDataSourceFactory.setDefaultRequestProperties(
            mapOf(
                "Authorization" to "Bearer $token",
                "X-Session-Token" to token,
                "X-Session-PIN" to token
            )
        )
    }

    val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

    val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .build()

    val mediaItem = MediaItem.fromUri(streamUrl)
    player.setMediaItem(mediaItem)
    player.prepare()
    player.playWhenReady = true

    return player
}

/**
 * Écran de lecture vidéo et audio en streaming direct via ExoPlayer Media3.
 */
@Composable
fun RemoteStreamingPlayerScreen(
    item: RemoteFileItem,
    viewModel: RemoteExplorerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val streamUrl = remember(item) { viewModel.buildStreamUrl(item) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember(streamUrl) {
        createStreamingExoPlayer(context, streamUrl, uiState.token)
    }

    // Écoute des états du lecteur ExoPlayer
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Boucle de rafraîchissement périodique de la position
    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (true) {
            if (!isUserScrubbing && exoPlayer.playbackState == Player.STATE_READY) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0L) durationMs = d
            }
            delay(200)
        }
    }

    // Masquage automatique des contrôles après 4 secondes d'inactivité
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        // Surface de rendu vidéo / affichage audio
        if (item.isVideo) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            )
        } else {
            // Affichage stylisé pour les pistes audio
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(LanceDropBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Audio",
                        tint = LanceDropBlue,
                        modifier = Modifier.size(80.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.formattedSize,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        // Indicateur de mise en mémoire tampon (buffering)
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center),
                color = LanceDropBlue,
                strokeWidth = 4.dp
            )
        }

        // Contrôles superposés (Barre haute, lecture/pause centrale, barre de progression)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
            ) {
                // Barre Supérieure : Bouton retour, titre du fichier, bouton de téléchargement
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Streaming direct PC • ${item.formattedSize}",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.downloadFile(context, item) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF0B4CA5), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Télécharger",
                            tint = Color.White
                        )
                    }
                }

                // Boutons centraux : Saut -10s, Lecture/Pause, Saut +10s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Saut -10 secondes
                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Reculer de 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Bouton principal Lecture / Pause
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF00D2FF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = Color(0xFF070C14),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Saut +10 secondes
                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(durationMs)
                            exoPlayer.seekTo(newPos)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Avancer de 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Barre Inférieure : Scrubber / Slider temporel et labels de temps dans un panneau flottant
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC161F30)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val displayPos = if (isUserScrubbing) scrubPositionMs.toLong() else currentPositionMs
                            Text(
                                text = formatDurationMs(displayPos),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatDurationMs(durationMs),
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Slider(
                            value = if (isUserScrubbing) {
                                scrubPositionMs
                            } else {
                                currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f))
                            },
                            onValueChange = { newValue ->
                                isUserScrubbing = true
                                scrubPositionMs = newValue
                            },
                            onValueChangeFinished = {
                                isUserScrubbing = false
                                exoPlayer.seekTo(scrubPositionMs.toLong())
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00D2FF),
                                activeTrackColor = Color(0xFF00D2FF),
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
