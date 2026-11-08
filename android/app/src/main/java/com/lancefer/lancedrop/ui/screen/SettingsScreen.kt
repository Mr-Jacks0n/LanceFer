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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    themeMode: String = "AUTO",
    onThemeModeChange: (String) -> Unit = {},
    soundEnabled: Boolean = true,
    onSoundEnabledChange: (Boolean) -> Unit = {},
    conflictResolution: String = "RENAME",
    onConflictResolutionChange: (String) -> Unit = {},
    onClearHistory: () -> Unit = {}
) {
    var multiStreamEnabled by remember { mutableStateOf(true) }
    var backgroundServiceEnabled by remember { mutableStateOf(true) }
    var autoAcceptContacts by remember { mutableStateOf(false) }
    var pinRequired by remember { mutableStateOf(true) }
    var darkModeEnabled by remember(themeMode) { mutableStateOf(themeMode == "DARK") }

    val context = LocalContext.current
    val deviceInfo = remember { com.lancefer.lancedrop.utils.DeviceMetadataHelper.getDeviceInfo(context) }
    val storageMetrics = remember { com.lancefer.lancedrop.utils.DeviceMetadataHelper.getStorageMetrics() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Paramètres",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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
            // 1. CARTE PROFIL APPAREIL LOCAL AUTHENTIQUE
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar Initiales Appareil
                        val deviceInitial = deviceInfo.fullDisplayName.firstOrNull()?.uppercaseChar()?.toString() ?: "L"
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(LanceDropBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = deviceInitial,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = deviceInfo.fullDisplayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LanceDropTextMainLight
                            )
                            Text(
                                text = "${deviceInfo.manufacturer} • ${deviceInfo.deviceName}",
                                fontSize = 13.sp,
                                color = LanceDropTextMutedLight
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Android ${deviceInfo.androidVersion} (API ${deviceInfo.sdkInt}) • Prêt",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LanceDropBlue
                            )
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
                            subtitle = deviceInfo.deviceName,
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
            // 2b. SPÉCIFICATIONS MATÉRIELLES COMPLÈTES (Zéro Donnée Fictive)
            // ─────────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(title = "Spécifications du Téléphone")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        DeviceSpecRow("Marque / Constructeur", deviceInfo.manufacturer)
                        HorizontalDivider(color = Color(0xFFF8FAFC), modifier = Modifier.padding(vertical = 6.dp))
                        DeviceSpecRow("Modèle", deviceInfo.model)
                        HorizontalDivider(color = Color(0xFFF8FAFC), modifier = Modifier.padding(vertical = 6.dp))
                        DeviceSpecRow("Code Série / Carte", "${deviceInfo.deviceCodename} (${deviceInfo.board})")
                        HorizontalDivider(color = Color(0xFFF8FAFC), modifier = Modifier.padding(vertical = 6.dp))
                        DeviceSpecRow("Version Android", "Android ${deviceInfo.androidVersion} (API ${deviceInfo.sdkInt})")
                        HorizontalDivider(color = Color(0xFFF8FAFC), modifier = Modifier.padding(vertical = 6.dp))
                        DeviceSpecRow("Numéro de Build", deviceInfo.buildNumber)
                        HorizontalDivider(color = Color(0xFFF8FAFC), modifier = Modifier.padding(vertical = 6.dp))
                        DeviceSpecRow("Stockage Réel", "${storageMetrics.totalFormatted} (${storageMetrics.freeFormatted} libres)")
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
                            icon = Icons.Default.ContentCopy,
                            title = "Gestion des doublons",
                            subtitle = when (conflictResolution) {
                                "PROMPT" -> "Demander confirmation à chaque fois"
                                "RENAME" -> "Conserver les deux (Renommer)"
                                "OVERWRITE" -> "Écraser les fichiers existants"
                                else -> "Ignorer les doublons (Passer)"
                            },
                            onClick = {
                                val next = when (conflictResolution) {
                                    "PROMPT" -> "RENAME"
                                    "RENAME" -> "OVERWRITE"
                                    "OVERWRITE" -> "SKIP"
                                    else -> "PROMPT"
                                }
                                onConflictResolutionChange(next)
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsToggleRow(
                            icon = Icons.Default.VolumeUp,
                            title = "Sons & Carillons",
                            subtitle = "Tonalités de succès, d'appairage et d'alerte",
                            checked = soundEnabled,
                            onCheckedChange = onSoundEnabledChange
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
                            title = "Thème sombre Cyber Slate",
                            subtitle = if (darkModeEnabled) "Palette Cyber Slate & Electric Blue active" else "Mode clair actif",
                            checked = darkModeEnabled,
                            onCheckedChange = {
                                darkModeEnabled = it
                                onThemeModeChange(if (it) "DARK" else "LIGHT")
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableRow(
                            icon = Icons.Default.DeleteSweep,
                            title = "Historique des transferts",
                            subtitle = "Effacer l'historique local et libérer l'espace",
                            onClick = onClearHistory
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

@Composable
private fun DeviceSpecRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = LanceDropTextMutedLight,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = LanceDropTextMainLight,
            fontWeight = FontWeight.SemiBold
        )
    }
}

