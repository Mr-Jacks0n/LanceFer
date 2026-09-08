package com.lancefer.lancedrop.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTransfersScreen(
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: En cours, 1: Terminés

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = LanceDropTextMainLight)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Transfert en cours",
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
            // Sélecteur d'onglets En cours (2) / Terminés (5)
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
                    Text("En cours (2)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    Text("Terminés (5)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (selectedTab == 0) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Item 1 : Voyage_Côte.mp4 (69%)
                    item {
                        TransferProgressCard(
                            name = "Voyage_Côte.mp4",
                            progress = 0.69f,
                            percentText = "69%",
                            speedText = "12,4 Mo / 28,7 Mo",
                            rateText = "2,8 Mo/s",
                            icon = Icons.Default.Movie,
                            iconColor = LanceDropBlue,
                            bgColor = LanceDropBlueLight,
                            actionIcon = Icons.Default.Pause
                        )
                    }

                    // Item 2 : Rapport_Projet.pdf (38%)
                    item {
                        TransferProgressCard(
                            name = "Rapport_Projet.pdf",
                            progress = 0.38f,
                            percentText = "38%",
                            speedText = "0,8 Mo / 1,2 Mo",
                            rateText = "1,2 Mo/s",
                            icon = Icons.Default.PictureAsPdf,
                            iconColor = LanceDropRed,
                            bgColor = LanceDropRedLight,
                            actionIcon = Icons.Default.Close
                        )
                    }
                }
            } else {
                // Fichiers terminés
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    listOf(
                        "Paysage_Alpes.jpg" to "4,8 Mo",
                        "Dubai_Ville.jpg" to "3,2 Mo",
                        "Présentation.pptx" to "4,7 Mo",
                        "Musique_AFRO.mp3" to "7,3 Mo",
                        "CV_John.docx" to "842 Ko"
                    ).forEach { (name, size) ->
                        item {
                            RecentTransferItem(
                                name = name,
                                meta = "$size • Terminé avec succès",
                                icon = Icons.Default.CheckCircle,
                                iconColor = LanceDropGreen,
                                bgColor = LanceDropGreenLight,
                                statusText = "100%"
                            )
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
fun TransferProgressCard(
    name: String,
    progress: Float,
    percentText: String,
    speedText: String,
    rateText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    bgColor: Color,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                        Text(percentText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LanceDropBlue)
                    }
                }

                IconButton(onClick = {}) {
                    Icon(actionIcon, contentDescription = null, tint = LanceDropTextMutedLight)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = LanceDropBlue,
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(speedText, fontSize = 11.sp, color = LanceDropTextMutedLight)
                Text(rateText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
            }
        }
    }
}
