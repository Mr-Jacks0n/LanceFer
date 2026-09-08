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
import com.lancefer.lancedrop.utils.FileHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onNavigateToPreview: (fileName: String) -> Unit,
    onNavigateToTransfers: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(initialCategory ?: "Tous") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedFileIds = remember { mutableStateListOf<String>() }
    var showSuccessToast by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Liste réelle de fichiers sélectionnés par l'utilisateur
    val realFiles = remember { mutableStateListOf<MobileFileItem>() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val info = FileHelper.getFileInfoFromUri(context, uri)
            val name = info?.name ?: "Fichier_${System.currentTimeMillis()}"
            val size = info?.size ?: 0L
            val sizeFormatted = String.format(Locale.FRANCE, "%.1f Mo", size.toDouble() / (1024.0 * 1024.0))
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
                        .background(LanceDropCardLight)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = { selectedFileIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Annuler", tint = LanceDropTextMainLight)
                        }
                        Text(
                            "${selectedFileIds.size} sélectionnés",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = LanceDropTextMainLight
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {}) { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = LanceDropRed) }
                        IconButton(onClick = {}) { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = LanceDropTextMutedLight) }
                        IconButton(onClick = {}) { Icon(Icons.Default.Share, contentDescription = null, tint = LanceDropTextMutedLight) }
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = LanceDropTextMainLight)
                    }
                    Text("Mes fichiers", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LanceDropTextMainLight)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = LanceDropBlue)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Sort, contentDescription = "Trier", tint = LanceDropTextMainLight)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Ajouter une sélection factice pour tester
                        selectedFileIds.addAll(listOf("1", "2", "3"))
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
                    color = LanceDropCardLight,
                    shadowElevation = 8.dp
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                selectedFileIds.clear()
                                showSuccessToast = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                        ) {
                            Text("Envoyer (33,1 Mo)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        containerColor = LanceDropBgLight
    ) { padding ->
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
                    listOf("Tous", "Images", "Vidéos", "Documents").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LanceDropBlue,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = LanceDropTextMutedLight
                            )
                        )
                    }
                }

                // Grille de Fichiers
            if (realFiles.isEmpty()) {
                // ÉTAT VIDE : AUCUN FICHIER CHOISI
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
                            text = "Votre explorateur est vide",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = LanceDropTextMainLight
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Sélectionnez les vrais fichiers de votre appareil (photos, vidéos, documents) pour préparer un transfert.",
                            fontSize = 13.sp,
                            color = LanceDropTextMutedLight,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                Text("Parcourir l'appareil", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                        onNavigateToPreview(file.name)
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = LanceDropCardLight),
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
                                    color = LanceDropTextMainLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${file.size} • ${file.date}",
                                    fontSize = 10.sp,
                                    color = LanceDropTextMutedLight
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
