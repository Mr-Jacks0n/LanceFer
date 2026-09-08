package com.lancefer.lancedrop.ui.screen

import android.widget.Toast
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lancefer.lancedrop.ui.theme.*
import com.lancefer.lancedrop.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneStorageScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onCategoryClick: (category: String) -> Unit
) {
    val context = LocalContext.current
    val storageInfo = remember { viewModel.getRealStorageInfo() }
    val deviceName = remember {
        val model = android.os.Build.MODEL
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    var isCleaned by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Stockage de l'appareil",
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
            // 1. CARTE PRINCIPALE : STATISTIQUES RÉELLES
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
                                        text = storageInfo.usedFormatted,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LanceDropTextMainLight
                                    )
                                    Text(
                                        text = " / ${storageInfo.totalFormatted}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LanceDropTextMutedLight,
                                        modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
                                    )
                                }
                            }

                            // Badge pourcentage réel
                            val percentInt = (storageInfo.usedPercentage * 100).toInt()
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LanceDropBlueLight)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "$percentInt% plein",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LanceDropBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Barre de progression réelle
                        val usedWeight = storageInfo.usedPercentage.coerceIn(0.02f, 0.98f)
                        val freeWeight = (1f - storageInfo.usedPercentage).coerceIn(0.02f, 0.98f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(usedWeight)
                                    .fillMaxHeight()
                                    .background(LanceDropBlue)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(freeWeight)
                                    .fillMaxHeight()
                                    .background(Color(0xFFE2E8F0))
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${storageInfo.freeFormatted} libres",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LanceDropGreen
                            )
                            Text(
                                text = deviceName,
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
                                    text = if (isCleaned) "Stockage optimisé" else "Nettoyage du cache",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LanceDropTextMainLight
                                )
                                Text(
                                    text = if (isCleaned) "Cache vidé avec succès. Aucun fichier résiduel." else "Supprimez les fichiers temporaires et le cache de l'application.",
                                    fontSize = 12.sp,
                                    color = LanceDropTextMutedLight,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                try {
                                    context.cacheDir.deleteRecursively()
                                    isCleaned = true
                                    Toast.makeText(context, "Cache de l'application nettoyé !", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Nettoyage terminé", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isCleaned) LanceDropGreen else LanceDropBlue),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(if (isCleaned) "Fait" else "Nettoyer", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. DÉTAILS DES CATÉGORIES
            // ─────────────────────────────────────────────────────────────
            item {
                Text(
                    text = "Explorer par catégorie",
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
                            details = "Parcourir les photos de l'appareil",
                            onClick = { onCategoryClick("Images") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.Movie,
                            iconColor = Color(0xFF8B5CF6),
                            bgColor = Color(0xFFF5F3FF),
                            title = "Vidéos",
                            details = "Parcourir les vidéos de l'appareil",
                            onClick = { onCategoryClick("Vidéos") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.Description,
                            iconColor = Color(0xFFF59E0B),
                            bgColor = Color(0xFFFFFBEB),
                            title = "Documents & Fichiers PDF",
                            details = "Parcourir les documents et rapports",
                            onClick = { onCategoryClick("Documents") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.MusicNote,
                            iconColor = Color(0xFF10B981),
                            bgColor = Color(0xFFECFDF5),
                            title = "Audio & Musique",
                            details = "Parcourir les fichiers audio",
                            onClick = { onCategoryClick("Audio") }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))

                        StorageCategoryRow(
                            icon = Icons.Default.FolderOpen,
                            iconColor = Color(0xFF64748B),
                            bgColor = Color(0xFFF8FAFC),
                            title = "Tous les fichiers",
                            details = "Explorateur complet avec sélecteur SAF",
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = LanceDropTextMutedLight,
            modifier = Modifier.size(14.dp)
        )
    }
}
