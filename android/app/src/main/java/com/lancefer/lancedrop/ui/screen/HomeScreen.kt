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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToFiles: (category: String?) -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToReceiveMode: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
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
                    BadgedBox(badge = { Badge { Text("2") } }) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = LanceDropTextMainLight)
                    }
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
                                        "Samsung Galaxy A54",
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
                                        Text("Connecté", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LanceDropGreen)
                                    }
                                }
                            }

                            // Batterie
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = LanceDropGreen, modifier = Modifier.size(18.dp))
                                Text("78%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Jauge de stockage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Espace disponible", fontSize = 12.sp, color = LanceDropTextMutedLight)
                            Text("124,6 Go / 256 Go", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { 0.486f },
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
            // 2. BOUTON RECEVOIR RAPIDE (MODE RÉCEPTION QR)
            // ─────────────────────────────────────────────────────────────
            item {
                Button(
                    onClick = onNavigateToReceiveMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ouvrir le Mode Réception QR", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                        count = "1246",
                        icon = Icons.Default.Image,
                        iconColor = LanceDropBlue,
                        bgColor = LanceDropBlueLight,
                        onClick = { onNavigateToFiles("Images") }
                    )
                    CategoryCard(
                        modifier = Modifier.weight(1f),
                        title = "Vidéos",
                        count = "32",
                        icon = Icons.Default.Movie,
                        iconColor = LanceDropPurple,
                        bgColor = LanceDropPurpleLight,
                        onClick = { onNavigateToFiles("Vidéos") }
                    )
                    CategoryCard(
                        modifier = Modifier.weight(1f),
                        title = "Documents",
                        count = "18",
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
                        count = "56",
                        icon = Icons.Default.MusicNote,
                        iconColor = LanceDropGreen,
                        bgColor = LanceDropGreenLight,
                        onClick = { onNavigateToFiles("Audio") }
                    )
                    CategoryCard(
                        modifier = Modifier.weight(1f),
                        title = "Autres",
                        count = "12",
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

            item {
                RecentTransferItem(
                    name = "Voyage_Côte.mp4",
                    meta = "28,7 Mo • 6 août 2025",
                    icon = Icons.Default.Movie,
                    iconColor = LanceDropBlue,
                    bgColor = LanceDropBlueLight,
                    statusText = "Reçu"
                )
            }

            item {
                RecentTransferItem(
                    name = "Rapport_Projet.pdf",
                    meta = "1,2 Mo • 8 août 2025",
                    icon = Icons.Default.PictureAsPdf,
                    iconColor = LanceDropRed,
                    bgColor = LanceDropRedLight,
                    statusText = "Reçu"
                )
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
