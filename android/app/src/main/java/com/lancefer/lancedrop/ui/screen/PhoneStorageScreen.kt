package com.lancefer.lancedrop.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneStorageScreen(
    onNavigateBack: () -> Unit,
    onCategoryClick: (category: String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Stockage interne",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = LanceDropTextMainLight
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = LanceDropTextMainLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LanceDropBgLight)
            )
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
            // 1. CARTE PRINCIPALE : GAUGE & STATISTIQUES GLOBALES
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Espace utilisé",
                                    fontSize = 13.sp,
                                    color = LanceDropTextMutedLight,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "84,3 Go",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LanceDropTextMainLight
                                    )
                                    Text(
                                        text = " / 128 Go",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LanceDropTextMutedLight,
                                        modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
                                    )
                                }
                            }

                            // Badge pourcentage
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LanceDropBlueLight)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "66% plein",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LanceDropBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Barre segmentée multi-couleurs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0))
                        ) {
                            // Images : ~21%
                            Box(modifier = Modifier.weight(0.21f).fillMaxHeight().background(Color(0xFF3B82F6)))
                            // Vidéos : ~14%
                            Box(modifier = Modifier.weight(0.14f).fillMaxHeight().background(Color(0xFF8B5CF6)))
                            // Applications : ~12%
                            Box(modifier = Modifier.weight(0.12f).fillMaxHeight().background(Color(0xFF06B6D4)))
                            // Documents : ~10%
                            Box(modifier = Modifier.weight(0.10f).fillMaxHeight().background(Color(0xFFF59E0B)))
                            // Audio : ~5%
                            Box(modifier = Modifier.weight(0.05f).fillMaxHeight().background(Color(0xFF10B981)))
                            // Autres : ~4%
                            Box(modifier = Modifier.weight(0.04f).fillMaxHeight().background(Color(0xFF64748B)))
                            // Libre : ~34%
                            Box(modifier = Modifier.weight(0.34f).fillMaxHeight().background(Color(0xFFE2E8F0)))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "43,7 Go libres",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LanceDropGreen
                            )
                            Text(
                                text = "Samsung Galaxy A54 5G",
                                fontSize = 12.sp,
                                color = LanceDropTextMutedLight
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. CARTE D'OPTIMISATION & LIBÉRATION D'ESPACE
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBFDBFE)))
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(LanceDropBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            Column {
                                Text(
                                    text = "Nettoyage rapide",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LanceDropTextMainLight
                                )
                                Text(
                                    text = "1,4 Go de fichiers temporaires ou doublons détectés.",
                                    fontSize = 12.sp,
                                    color = LanceDropTextMutedLight,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Libérer", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. DÉTAILS DES CATÉGORIES
            // ─────────────────────────────────────────────────────────────
            item {
                Text(
                    text = "Détail par catégorie",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LanceDropTextMainLight,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        StorageCategoryRow(
                            icon = Icons.Default.Image,
                            iconColor = Color(0xFF3B82F6),
                            bgColor = Color(0xFFEFF6FF),
                            title = "Images & Photos",
                            details = "3 450 fichiers",
                            sizeText = "26,8 Go",
                            onClick = { onCategoryClick("Images") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.Movie,
                            iconColor = Color(0xFF8B5CF6),
                            bgColor = Color(0xFFF5F3FF),
                            title = "Vidéos",
                            details = "124 fichiers",
                            sizeText = "18,4 Go",
                            onClick = { onCategoryClick("Vidéos") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.Apps,
                            iconColor = Color(0xFF06B6D4),
                            bgColor = Color(0xFFECFEFF),
                            title = "Applications & Données",
                            details = "88 applications installées",
                            sizeText = "15,2 Go",
                            onClick = { onCategoryClick("Apps") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.Description,
                            iconColor = Color(0xFFF59E0B),
                            bgColor = Color(0xFFFFFBEB),
                            title = "Documents & Fichiers PDF",
                            details = "412 fichiers",
                            sizeText = "12,6 Go",
                            onClick = { onCategoryClick("Documents") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.MusicNote,
                            iconColor = Color(0xFF10B981),
                            bgColor = Color(0xFFECFDF5),
                            title = "Audio & Musique",
                            details = "540 fichiers",
                            sizeText = "6,2 Go",
                            onClick = { onCategoryClick("Audio") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.FolderOpen,
                            iconColor = Color(0xFF64748B),
                            bgColor = Color(0xFFF8FAFC),
                            title = "Autres & Téléchargements",
                            details = "Système, cache et archives",
                            sizeText = "5,1 Go",
                            onClick = { onCategoryClick("Tous") }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StorageCategoryRow(
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    title: String,
    details: String,
    sizeText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LanceDropTextMainLight
                )
                Text(
                    text = details,
                    fontSize = 11.sp,
                    color = LanceDropTextMutedLight
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = sizeText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LanceDropTextMainLight
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
