package com.lancefer.lancedrop.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.OsType
import com.lancefer.lancedrop.ui.theme.*

/**
 * Modern tactile discovery radar inspired by Apple AirDrop & Google Quick Share.
 * Features concentric animated pulse rings, center device anchor, and circular peer avatars
 * with OS branding badges and haptic tap feedback.
 */
@Composable
fun PeerRadarView(
    modifier: Modifier = Modifier,
    discoveredPeers: List<DiscoveredPeer>,
    onSelectPeer: (DiscoveredPeer) -> Unit,
    localDeviceName: String = "Ce Téléphone"
) {
    val haptic = LocalHapticFeedback.current

    // Concentric pulse animation (3 staggered expanding waves)
    val infiniteTransition = rememberInfiniteTransition(label = "RadarWaves")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseFraction"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            contentAlignment = Alignment.Center
        ) {
            // Animated concentric wave rings
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = minOf(size.width, size.height) * 0.48f

                // Draw 3 staggered expanding rings
                for (i in 0..2) {
                    val ringFraction = (pulseFraction + (i * 0.333f)) % 1.0f
                    val currentRadius = maxRadius * ringFraction
                    val alpha = (1.0f - ringFraction).coerceIn(0f, 0.45f)
                    drawCircle(
                        color = ElectricBlue.copy(alpha = alpha),
                        radius = currentRadius,
                        center = centerOffset,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Subtle static guideline ring
                drawCircle(
                    color = ElectricBlue.copy(alpha = 0.12f),
                    radius = maxRadius * 0.75f,
                    center = centerOffset,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Central Device Anchor (Self)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(ElectricBlue.copy(alpha = 0.35f), ElectricBlue.copy(alpha = 0.08f))
                            )
                        )
                        .border(2.dp, ElectricBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Mon Appareil",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = localDeviceName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val filteredPeers = remember(discoveredPeers, localDeviceName) {
            discoveredPeers.filterNot { peer ->
                com.lancefer.lancedrop.utils.NetworkUtils.isSelfIp(peer.ipAddress) ||
                    peer.name.startsWith("LanceDrop-Android") ||
                    peer.id.startsWith("LanceDrop-Android") ||
                    peer.name.equals(localDeviceName, ignoreCase = true)
            }
        }

        // Peer Avatars Section
        if (filteredPeers.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Recherche d'appareils LanceDrop à proximité...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                filteredPeers.forEach { peer ->
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    ) {
                        PeerAvatarBubble(
                            peer = peer,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectPeer(peer)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PeerAvatarBubble(
    peer: DiscoveredPeer,
    onClick: () -> Unit
) {
    val (osIcon, osBadgeBg) = when (peer.osType) {
        OsType.WINDOWS -> Pair(Icons.Default.LaptopWindows, Color(0xFF0078D4))
        OsType.ANDROID -> Pair(Icons.Default.Android, Color(0xFF3DDC84))
        OsType.MACOS -> Pair(Icons.Default.LaptopMac, Color(0xFF333333))
        OsType.LINUX -> Pair(Icons.Default.Terminal, Color(0xFFFCC624))
        else -> Pair(Icons.Default.Devices, MaterialTheme.colorScheme.primary)
    }

    val initials = peer.name.split(" ", "-", "_")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "PC" }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            // Main Avatar Circle
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // OS Badge Pinned at bottom right
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(osBadgeBg)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = osIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = peer.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LanceDropGreen)
            )
            Text(
                text = "Prêt",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = LanceDropGreen
            )
        }
    }
}
