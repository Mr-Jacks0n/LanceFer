package com.lancefer.lancedrop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.components.PeerRadarView
import com.lancefer.lancedrop.ui.theme.*
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.OsType
import com.lancefer.lancedrop.model.TransferHistoryItem
import com.lancefer.lancedrop.utils.FileHelper
import androidx.compose.ui.platform.LocalContext

@Composable
fun HomeScreen(
    recentTransfers: List<TransferHistoryItem> = emptyList(),
    interruptedTransfer: TransferHistoryItem? = null,
    onResumeInterrupted: (TransferHistoryItem) -> Unit = {},
    onDismissInterrupted: (TransferHistoryItem) -> Unit = {},
    discoveredPeers: List<DiscoveredPeer> = emptyList(),
    onSelectPeer: (DiscoveredPeer) -> Unit = {},
    onAddDevice: () -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    onNavigateToFiles: (category: String?) -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToSendMode: () -> Unit,
    onNavigateToReceiveMode: () -> Unit,
    onNavigateToClipboard: () -> Unit,
    onNavigateToSettings: () -> Unit,
    deviceInfo: com.lancefer.lancedrop.model.DeviceInfo? = null,
    localIpAddress: String = "127.0.0.1",
    wifiSSID: String = "Wi-Fi Connecté"
) {
    val context = LocalContext.current

    // Données réelles authentiques de l'appareil et du stockage physique
    val effectiveDeviceInfo = deviceInfo ?: remember { com.lancefer.lancedrop.utils.DeviceMetadataHelper.getDeviceInfo(context) }
    val storageMetrics = remember { com.lancefer.lancedrop.utils.DeviceMetadataHelper.getStorageMetrics() }
    val storageText = "${storageMetrics.usedFormatted} / ${storageMetrics.totalFormatted}"
    val storageProgress = storageMetrics.usedPercentage
    val deviceDisplayName = effectiveDeviceInfo.fullDisplayName
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onBackground)
                }

                Text(
                    text = "LanceDrop",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenQrScanner) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner QR", tint = LanceDropBlue)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // BANDEAU PROACTIF DE REPRISE APRÈS CRASH / INTERRUPTION
            // ─────────────────────────────────────────────────────────────
            if (interruptedTransfer != null) {
                item {
                    val sizeFormatted = com.lancefer.lancedrop.utils.FileHelper.formatFileSize(interruptedTransfer.fileSize)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF59E0B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Transfert interrompu détecté",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(
                                        text = "${interruptedTransfer.fileName} ($sizeFormatted) • ${interruptedTransfer.peerName}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF78350F)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onResumeInterrupted(interruptedTransfer) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Reprendre immédiatement", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { onDismissInterrupted(interruptedTransfer) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Annuler", fontSize = 12.sp, color = Color(0xFF78350F))
                                }
                            }
                        }
                    }
                }
            }
            // ─────────────────────────────────────────────────────────────
            // ─────────────────────────────────────────────────────────────
            // 1. SECTION DÉDIÉE : MON APPAREIL (NON-INTERACTIVE & WCAG AAA)
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // En-tête : Badge "Mon appareil" + Statut de disponibilité
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "MON APPAREIL",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = deviceDisplayName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Statut de disponibilité en ligne (WCAG AAA >= 7.0:1)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF14532D) else Color(0xFFDCFCE7),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF166534) else Color(0xFF86EFAC)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (androidx.compose.foundation.isSystemInDarkTheme()) LanceDropSuccessIconDark else LanceDropSuccessIconLight)
                                    )
                                    Text(
                                        text = "En ligne",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF86EFAC) else LanceDropSuccessTextLightAAA
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Grille d'informations système et réseau (OS, IP, Wi-Fi)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Info OS
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        tint = Color(0xFF3DDC84),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Système",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Android ${Build.VERSION.RELEASE}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Info IP Locale Active
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Adresse IP LAN",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = localIpAddress.ifBlank { "127.0.0.1" },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. RADAR DE DISPONIBILITÉ IMMÉDIATE (AIRDROP / QUICK SHARE STYLE)
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    PeerRadarView(
                        discoveredPeers = discoveredPeers,
                        onSelectPeer = onSelectPeer,
                        localDeviceName = deviceDisplayName
                    )
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. BARRE D'ACTIONS ÉPURÉE : ENVOYER RAPIDEMENT, RECEVOIR (QR), PRESSE-PAPIER
            // ─────────────────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onNavigateToSendMode,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Envoyer rapidement", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Vers un PC / Mobile", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                            }
                        }

                        Button(
                            onClick = onNavigateToReceiveMode,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0D9488),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Recevoir (QR)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("QR & Code PIN", fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }

                    // Bouton Presse-papier secondaire
                    OutlinedButton(
                        onClick = onNavigateToClipboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Presse-papier partagé (Synchronisation texte)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 4. ACCÈS DIRECT AU GESTIONNAIRE DE FICHIERS
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToFiles("Tous") },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DriveFolderUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Gestionnaire de fichiers",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Accédez et envoyez vos fichiers directement",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. GRILLE DES CATÉGORIES DE FICHIERS
            // ─────────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryCard(
                        modifier = Modifier.weight(1f),
                        title = "Images",
                        count = "Parcourir",
                        icon = Icons.Default.Image,
                        iconColor = LanceDropBlue,
                        bgColor = LanceDropBlueLight,
                        onClick = { onNavigateToFiles("Images") }
                    )
                    CategoryCard(
                        modifier = Modifier.weight(1f),
                        title = "Vidéos",
                        count = "Parcourir",
                        icon = Icons.Default.Movie,
                        iconColor = LanceDropPurple,
                        bgColor = LanceDropPurpleLight,
                        onClick = { onNavigateToFiles("Vidéos") }
                    )
                    CategoryCard(
                        modifier = Modifier.weight(1f),
                        title = "Documents",
                        count = "Parcourir",
                        icon = Icons.Default.Description,
                        iconColor = LanceDropOrange,
                        bgColor = LanceDropOrangeLight,
                        onClick = { onNavigateToFiles("Documents") }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryCard(
                        modifier = Modifier.weight(1f),
                        title = "Audio",
                        count = "Parcourir",
                        icon = Icons.Default.MusicNote,
                        iconColor = LanceDropGreen,
                        bgColor = LanceDropGreenLight,
                        onClick = { onNavigateToFiles("Audio") }
                    )
                    CategoryCard(
                        modifier = Modifier.weight(1f),
                        title = "Autres",
                        count = "Parcourir",
                        icon = Icons.Default.FolderZip,
                        iconColor = Color(0xFF64748B),
                        bgColor = Color(0xFFF1F5F9),
                        onClick = { onNavigateToFiles(null) }
                    )
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 5. TRANSFERTS RÉCENTS
            // ─────────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transferts récents",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    TextButton(onClick = onNavigateToTransfers) {
                        Text("Voir tout", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (recentTransfers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aucun transfert récent",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Vos fichiers envoyés ou reçus apparaîtront ici.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(recentTransfers.size) { index ->
                    val item = recentTransfers[index]
                    val sizeFormatted = com.lancefer.lancedrop.utils.FileHelper.formatFileSize(item.fileSize)
                    val speedPart = if (item.averageSpeedBytesPerSec > 0) {
                        String.format(java.util.Locale.FRANCE, " • %.1f Mo/s", item.averageSpeedBytesPerSec / (1024.0 * 1024.0))
                    } else ""
                    val isDirPart = if (item.isDirectory) " [Dossier]" else ""

                    RecentTransferItem(
                        name = item.fileName + isDirPart,
                        meta = "$sizeFormatted • ${item.peerName}$speedPart",
                        icon = if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        iconColor = LanceDropBlue,
                        bgColor = LanceDropBlueLight,
                        statusText = when (item.status) {
                            "COMPLETED" -> "Terminé"
                            "INTERRUPTED" -> "Interrompu"
                            "CANCELLED" -> "Annulé"
                            else -> item.status
                        },
                        onOpen = if (item.fileUri != null || !item.filePath.isNullOrEmpty()) {
                            { FileHelper.openFile(context, item.fileUri, item.filePath, item.fileName) }
                        } else null
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun CategoryCard(
    modifier: Modifier = Modifier,
    title: String,
    count: String,
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(count, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RecentTransferItem(
    name: String,
    meta: String,
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    statusText: String,
    onOpen: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }

                Column {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isSuccess = statusText == "Terminé"
                Icon(
                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (isSuccess) LanceDropGreen else LanceDropRed,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSuccess) LanceDropGreen else LanceDropRed
                )

                if (onOpen != null) {
                    IconButton(onClick = onOpen, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Ouvrir",
                            tint = LanceDropBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
