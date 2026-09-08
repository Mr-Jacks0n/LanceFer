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

    val files = remember {
        listOf(
            MobileFileItem("1", "Paysage_Alpes.jpg", "4,8 Mo", "12 août 2025", "Images", Icons.Default.Image, LanceDropBlue, LanceDropBlueLight),
            MobileFileItem("2", "Dubai_Ville.jpg", "3,2 Mo", "10 août 2025", "Images", Icons.Default.Image, LanceDropBlue, LanceDropBlueLight),
            MobileFileItem("3", "Présentation.pptx", "4,7 Mo", "31 juil. 2025", "Documents", Icons.Default.Slideshow, LanceDropOrange, LanceDropOrangeLight),
            MobileFileItem("4", "Rapport_Projet.pdf", "1,2 Mo", "8 août 2025", "Documents", Icons.Default.PictureAsPdf, LanceDropRed, LanceDropRedLight),
            MobileFileItem("5", "Musique_AFRO.mp3", "7,3 Mo", "1 août 2025", "Audio", Icons.Default.MusicNote, LanceDropPurple, LanceDropPurpleLight),
            MobileFileItem("6", "Forêt.jpg", "2,6 Mo", "24 juil. 2025", "Images", Icons.Default.Image, LanceDropGreen, LanceDropGreenLight),
            MobileFileItem("7", "Voyage_Côte.mp4", "28,7 Mo", "6 août 2025", "Vidéos", Icons.Default.Movie, LanceDropBlue, LanceDropBlueLight),
            MobileFileItem("8", "Archives.zip", "12,4 Mo", "26 juil. 2025", "Documents", Icons.Default.FolderZip, LanceDropOrange, LanceDropOrangeLight)
        )
    }

    val filteredFiles = files.filter { item ->
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
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = LanceDropTextMainLight)
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
