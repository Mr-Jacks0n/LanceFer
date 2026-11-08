package com.lancefer.lancedrop.ui.remote

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lancefer.lancedrop.data.HistoryDatabaseHelper
import com.lancefer.lancedrop.data.remote.*
import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.TransferHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * État complet de l'interface utilisateur de l'explorateur distant.
 */
data class RemoteExplorerUiState(
    val host: String = "127.0.0.1",
    val port: Int = 42421,
    val token: String = "",
    val peerName: String = "PC distant",
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val shares: List<RemoteShare> = emptyList(),
    val selectedShare: RemoteShare? = null,
    val currentListing: RemoteDirectoryListing? = null,
    val items: List<RemoteFileItem> = emptyList(),
    val filteredItems: List<RemoteFileItem> = emptyList(),
    val searchQuery: String = "",
    val sortMode: String = "name", // name, size, mtime
    val backStack: List<String> = emptyList(), // historique des sous-chemins
    val activeMediaItem: RemoteFileItem? = null,
    val activePhotoItem: RemoteFileItem? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytesToDownload: Long = 0L,
    val downloadMessage: String? = null
)

/**
 * ViewModel pour l'explorateur de fichiers distant et le streaming multimédia.
 */
class RemoteExplorerViewModel @JvmOverloads constructor(
    application: Application,
    private val client: RemoteExplorerClient = RemoteExplorerClient()
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, RemoteExplorerClient())

    private val _uiState = MutableStateFlow(RemoteExplorerUiState())
    val uiState: StateFlow<RemoteExplorerUiState> = _uiState.asStateFlow()

    private val historyDb = try {
        HistoryDatabaseHelper(application)
    } catch (_: Exception) {
        null
    }

    /**
     * Initialise la connexion vers un poste distant.
     */
    fun configureConnection(
        host: String,
        port: Int = 42421,
        token: String = "",
        peerName: String = "PC distant"
    ) {
        _uiState.update {
            it.copy(
                host = host.ifBlank { "127.0.0.1" },
                port = if (port > 0) port else 42421,
                token = token,
                peerName = peerName,
                errorMessage = null
            )
        }
        loadShares()
    }

    /**
     * Initialise la connexion à partir d'un DiscoveredPeer de LanceDrop.
     */
    fun connectToPeer(peer: DiscoveredPeer, token: String = "", httpPort: Int = 42421) {
        configureConnection(
            host = peer.ipAddress,
            port = httpPort,
            token = token,
            peerName = peer.name
        )
    }

    /**
     * Récupère la liste des dossiers partagés autorisés sur le PC.
     */
    fun loadShares() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            try {
                val shares = client.fetchShares(state.host, state.port, state.token)
                _uiState.update {
                    it.copy(
                        shares = shares,
                        isLoading = false,
                        isConnected = true,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Impossible de contacter le PC : ${e.message ?: "Erreur réseau"}"
                    )
                }
            }
        }
    }

    /**
     * Sélectionne un partage et charge son répertoire racine.
     */
    fun selectShare(share: RemoteShare) {
        _uiState.update {
            it.copy(
                selectedShare = share,
                backStack = emptyList(),
                searchQuery = "",
                errorMessage = null
            )
        }
        browsePath(subpath = "")
    }

    /**
     * Quitte le partage actuel et revient à la liste des partages.
     */
    fun closeShare() {
        _uiState.update {
            it.copy(
                selectedShare = null,
                currentListing = null,
                items = emptyList(),
                filteredItems = emptyList(),
                backStack = emptyList(),
                searchQuery = "",
                errorMessage = null
            )
        }
    }

    /**
     * Entre dans un sous-dossier.
     */
    fun enterFolder(item: RemoteFileItem) {
        if (!item.isDirectory) return
        val currentSubpath = _uiState.value.currentListing?.currentPath.orEmpty()
        _uiState.update {
            it.copy(
                backStack = it.backStack + currentSubpath,
                searchQuery = ""
            )
        }
        browsePath(subpath = item.subpath)
    }

    /**
     * Remonte d'un niveau dans l'arborescence (ou quitte le partage si à la racine).
     * @return true si une navigation arrière a été effectuée, false si on est déjà au sommet.
     */
    fun navigateUp(): Boolean {
        val state = _uiState.value
        if (state.activeMediaItem != null) {
            closeMedia()
            return true
        }
        if (state.activePhotoItem != null) {
            closePhoto()
            return true
        }
        if (state.backStack.isNotEmpty()) {
            val previousPath = state.backStack.last()
            val newStack = state.backStack.dropLast(1)
            _uiState.update { it.copy(backStack = newStack, searchQuery = "") }
            browsePath(subpath = previousPath)
            return true
        } else if (state.selectedShare != null) {
            closeShare()
            return true
        }
        return false
    }

    /**
     * Navigue directement vers un niveau du fil d'Ariane.
     */
    fun navigateToBreadcrumb(breadcrumb: BreadcrumbItem) {
        val state = _uiState.value
        if (breadcrumb.subpath == state.currentListing?.currentPath) return
        // Tronque le backstack
        val targetPath = breadcrumb.subpath
        val newStack = mutableListOf<String>()
        for (p in state.backStack) {
            if (p == targetPath) break
            newStack.add(p)
        }
        _uiState.update { it.copy(backStack = newStack, searchQuery = "") }
        browsePath(subpath = targetPath)
    }

    /**
     * Rafraîchit l'affichage en cours.
     */
    fun refresh() {
        val state = _uiState.value
        if (state.selectedShare != null) {
            val currentPath = state.currentListing?.currentPath.orEmpty()
            browsePath(subpath = currentPath)
        } else {
            loadShares()
        }
    }

    /**
     * Applique un filtre de recherche textuel sur les éléments affichés.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.items
            } else {
                state.items.filter { it.name.contains(query, ignoreCase = true) }
            }
            state.copy(searchQuery = query, filteredItems = filtered)
        }
    }

    /**
     * Modifie le mode de tri.
     */
    fun setSortMode(sort: String) {
        _uiState.update { it.copy(sortMode = sort) }
        val currentPath = _uiState.value.currentListing?.currentPath.orEmpty()
        browsePath(subpath = currentPath)
    }

    /**
     * Charge le contenu d'un répertoire donné.
     */
    private fun browsePath(subpath: String, page: Int = 1) {
        val share = _uiState.value.selectedShare ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val listing = client.browseDirectory(
                    host = state.host,
                    port = state.port,
                    shareId = share.id,
                    subpath = subpath,
                    page = page,
                    limit = 100,
                    sort = state.sortMode,
                    token = state.token
                )
                _uiState.update { s ->
                    val filtered = if (s.searchQuery.isBlank()) {
                        listing.items
                    } else {
                        listing.items.filter { it.name.contains(s.searchQuery, ignoreCase = true) }
                    }
                    s.copy(
                        currentListing = listing,
                        items = listing.items,
                        filteredItems = filtered,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Erreur d'accès : ${e.message ?: "Impossible de lire le dossier"}"
                    )
                }
            }
        }
    }

    fun openMedia(item: RemoteFileItem) {
        _uiState.update { it.copy(activeMediaItem = item, activePhotoItem = null) }
    }

    fun closeMedia() {
        _uiState.update { it.copy(activeMediaItem = null) }
    }

    fun openPhoto(item: RemoteFileItem) {
        _uiState.update { it.copy(activePhotoItem = item, activeMediaItem = null) }
    }

    fun closePhoto() {
        _uiState.update { it.copy(activePhotoItem = null) }
    }

    /**
     * Construit l'URL de streaming pour l'élément donné.
     */
    fun buildStreamUrl(item: RemoteFileItem): String {
        val state = _uiState.value
        val shareId = state.selectedShare?.id.orEmpty()
        return client.buildStreamUrl(state.host, state.port, shareId, item.subpath, state.token)
    }

    /**
     * Construit l'URL de la vignette pour l'élément donné.
     */
    fun buildThumbnailUrl(item: RemoteFileItem, size: Int = 256): String {
        val state = _uiState.value
        val shareId = state.selectedShare?.id.orEmpty()
        return client.buildThumbnailUrl(state.host, state.port, shareId, item.subpath, state.token, size)
    }

    /**
     * Télécharge un fichier distant et l'enregistre dans le dossier Downloads/LanceDrop de l'appareil.
     */
    fun downloadFile(
        context: Context,
        item: RemoteFileItem,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (item.isDirectory) return
        val state = _uiState.value
        val shareId = state.selectedShare?.id.orEmpty()
        val streamUrl = client.buildStreamUrl(state.host, state.port, shareId, item.subpath, state.token)

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    totalBytesToDownload = item.size,
                    downloadMessage = "Téléchargement de ${item.name}..."
                )
            }

            var downloadSuccess = false
            var createdUri: Uri? = null

            try {
                // Création de l'URI MediaStore Scoped Storage
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        if (item.mimeType.isNotBlank()) item.mimeType else "application/octet-stream"
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/LanceDrop"
                        )
                    }
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IllegalStateException("Impossible d'insérer dans MediaStore.Downloads")

                createdUri = uri

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    client.downloadFile(
                        streamUrl = streamUrl,
                        token = state.token,
                        outputStream = outputStream,
                        onProgress = { read, total ->
                            val progress = if (total > 0) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            _uiState.update {
                                it.copy(
                                    downloadProgress = progress,
                                    downloadedBytes = read,
                                    totalBytesToDownload = if (total > 0) total else item.size
                                )
                            }
                        }
                    )
                }

                downloadSuccess = true

                // Enregistrement dans la base d'historique de transferts
                historyDb?.insertOrUpdate(
                    TransferHistoryItem(
                        id = UUID.randomUUID().toString(),
                        fileName = item.name,
                        fileSize = item.size,
                        peerName = state.peerName,
                        timestamp = System.currentTimeMillis(),
                        isIncoming = true,
                        fileUri = uri,
                        status = "COMPLETED",
                        isDirectory = false
                    )
                )

                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 1f,
                        downloadMessage = "Fichier ${item.name} enregistré avec succès dans Téléchargements/LanceDrop !"
                    )
                }
            } catch (e: Exception) {
                if (createdUri != null) {
                    try { context.contentResolver.delete(createdUri, null, null) } catch (_: Exception) {}
                }
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadMessage = "Échec du téléchargement : ${e.message}"
                    )
                }
            } finally {
                onComplete?.invoke(downloadSuccess)
            }
        }
    }

    fun dismissErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissDownloadMessage() {
        _uiState.update { it.copy(downloadMessage = null) }
    }
}
