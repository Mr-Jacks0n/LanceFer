package com.lancefer.lancedrop.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lancefer.lancedrop.ui.theme.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.lancefer.lancedrop.util.MediaStoreHelper
import com.lancefer.lancedrop.utils.FileHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.lancefer.lancedrop.ui.viewmodel.MainViewModel

data class MobileFileItem(
    val id: String,
    val name: String,
    val size: String,
    val date: String,
    val category: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFilesScreen(
    initialCategory: String? = null,
    viewModel: MainViewModel? = null,
    onNavigateToPreview: (fileName: String, fileUri: android.net.Uri?, fileSize: String, fileDate: String) -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(initialCategory ?: "Tous") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedFileIds = remember { mutableStateListOf<String>() }
    var showSuccessToast by remember { mutableStateOf(false) }
    var showTargetDeviceDialog by remember { mutableStateOf(false) }
    val uiState = viewModel?.uiState?.collectAsState()?.value
    val context = LocalContext.current

    // Liste réelle de fichiers
    val realFiles = remember { mutableStateListOf<MobileFileItem>() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            val media = MediaStoreHelper.queryRealMediaFiles(context, selectedCategory)
            realFiles.clear()
            realFiles.addAll(media)
        }
    }

    LaunchedEffect(selectedCategory) {
        if (MediaStoreHelper.hasStoragePermission(context)) {
            val media = MediaStoreHelper.queryRealMediaFiles(context, selectedCategory)
            realFiles.clear()
            realFiles.addAll(media)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val info = FileHelper.getFileInfoFromUri(context, uri)
            val name = info?.name ?: "Fichier_${System.currentTimeMillis()}"
            val size = info?.size ?: 0L
            val sizeFormatted = FileHelper.formatFileSize(size)
            val dateFormatted = SimpleDateFormat("d MMM yyyy", Locale.FRANCE).format(Date())

            val cat = when {
                name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".webp", true) -> "Images"
                name.endsWith(".mp4", true) || name.endsWith(".mkv", true) || name.endsWith(".mov", true) -> "Vidéos"
                name.endsWith(".mp3", true) || name.endsWith(".wav", true) || name.endsWith(".flac", true) -> "Audio"
                else -> "Documents"
            }

            val icon = when (cat) {
                "Images" -> Icons.Default.Image
                "Vidéos" -> Icons.Default.Movie
                "Audio" -> Icons.Default.MusicNote
                else -> Icons.Default.InsertDriveFile
            }

            val color = when (cat) {
                "Images" -> LanceDropBlue
                "Vidéos" -> LanceDropPurple
                "Audio" -> LanceDropGreen
                else -> LanceDropOrange
            }

            val bgColor = when (cat) {
                "Images" -> LanceDropBlueLight
                "Vidéos" -> LanceDropPurpleLight
                "Audio" -> LanceDropGreenLight
                else -> LanceDropOrangeLight
            }

            val newItem = MobileFileItem(
                id = uri.toString(),
                name = name,
                size = sizeFormatted,
                date = dateFormatted,
                category = cat,
                icon = icon,
                iconColor = color,
                bgColor = bgColor
            )
            if (realFiles.none { it.id == newItem.id }) {
                realFiles.add(newItem)
            }
        }
    }

    val filteredFiles = realFiles.filter { item ->
        val matchesCat = selectedCategory == "Tous" || item.category == selectedCategory
        val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
        matchesCat && matchesSearch
    }

    val isSelectionMode = selectedFileIds.isNotEmpty()

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Barre en mode Sélection Multiple
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = { selectedFileIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Annuler", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            "${selectedFileIds.size} sélectionnés",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {}) { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = LanceDropRed) }
                        IconButton(onClick = {}) { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = {}) { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } else {
                // Barre Normale
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("Mes fichiers", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = LanceDropBlue)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Sort, contentDescription = "Trier", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    },
                    containerColor = LanceDropBlue,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(999.dp),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Ajouter des fichiers", fontWeight = FontWeight.Bold) }
                )
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                showTargetDeviceDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                        ) {
                            Text("Transférer ${selectedFileIds.size} fichier(s) sélectionné(s)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (showTargetDeviceDialog) {
            val peers = uiState?.discoveredPeers ?: emptyList()
            AlertDialog(
                onDismissRequest = { showTargetDeviceDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = LanceDropBlue)
                        Text("Choisir l'appareil destinataire", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Sélectionnez l'ordinateur ou le smartphone vers lequel envoyer les fichiers :",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )

                        if (peers.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Recherche d'appareils sur le Wi-Fi local...", fontSize = 12.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            // Ajouter un PC local par défaut
                                            val defaultIp = "192.168.1.34"
                                            viewModel?.addManualPeer(defaultIp, 42420, "PC-Salon (Wi-Fi)")
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                                    ) {
                                        Text("Associer PC-Salon localement", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            peers.forEach { peer ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showTargetDeviceDialog = false
                                            viewModel?.setSelectedPeerForTransfer(peer)
                                            val filesToSend = realFiles.filter { it.id in selectedFileIds }
                                            val firstFile = filesToSend.firstOrNull()
                                            val fileName = firstFile?.name ?: "Fichier_selectionne.dat"
                                            viewModel?.startFileTransferFromUri(
                                                peer = peer,
                                                uri = android.net.Uri.EMPTY,
                                                fileName = fileName,
                                                fileSize = 15_400_000L
                                            )
                                            selectedFileIds.clear()
                                            onNavigateToTransfers()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            val isWindows = peer.osType == com.lancefer.lancedrop.model.OsType.WINDOWS
                                            Icon(
                                                if (isWindows) Icons.Default.Laptop else Icons.Default.Smartphone,
                                                contentDescription = null,
                                                tint = LanceDropBlue,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(peer.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                                Text("${peer.ipAddress}:${peer.port}", fontSize = 11.sp, color = Color(0xFF64748B))
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(LanceDropBlueLight)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text("Envoyer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LanceDropBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTargetDeviceDialog = false }) {
                        Text("Annuler", color = Color(0xFF64748B))
                    }
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // Barre de Recherche
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher un fichier, un dossier...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LanceDropTextMutedLight) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = LanceDropBlue,
                        unfocusedBorderColor = LanceDropCardBorderLight
                    ),
                    singleLine = true
                )

                // Filtres Catégories
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Tous", "Images", "Vidéos", "Audio", "Documents").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LanceDropBlue,
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Grille de Fichiers
            if (realFiles.isEmpty()) {
                val hasPerm = MediaStoreHelper.hasStoragePermission(context)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(LanceDropBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = LanceDropBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (hasPerm) "Votre explorateur est vide" else "Autorisation des fichiers requise",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = LanceDropTextMainLight
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (hasPerm)
                                "Sélectionnez des fichiers réels sur votre appareil (photos, vidéos, documents) pour préparer un transfert."
                            else
                                "Pour accéder directement à vos photos, vidéos et musiques, autorisez l'accès ou parcourez l'appareil via le gestionnaire système.",
                            fontSize = 13.sp,
                            color = LanceDropTextMutedLight,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (!hasPerm) {
                            Button(
                                onClick = { permissionLauncher.launch(MediaStoreHelper.getRequiredStoragePermissions()) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = Color.White)
                                    Text("Autoriser l'accès aux médias", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, LanceDropBlue),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LanceDropBlue)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = LanceDropBlue)
                                Text("Parcourir avec l'explorateur", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else if (filteredFiles.isEmpty()) {
                // ÉTAT VIDE RECHERCHE
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Aucun fichier trouvé",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LanceDropTextMainLight
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Essayez un autre mot-clé ou modifiez la catégorie.",
                            fontSize = 12.sp,
                            color = LanceDropTextMutedLight
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
                ) {
                    items(filteredFiles) { file ->
                        val isSelected = selectedFileIds.contains(file.id)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedFileIds.remove(file.id) else selectedFileIds.add(file.id)
                                    } else {
                                        val parsedUri = try { android.net.Uri.parse(file.id) } catch (_: Exception) { null }
                                        onNavigateToPreview(file.name, parsedUri, file.size, file.date)
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, LanceDropBlue) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(file.bgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        file.icon,
                                        contentDescription = null,
                                        tint = file.iconColor,
                                        modifier = Modifier.size(36.dp)
                                    )

                                    // Badge catégorie de fichier en haut à droite
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(file.iconColor.copy(alpha = 0.85f))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = file.category,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    // Checkbox si sélection
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                if (isSelected) selectedFileIds.remove(file.id) else selectedFileIds.add(file.id)
                                            },
                                            modifier = Modifier.align(Alignment.TopStart),
                                            colors = CheckboxDefaults.colors(checkedColor = LanceDropBlue)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    file.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${file.size} • ${file.date}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
            }
        }
    }

    // Notification Toast (Transfert terminé !)
    AnimatedVisibility(
                visible = showSuccessToast,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(LanceDropGreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = LanceDropGreen)
                            }
                            Column {
                                Text("Transfert terminé !", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                                Text("3 fichiers ont été transférés avec succès.", fontSize = 12.sp, color = LanceDropTextMutedLight)
                            }
                        }

                        Button(
                            onClick = {
                                showSuccessToast = false
                                onNavigateToTransfers()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                        ) {
                            Text("Voir les fichiers", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
