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
import androidx.compose.material.icons.automirrored.filled.Logout
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    var multiStreamEnabled by remember { mutableStateOf(true) }
    var backgroundServiceEnabled by remember { mutableStateOf(true) }
    var autoAcceptContacts by remember { mutableStateOf(false) }
    var pinRequired by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Paramètres",
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
            // 1. CARTE PROFIL UTILISATEUR (John D.)
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar Initiales
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(LanceDropBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "JD",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "John D.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LanceDropTextMainLight
                            )
                            Text(
                                text = "john.d@gmail.com",
                                fontSize = 13.sp,
                                color = LanceDropTextMutedLight
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Samsung Galaxy A54 5G",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LanceDropBlue
                            )
                        }

                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Edit, contentDescription = "Éditer", tint = LanceDropTextMutedLight)
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. GROUPE APPAREIL & DOSSIER
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Appareil & Rangement")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingsClickableRow(
                            icon = Icons.Default.Smartphone,
                            title = "Nom de l'appareil",
                            subtitle = "Samsung Galaxy A54",
                            onClick = {}
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableRow(
                            icon = Icons.Default.Folder,
                            title = "Dossier de réception",
                            subtitle = "/Stockage/Download/LanceDrop/",
                            onClick = {}
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. GROUPE TRANSFERTS & RÉSEAU
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Transferts & Performances")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Default.Speed,
                            title = "Mode multi-flux Turbo",
                            subtitle = "Accélère les gros transferts jusqu'à 80 Mo/s",
                            checked = multiStreamEnabled,
                            onCheckedChange = { multiStreamEnabled = it }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggleRow(
                            icon = Icons.Default.Sync,
                            title = "Recevoir en arrière-plan",
                            subtitle = "Maintient le service actif écran éteint",
                            checked = backgroundServiceEnabled,
                            onCheckedChange = { backgroundServiceEnabled = it }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableRow(
                            icon = Icons.Default.Lan,
                            title = "Port réseau local",
                            subtitle = "Port TCP 50001 (Automatique)",
                            onClick = {}
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 4. GROUPE SÉCURITÉ & CONFIDENTIALITÉ
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Sécurité")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Default.Lock,
                            title = "Code PIN d'appairage",
                            subtitle = "Valider chaque nouvelle connexion",
                            checked = pinRequired,
                            onCheckedChange = { pinRequired = it }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggleRow(
                            icon = Icons.Default.VerifiedUser,
                            title = "Confiance automatique",
                            subtitle = "Pour les appareils déjà vérifiés",
                            checked = autoAcceptContacts,
                            onCheckedChange = { autoAcceptContacts = it }
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 5. APPARENCE & APPLICATION
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Apparence & Informations")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Default.DarkMode,
                            title = "Thème sombre",
                            subtitle = "Adoucit l'affichage la nuit",
                            checked = darkModeEnabled,
                            onCheckedChange = { darkModeEnabled = it }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableRow(
                            icon = Icons.Default.Info,
                            title = "Version de LanceDrop",
                            subtitle = "v1.0.0 (Build 42) • À jour",
                            onClick = {}
                        )
                    }
                }
            }

            // Bouton Déconnexion
            item {
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LanceDropRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = LanceDropRed)
                        Text("Déconnexion", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = LanceDropTextMutedLight,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(20.dp))
            }

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LanceDropTextMainLight
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = LanceDropTextMutedLight
                )
            }
        }

        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color(0xFFCBD5E1),
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(20.dp))
            }

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LanceDropTextMainLight
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = LanceDropTextMutedLight
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LanceDropBlue
            )
        )
    }
}
