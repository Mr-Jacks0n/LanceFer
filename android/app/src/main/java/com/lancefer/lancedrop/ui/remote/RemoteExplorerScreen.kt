package com.lancefer.lancedrop.ui.remote

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lancefer.lancedrop.data.remote.BreadcrumbItem
import com.lancefer.lancedrop.data.remote.RemoteFileItem
import com.lancefer.lancedrop.data.remote.RemoteShare
import com.lancefer.lancedrop.ui.theme.*
import com.lancefer.lancedrop.ui.components.shimmerPlaceholder

/**
 * Écran principal de l'Explorateur de Fichiers Distant pour naviguer dans les dossiers partagés du PC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteExplorerScreen(
    viewModel: RemoteExplorerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showConnectionDialog by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }

    // Gestion de la touche retour matérielle d'Android
    BackHandler {
        val handled = viewModel.navigateUp()
        if (!handled) {
            onNavigateBack()
        }
    }

    // Si le lecteur multimédia est actif, l'afficher en plein écran
    if (uiState.activeMediaItem != null) {
        RemoteStreamingPlayerScreen(
            item = uiState.activeMediaItem!!,
            viewModel = viewModel,
            onBack = { viewModel.closeMedia() }
        )
        return
    }

    // Si la visionneuse photo HD est active, l'afficher en plein écran
    if (uiState.activePhotoItem != null) {
        RemotePhotoViewerScreen(
            item = uiState.activePhotoItem!!,
            viewModel = viewModel,
            onBack = { viewModel.closePhoto() }
        )
        return
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Barre Supérieure Principale
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val handled = viewModel.navigateUp()
                            if (!handled) onNavigateBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showConnectionDialog = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.selectedShare?.name ?: uiState.peerName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Indicateur de statut connecté
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isConnected) LanceDropGreen else LanceDropRed)
                            )
                        }
                        Text(
                            text = "${uiState.host}:${uiState.port}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Bouton Recherche
                    IconButton(onClick = { searchActive = !searchActive }) {
                        Icon(
                            imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Rechercher",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Bouton Rafraîchir
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rafraîchir",
                            tint = LanceDropBlue
                        )
                    }
                }

                // Barre de recherche rétractable
                AnimatedVisibility(visible = searchActive) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Filtrer les fichiers...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Effacer")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Fil d'Ariane (Breadcrumbs)
                if (uiState.selectedShare != null) {
                    val breadcrumbs = uiState.currentListing?.breadcrumbs.orEmpty().ifEmpty {
                        listOf(BreadcrumbItem(name = uiState.selectedShare!!.name, subpath = ""))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Élément Partages (Racine absolue)
                        Text(
                            text = "Partages",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = LanceDropBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.closeShare() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )

                        breadcrumbs.forEachIndexed { index, bc ->
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )

                            val isCurrent = index == breadcrumbs.size - 1
                            Text(
                                text = bc.name,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else LanceDropBlue,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(!isCurrent) { viewModel.navigateToBreadcrumb(bc) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Affichage du contenu
            when {
                uiState.isLoading && uiState.items.isEmpty() && uiState.shares.isEmpty() -> {
                    RemoteExplorerSkeletonLoader()
                }

                uiState.errorMessage != null && uiState.items.isEmpty() && uiState.shares.isEmpty() -> {
                    ErrorStateView(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.loadShares() },
                        onEditConnection = { showConnectionDialog = true },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Vue 1 : Liste des Partages racines du PC
                uiState.selectedShare == null -> {
                    if (uiState.shares.isEmpty()) {
                        EmptySharesView(
                            onAddConnection = { showConnectionDialog = true },
                            onRefresh = { viewModel.loadShares() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text(
                                    text = "Dossiers partagés sur ${uiState.peerName}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            items(uiState.shares) { share ->
                                ShareCardItem(
                                    share = share,
                                    onClick = { viewModel.selectShare(share) }
                                )
                            }
                        }
                    }
                }

                // Vue 2 : Liste des Fichiers et Sous-dossiers dans le Partage sélectionné
                else -> {
                    val displayItems = uiState.filteredItems
                    if (displayItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) "Aucun résultat trouvé pour \"${uiState.searchQuery}\"" else "Ce dossier est vide",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayItems, key = { it.subpath }) { item ->
                                RemoteFileRowItem(
                                    item = item,
                                    thumbnailUrl = viewModel.buildThumbnailUrl(item),
                                    token = uiState.token,
                                    onClick = {
                                        when {
                                            item.isDirectory -> viewModel.enterFolder(item)
                                            item.isVideo || item.isAudio -> viewModel.openMedia(item)
                                            item.isImage -> viewModel.openPhoto(item)
                                            else -> viewModel.downloadFile(context, item)
                                        }
                                    },
                                    onDownload = { viewModel.downloadFile(context, item) }
                                )
                            }
                        }
                    }
                }
            }

            // Bannière flottante d'état de téléchargement
            if (uiState.isDownloading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.downloadMessage ?: "Téléchargement en cours...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(uiState.downloadProgress * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = LanceDropBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = LanceDropBlue
                        )
                    }
                }
            }

            // Notification de téléchargement terminé
            if (!uiState.isDownloading && uiState.downloadMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = { viewModel.dismissDownloadMessage() }) {
                            Text("OK", color = LanceDropBlue)
                        }
                    }
                ) {
                    Text(uiState.downloadMessage!!)
                }
            }
        }
    }

    // Dialogue de configuration d'adresse IP & Port PC
    if (showConnectionDialog) {
        ConnectionConfigDialog(
            currentHost = uiState.host,
            currentPort = uiState.port,
            currentToken = uiState.token,
            onDismiss = { showConnectionDialog = false },
            onConfirm = { host, port, token ->
                viewModel.configureConnection(host, port, token, "PC ($host)")
                showConnectionDialog = false
            }
        )
    }
}

/**
 * Carte de dossier partagé (racine).
 */
@Composable
fun ShareCardItem(
    share: RemoteShare,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LanceDropBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderShared,
                    contentDescription = "Partage",
                    tint = LanceDropBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = share.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (share.itemCount > 0) {
                    Text(
                        text = "${share.itemCount} éléments",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Ligne d'un fichier ou sous-dossier dans l'explorateur.
 */
@Composable
fun RemoteFileRowItem(
    item: RemoteFileItem,
    thumbnailUrl: String,
    token: String,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône ou vignette du fichier
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            item.isDirectory -> LanceDropBlueLight
                            item.isVideo -> LanceDropPurple.copy(alpha = 0.15f)
                            item.isAudio -> LanceDropGreen.copy(alpha = 0.15f)
                            item.isImage -> LanceDropBlue.copy(alpha = 0.15f)
                            else -> LanceDropBgLight
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    (item.isImage || item.isVideo) && item.hasThumbnail -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(thumbnailUrl)
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("X-Session-Token", token)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    item.isDirectory -> {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Dossier",
                            tint = LanceDropBlue,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    item.isVideo -> {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Vidéo",
                            tint = LanceDropPurple,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    item.isAudio -> {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Audio",
                            tint = LanceDropGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    item.isImage -> {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Image",
                            tint = LanceDropBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = "Fichier",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Nom et métadonnées (Taille, type)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.formattedSize,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action à droite
            if (item.isDirectory) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Ouvrir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Télécharger",
                        tint = LanceDropBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    onEditConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Erreur",
            tint = LanceDropRed,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connexion impossible",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onEditConnection) {
                Text("Configurer IP")
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
            ) {
                Text("Réessayer")
            }
        }
    }
}

@Composable
fun EmptySharesView(
    onAddConnection: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = LanceDropBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Aucun dossier partagé",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Assurez-vous que LanceDrop Desktop est démarré sur le PC et qu'au moins un dossier est partagé.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAddConnection) {
                Text("Changer d'IP")
            }
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
            ) {
                Text("Rafraîchir")
            }
        }
    }
}

@Composable
fun ConnectionConfigDialog(
    currentHost: String,
    currentPort: Int,
    currentToken: String,
    onDismiss: () -> Unit,
    onConfirm: (host: String, port: Int, token: String) -> Unit
) {
    var host by remember { mutableStateOf(currentHost) }
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var token by remember { mutableStateOf(currentToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres du PC Distant", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Adresse IP du PC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port HTTP (défaut 42421)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Jeton de session / PIN (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = portText.toIntOrNull() ?: 42421
                    onConfirm(host.trim(), p, token.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = LanceDropBlue)
            ) {
                Text("Connecter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

/**
 * Skeleton Loader ultra-fluide simulant les répertoires et fichiers du PC distant
 * pendant le temps de négociation réseau.
 */
@Composable
fun RemoteExplorerSkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .shimmerPlaceholder(true, shape = RoundedCornerShape(10.dp))
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(14.dp)
                            .shimmerPlaceholder(true)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .shimmerPlaceholder(true)
                    )
                }
            }
        }
    }
}
