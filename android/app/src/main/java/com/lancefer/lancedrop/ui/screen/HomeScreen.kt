package com.lancefer.lancedrop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.*
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.ui.text.style.TextAlign
import com.lancefer.lancedrop.model.TransferHistoryItem

@Composable
fun HomeScreen(
    recentTransfers: List<TransferHistoryItem> = emptyList(),
    onNavigateToFiles: (category: String?) -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToReceiveMode: () -> Unit,
    onNavigateToClipboard: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Calcul dynamique de l'espace de stockage réel du téléphone
    val (storageText, storageProgress) = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes = stat.availableBlocksLong * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
            val progress = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) else 0.4f
            val usedGb = String.format(java.util.Locale.FRANCE, "%.1f Go", usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            val totalGb = String.format(java.util.Locale.FRANCE, "%.0f Go", totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            Pair("$usedGb / $totalGb", progress)
        } catch (e: Exception) {
            Pair("45,2 Go / 128 Go", 0.35f)
        }
    }

    val deviceDisplayName = remember {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }
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
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = LanceDropTextMainLight)
                }

                Text(
                    text = "LanceDrop",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LanceDropTextMainLight
                )

                IconButton(onClick = {}) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = LanceDropTextMainLight)
                }
            }
        },
        containerColor = LanceDropBgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // 1. CARTE APPAREIL & STOCKAGE (Samsung Galaxy A54)
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToStorage() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(LanceDropBlueLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = LanceDropBlue,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Column {
                                    Text("Mon téléphone", fontSize = 11.sp, color = LanceDropTextMutedLight)
                                    Text(
                                        text = deviceDisplayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LanceDropTextMainLight
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
                                        Text("Prêt pour transfert", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LanceDropGreen)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Jauge de stockage réel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Espace utilisé", fontSize = 12.sp, color = LanceDropTextMutedLight)
                            Text(storageText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { storageProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = LanceDropBlue,
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. ACTIONS RAPIDES : MODE RÉCEPTION & PRESSE-PAPIER PARTAGÉ
            // ─────────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onNavigateToReceiveMode,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recevoir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToClipboard,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LanceDropBlue),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LanceDropBlue)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Presse-papier", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            // 4. TRANSFERTS RÉCENTS
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
                        color = LanceDropTextMainLight
                    )

                    TextButton(onClick = onNavigateToTransfers) {
                        Text("Voir tout", color = LanceDropBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (recentTransfers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
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
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = LanceDropTextMutedLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aucun transfert récent",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LanceDropTextMainLight
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Vos fichiers envoyés ou reçus apparaîtront ici.",
                                fontSize = 12.sp,
                                color = LanceDropTextMutedLight,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(recentTransfers.size) { index ->
                    val item = recentTransfers[index]
                    val sizeFormatted = String.format(java.util.Locale.FRANCE, "%.1f Mo", item.fileSize.toDouble() / (1024.0 * 1024.0))
                    RecentTransferItem(
                        name = item.fileName,
                        meta = "$sizeFormatted • ${item.peerName}",
                        icon = Icons.Default.InsertDriveFile,
                        iconColor = LanceDropBlue,
                        bgColor = LanceDropBlueLight,
                        statusText = "Terminé"
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
        colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
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
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
            Text(count, fontSize = 11.sp, color = LanceDropTextMutedLight)
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
    statusText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
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
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
                    Text(meta, fontSize = 11.sp, color = LanceDropTextMutedLight)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LanceDropGreen, modifier = Modifier.size(14.dp))
                Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LanceDropGreen)
            }
        }
    }
}
