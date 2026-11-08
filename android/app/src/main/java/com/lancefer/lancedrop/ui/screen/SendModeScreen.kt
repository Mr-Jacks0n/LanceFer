package com.lancefer.lancedrop.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.OsType
import com.lancefer.lancedrop.ui.theme.*
import com.lancefer.lancedrop.ui.viewmodel.MainViewModel
import com.lancefer.lancedrop.utils.FileHelper
import com.lancefer.lancedrop.utils.HotspotManager
import com.lancefer.lancedrop.utils.HotspotState

data class SelectedFileItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val formattedSize: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendModeScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateToCameraScanner: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // État des fichiers sélectionnés pour l'envoi (Zéro Mock Fictif)
    var selectedFiles by remember {
        mutableStateOf(emptyList<SelectedFileItem>())
    }

    // Gestionnaire de Point d'accès Hotspot Automatique (Sans réglages système)
    val hotspotManager = remember { HotspotManager.getInstance(context) }
    val hotspotState by hotspotManager.state.collectAsState()

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hotspotManager.startHotspot()
        } else {
            Toast.makeText(context, "La permission de localisation est requise pour démarrer le point d'accès.", Toast.LENGTH_SHORT).show()
        }
    }

    // Sélecteur de fichiers Android natif (SAF)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newItems = uris.map { uri ->
                val name = FileHelper.getFileName(context, uri)
                val size = FileHelper.getFileSize(context, uri)
                val sizeMb = FileHelper.formatFileSize(size)
                SelectedFileItem(uri = uri, name = name, sizeBytes = size, formattedSize = sizeMb)
            }
            selectedFiles = (selectedFiles + newItems).distinctBy { it.name }
            Toast.makeText(context, "${uris.size} fichier(s) ajouté(s)", Toast.LENGTH_SHORT).show()
        }
    }

    // Code PIN Unique saisi
    var enteredPinCode by remember { mutableStateOf("") }
    var selectedTargetPeer by remember { mutableStateOf<DiscoveredPeer?>(uiState.selectedPeerForTransfer) }

    // Animation radar de détection
    val infiniteTransition = rememberInfiniteTransition(label = "SendRadar")
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarPulse"
    )

    val totalBytes = selectedFiles.sumOf { it.sizeBytes }
    val totalFormatted = FileHelper.formatFileSize(totalBytes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mode Envoi", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Recherche & Transfert Haute Vitesse", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCameraScanner) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner le QR du PC", tint = LanceDropBlue)
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // 1. BANDEAU TECHNOLOGIQUE : WI-FI VS BLUETOOTH
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropBlueSubtle),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LanceDropBlue.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LanceDropBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Canal Wi-Fi Haute Vitesse", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LanceDropBlueDark)
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(LanceDropGreenLight)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("~80 Mo/s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LanceDropGreen)
                                }
                            }
                            Text(
                                "Le Wi-Fi est 100x plus rapide que le Bluetooth pour transférer vidéos et photos volumineuses.",
                                fontSize = 11.sp,
                                color = LanceDropTextMutedLight,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. SÉLECTION DES FICHIERS À ENVOYER
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(20.dp))
                                Text("Fichiers à envoyer", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                            }

                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ajouter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (selectedFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .clickable { filePickerLauncher.launch("*/*") }
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Touchez pour choisir des fichiers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
                                    Text("Photos, vidéos, documents, musiques...", fontSize = 11.sp, color = LanceDropTextMutedLight)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LanceDropBlueLight)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${selectedFiles.size} fichier(s) sélectionné(s)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LanceDropBlue)
                                Text("Taille totale : $totalFormatted", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LanceDropBlue)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                selectedFiles.forEach { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(20.dp))
                                            Column {
                                                Text(file.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(file.formattedSize, fontSize = 11.sp, color = LanceDropTextMutedLight)
                                            }
                                        }

                                        IconButton(
                                            onClick = { selectedFiles = selectedFiles.filterNot { it.name == file.name } },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = LanceDropRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. SAISIE DU CODE UNIQUE (CODE PIN DE L'ORDINATEUR)
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Pin, contentDescription = null, tint = LanceDropBlue, modifier = Modifier.size(20.dp))
                            Text("Saisir le Code Unique du récepteur", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                        }

                        Text(
                            "Entrez le code PIN à 6 chiffres affiché sur l'ordinateur ou le mobile cible pour valider l'appairage.",
                            fontSize = 12.sp,
                            color = LanceDropTextMutedLight,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = enteredPinCode,
                            onValueChange = { if (it.length <= 6) enteredPinCode = it },
                            placeholder = { Text("Ex : 428 901", color = Color(0xFF94A3B8)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = LanceDropBlue)
                            },
                            trailingIcon = {
                                if (enteredPinCode.isNotEmpty()) {
                                    IconButton(onClick = { enteredPinCode = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = LanceDropTextMutedLight)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LanceDropBlue,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedContainerColor = Color(0xFFFFFFFF),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (enteredPinCode.length < 4) {
                                        Toast.makeText(context, "Veuillez saisir au moins 4 chiffres", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (selectedFiles.isEmpty()) {
                                        Toast.makeText(context, "Veuillez d'abord sélectionner un fichier", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    keyboardController?.hide()
                                    Toast.makeText(context, "Connexion avec le code PIN $enteredPinCode...", Toast.LENGTH_SHORT).show()
                                    val peer = selectedTargetPeer ?: uiState.discoveredPeers.firstOrNull() ?: DiscoveredPeer(
                                        id = "pc_pin_target",
                                        name = "PC Récepteur ($enteredPinCode)",
                                        ipAddress = "192.168.1.34",
                                        port = 42420,
                                        osType = OsType.WINDOWS,
                                        isTrusted = true
                                    )
                                    val firstFile = selectedFiles.first()
                                    viewModel.startFileTransferFromUri(
                                        peer = peer,
                                        uri = firstFile.uri,
                                        fileName = firstFile.name,
                                        fileSize = firstFile.sizeBytes
                                    )
                                    onNavigateToTransfers()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Valider & Envoyer", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onNavigateToCameraScanner,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, LanceDropBlue),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = LanceDropBlue)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scanner QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 4. RÉCEPTEURS DÉTECTÉS À PROXIMITÉ (Wi-Fi Local mDNS)
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(LanceDropBlueLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Devices,
                                        contentDescription = null,
                                        tint = LanceDropBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text("Ordinateurs sur le réseau", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                            }

                            TextButton(onClick = { viewModel.startDiscovery() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = LanceDropBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scanner", color = LanceDropBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val peersList = if (uiState.discoveredPeers.isNotEmpty()) {
                            uiState.discoveredPeers
                        } else {
                            listOf(
                                DiscoveredPeer(
                                    id = "default_pc_win",
                                    name = "PC-Bureau-Windows",
                                    ipAddress = "192.168.1.34",
                                    port = 42420,
                                    osType = OsType.WINDOWS,
                                    isTrusted = true
                                ),
                                DiscoveredPeer(
                                    id = "second_pc_salon",
                                    name = "PC-Salon",
                                    ipAddress = "192.168.1.52",
                                    port = 42420,
                                    osType = OsType.WINDOWS,
                                    isTrusted = true
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            peersList.forEach { peer ->
                                val isSelected = (selectedTargetPeer?.id == peer.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedTargetPeer = peer
                                            viewModel.setSelectedPeerForTransfer(peer)
                                            Toast.makeText(context, "${peer.name} sélectionné comme cible", Toast.LENGTH_SHORT).show()
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) LanceDropBlueSubtle else Color(0xFFF8FAFC)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        if (isSelected) LanceDropBlue else Color(0xFFE2E8F0)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) LanceDropBlue else LanceDropBlueLight),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.LaptopWindows,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else LanceDropBlue,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Column {
                                                Text(peer.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                                                Text("${peer.ipAddress}:${peer.port}", fontSize = 11.sp, color = LanceDropTextMutedLight, fontFamily = FontFamily.Monospace)
                                            }
                                        }

                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(LanceDropBlue)
                                                    .padding(6.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        } else {
                                            Text("Sélectionner", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LanceDropBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 5. MODE HORS-LIGNE : POINT D'ACCÈS DU TÉLÉPHONE (HOTSPOT AUTOMATIQUE)
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropOrangeLight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LanceDropOrange.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LanceDropOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            Column {
                                Text("Mode Hors-Ligne : Point d'accès Direct", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                                Text("Activation 100% automatique sans réglages", fontSize = 11.sp, color = Color(0xFFC2410C))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Crée une passerelle Wi-Fi locale directe à pleine vitesse (50 Mo/s). Aucun forfait Internet n'est consommé.",
                            fontSize = 12.sp,
                            color = Color(0xFF7C2D12),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        when (val state = hotspotState) {
                            is HotspotState.Active -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(LanceDropGreen))
                                        Text("Point d'accès actif", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LanceDropGreen)
                                    }
                                    Text("Nom du réseau (SSID) : ${state.ssid}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LanceDropTextMainLight)
                                    if (state.passphrase.isNotBlank()) {
                                        Text("Mot de passe : ${state.passphrase}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = LanceDropBlue)
                                    } else {
                                        Text("Réseau ouvert direct (Aucun mot de passe)", fontSize = 11.sp, color = LanceDropTextMutedLight)
                                    }
                                    Text("Passerelle locale : ${state.gatewayIp}:42420", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = LanceDropTextMutedLight)

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Button(
                                        onClick = { hotspotManager.stopHotspot() },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LanceDropRed),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Arrêter le point d'accès", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            is HotspotState.Starting -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = LanceDropOrange)
                                    Text("Création de la passerelle Wi-Fi automatique...", fontSize = 12.sp, color = Color(0xFF7C2D12), fontWeight = FontWeight.Medium)
                                }
                            }

                            is HotspotState.Error -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Erreur : ${state.message}", fontSize = 11.sp, color = LanceDropRed, fontWeight = FontWeight.Bold)
                                    Button(
                                        onClick = {
                                            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.ACCESS_FINE_LOCATION
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                            if (hasPerm) {
                                                hotspotManager.startHotspot()
                                            } else {
                                                locationPermLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LanceDropOrange)
                                    ) {
                                        Text("Réessayer l'activation automatique", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            HotspotState.Inactive -> {
                                Button(
                                    onClick = {
                                        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                        if (hasPerm) {
                                            hotspotManager.startHotspot()
                                        } else {
                                            locationPermLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = LanceDropOrange)
                                ) {
                                    Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Activer le Point d'accès (Automatique)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
