package com.lancefer.lancedrop.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lancefer.lancedrop.model.TransferHistoryItem
import com.lancefer.lancedrop.model.TransferState
import com.lancefer.lancedrop.ui.theme.*
import com.lancefer.lancedrop.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTransfersScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: En cours, 1: Terminés

    val activeCount = if (uiState.activeTransferState is TransferState.Transferring) 1 else 0
    val finishedCount = uiState.transferHistory.size

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = LanceDropTextMainLight)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Transferts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LanceDropTextMainLight
                )
            }
        },
        containerColor = LanceDropBgLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // Sélecteur d'onglets dynamique En cours (activeCount) / Terminés (finishedCount)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .padding(4.dp)
            ) {
                Button(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) LanceDropBlue else Color.Transparent,
                        contentColor = if (selectedTab == 0) Color.White else LanceDropTextMutedLight
                    ),
                    elevation = null
                ) {
                    Text(
                        text = if (activeCount > 0) "En cours ($activeCount)" else "En cours",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 1) LanceDropBlue else Color.Transparent,
                        contentColor = if (selectedTab == 1) Color.White else LanceDropTextMutedLight
                    ),
                    elevation = null
                ) {
                    Text(
                        text = if (finishedCount > 0) "Terminés ($finishedCount)" else "Terminés",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedTab == 0) {
                // ONGLET EN COURS
                when (val transferState = uiState.activeTransferState) {
                    is TransferState.Transferring -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                TransferProgressCardLive(
                                    state = transferState,
                                    onPause = { viewModel.pauseTransfer() },
                                    onResume = { viewModel.resumeTransfer() },
                                    onCancel = { viewModel.cancelTransfer() }
                                )
                            }
                        }
                    }
                    is TransferState.Error -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = LanceDropRedLight)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = LanceDropRed, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Erreur de transfert", fontWeight = FontWeight.Bold, color = LanceDropRed, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(transferState.message, color = LanceDropTextMutedLight, fontSize = 12.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { viewModel.resumeTransfer() },
                                        colors = ButtonDefaults.buttonColors(containerColor = LanceDropRed)
                                    ) {
                                        Text("Réessayer")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // Empty state quand aucun transfert n'est actif
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF1F5F9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = LanceDropTextMutedLight,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Aucun transfert en cours",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LanceDropTextMainLight
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Sélectionnez des fichiers dans vos documents ou recevez un fichier depuis un autre appareil pour lancer un échange.",
                                        fontSize = 12.sp,
                                        color = LanceDropTextMutedLight,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ONGLET TERMINÉS
                if (uiState.transferHistory.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.transferHistory, key = { it.id }) { item ->
                            val sizeFormatted = formatFileSize(item.fileSize)
                            RecentTransferItem(
                                name = item.fileName,
                                meta = "$sizeFormatted • ${item.peerName}",
                                icon = Icons.Default.CheckCircle,
                                iconColor = LanceDropGreen,
                                bgColor = LanceDropGreenLight,
                                statusText = "100%"
                            )
                        }
                    }
                } else {
                    // Empty state pour les transferts terminés
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = LanceDropTextMutedLight,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Aucun transfert terminé",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LanceDropTextMainLight
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "L'historique des fichiers envoyés ou reçus avec succès s'affichera ici.",
                                    fontSize = 12.sp,
                                    color = LanceDropTextMutedLight,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bannière Bas de Page : Service en arrière-plan
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = LanceDropBlueSubtle),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(LanceDropBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    Column {
                        Text(
                            "Vous pouvez continuer en arrière-plan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LanceDropBlue
                        )
                        Text(
                            "L'application reste active même si vous changez d'écran.",
                            fontSize = 11.sp,
                            color = LanceDropTextMutedLight
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransferProgressCardLive(
    state: TransferState.Transferring,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val percentText = "${(state.progressPercentage * 100).toInt()}%"
    val transferredMb = formatFileSize(state.transferredBytes)
    val totalMb = formatFileSize(state.totalBytes)
    val speedMb = String.format(java.util.Locale.FRANCE, "%.1f Mo/s", state.speedBytesPerSec / (1024.0 * 1024.0))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LanceDropBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(state.fileName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                        Text(
                            text = if (state.isPaused) "$percentText • En pause" else "$percentText • En cours",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.isPaused) LanceDropOrange else LanceDropBlue
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { if (state.isPaused) onResume() else onPause() }) {
                        Icon(
                            imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (state.isPaused) "Reprendre" else "Pause",
                            tint = LanceDropBlue
                        )
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Annuler", tint = LanceDropRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { state.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (state.isPaused) LanceDropOrange else LanceDropBlue,
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("$transferredMb / $totalMb", fontSize = 11.sp, color = LanceDropTextMutedLight)
                Text(speedMb, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return if (bytes < 1024 * 1024) {
        String.format(java.util.Locale.FRANCE, "%.0f Ko", bytes.toDouble() / 1024.0)
    } else if (bytes < 1024 * 1024 * 1024) {
        String.format(java.util.Locale.FRANCE, "%.1f Mo", bytes.toDouble() / (1024.0 * 1024.0))
    } else {
        String.format(java.util.Locale.FRANCE, "%.2f Go", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
    }
}
