package com.lancefer.lancedrop.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lancefer.lancedrop.core.SocketTransferEngine
import com.lancefer.lancedrop.core.SocketTransferServer
import com.lancefer.lancedrop.data.HistoryDatabaseHelper
import com.lancefer.lancedrop.discovery.NsdDiscoveryManager
import com.lancefer.lancedrop.discovery.UdpDiscoveryManager
import com.lancefer.lancedrop.model.DeviceInfo
import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.OsType
import com.lancefer.lancedrop.model.TransferHistoryItem
import com.lancefer.lancedrop.model.TransferState
import com.lancefer.lancedrop.service.TransferService
import com.lancefer.lancedrop.utils.DeviceMetadataHelper
import com.lancefer.lancedrop.utils.MediaCategoryStats
import com.lancefer.lancedrop.utils.NetworkUtils
import com.lancefer.lancedrop.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val usedPercentage: Float,
    val totalFormatted: String,
    val usedFormatted: String,
    val freeFormatted: String
)

data class ConflictDialogState(
    val fileName: String,
    val onResolve: (String) -> Unit
)

data class MainUiState(
    val wifiSSID: String = "Wi-Fi Connecté",
    val localIpAddress: String = "127.0.0.1",
    val isHotspotActive: Boolean = false,
    val isDiscovering: Boolean = true,
    val discoveredPeers: List<DiscoveredPeer> = emptyList(),
    val activeTransferState: TransferState = TransferState.Idle,
    val pairingPinCode: String? = null,
    val isManualIpDialogVisible: Boolean = false,
    val isQrCodeDialogVisible: Boolean = false,
    val isCameraScannerVisible: Boolean = false,
    val selectedPeerForTransfer: DiscoveredPeer? = null,
    val transferHistory: List<TransferHistoryItem> = emptyList(),
    val interruptedTransfer: TransferHistoryItem? = null,
    val themeMode: String = "AUTO", // "AUTO", "LIGHT", "DARK"
    val soundEffectsEnabled: Boolean = true,
    val conflictResolutionMode: String = "RENAME", // "PROMPT", "RENAME", "OVERWRITE", "SKIP"
    val conflictDialogState: ConflictDialogState? = null,
    val deviceInfo: DeviceInfo? = null,
    val mediaStats: Map<String, MediaCategoryStats> = emptyMap()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("lancedrop_prefs", android.content.Context.MODE_PRIVATE)
    private val nsdManager = NsdDiscoveryManager(application)
    private val udpDiscoveryManager = UdpDiscoveryManager(application)
    private val historyDbHelper = HistoryDatabaseHelper(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            wifiSSID = NetworkUtils.getWifiSsid(application),
            localIpAddress = NetworkUtils.getLocalIpAddress(),
            themeMode = application.getSharedPreferences("lancedrop_prefs", android.content.Context.MODE_PRIVATE).getString("theme_mode", "AUTO") ?: "AUTO"
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var transferServer: SocketTransferServer? = null
    private var lastSendNotifTime = 0L
    private var lastRecvNotifTime = 0L
    private var lastSendUiUpdateTime = 0L
    private var lastRecvUiUpdateTime = 0L

    init {
        val authenticDevice = DeviceMetadataHelper.getDeviceInfo(application)
        val savedDeviceName = prefs.getString("device_name", null)
        val finalDevice = if (!savedDeviceName.isNullOrBlank()) {
            authenticDevice.copy(deviceName = savedDeviceName, model = savedDeviceName)
        } else {
            authenticDevice
        }
        _uiState.update { it.copy(deviceInfo = finalDevice) }
        refreshMediaStats()

        nsdManager.registerLocalService()

        viewModelScope.launch {
            nsdManager.peers.collect { realPeers ->
                val filteredRealPeers = realPeers.filterNot { isSelfPeer(it) }
                if (filteredRealPeers.isNotEmpty()) {
                    _uiState.update { state ->
                        val incomingIps = filteredRealPeers.map { it.ipAddress }.toSet()
                        val kept = state.discoveredPeers.filterNot { it.ipAddress in incomingIps || isSelfPeer(it) }
                        state.copy(discoveredPeers = kept + filteredRealPeers, isDiscovering = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            udpDiscoveryManager.peers.collect { udpPeers ->
                val filteredUdpPeers = udpPeers.filterNot { isSelfPeer(it) }
                if (filteredUdpPeers.isNotEmpty()) {
                    _uiState.update { state ->
                        val incomingIps = filteredUdpPeers.map { it.ipAddress }.toSet()
                        val kept = state.discoveredPeers.filterNot { it.ipAddress in incomingIps || isSelfPeer(it) }
                        state.copy(discoveredPeers = kept + filteredUdpPeers, isDiscovering = false)
                    }
                }
            }
        }

        // Chargement de l'historique persistant et détection des transferts interrompus
        viewModelScope.launch(Dispatchers.IO) {
            val savedHistory = historyDbHelper.getAllHistory()
            val interruptedList = historyDbHelper.getInterruptedTransfers()
            _uiState.update { state ->
                state.copy(
                    transferHistory = savedHistory,
                    interruptedTransfer = interruptedList.firstOrNull()
                )
            }
        }

        // Écoute des actions provenant des boutons de notification
        viewModelScope.launch {
            TransferService.actionEvents.collect { action ->
                when (action) {
                    TransferService.ACTION_PAUSE_TRANSFER -> pauseTransfer()
                    TransferService.ACTION_RESUME_TRANSFER -> resumeTransfer()
                    TransferService.ACTION_CANCEL_TRANSFER -> cancelTransfer()
                }
            }
        }

        startTransferServer()
        startDiscovery()
    }

    fun startDiscovery() {
        viewModelScope.launch {
            val realIp = NetworkUtils.getLocalIpAddress()
            val realSsid = NetworkUtils.getWifiSsid(getApplication())

            _uiState.update {
                it.copy(
                    isDiscovering = true,
                    localIpAddress = realIp,
                    wifiSSID = realSsid
                )
            }

            nsdManager.startDiscovery()
            udpDiscoveryManager.start()

            delay(3.seconds)
            _uiState.update { it.copy(isDiscovering = false) }
        }
    }

    fun refreshMediaStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = DeviceMetadataHelper.getMediaStats(getApplication())
            _uiState.update { it.copy(mediaStats = stats) }
        }
    }

    fun getRealStorageInfo(): StorageInfo {
        val metrics = DeviceMetadataHelper.getStorageMetrics()
        return StorageInfo(
            totalBytes = metrics.totalBytes,
            usedBytes = metrics.usedBytes,
            freeBytes = metrics.freeBytes,
            usedPercentage = metrics.usedPercentage,
            totalFormatted = metrics.totalFormatted,
            usedFormatted = metrics.usedFormatted,
            freeFormatted = metrics.freeFormatted
        )
    }

    fun isSelfPeer(peer: DiscoveredPeer): Boolean {
        if (NetworkUtils.isSelfIp(peer.ipAddress)) return true
        if (peer.id.startsWith("LanceDrop-Android") || peer.name.startsWith("LanceDrop-Android")) return true
        val info = _uiState.value.deviceInfo
        if (info != null) {
            if (peer.name.equals(info.deviceName, ignoreCase = true) ||
                peer.name.equals(info.fullDisplayName, ignoreCase = true) ||
                peer.name.equals(info.model, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun addManualPeer(ipAddress: String, port: Int = 42420, name: String = "PC-Direct ($ipAddress)") {
        if (NetworkUtils.isSelfIp(ipAddress)) {
            Log.w("MainViewModel", "Tentative d'ajout manuel de l'adresse IP locale ignorée : $ipAddress")
            _uiState.update { state ->
                state.copy(
                    isManualIpDialogVisible = false,
                    isCameraScannerVisible = false
                )
            }
            return
        }

        val manualPeer = DiscoveredPeer(
            id = "manual_$ipAddress",
            name = name,
            ipAddress = ipAddress,
            port = port,
            osType = OsType.WINDOWS,
            isTrusted = true
        )

        SoundEffectManager.playPairingTone()

        _uiState.update { state ->
            val updatedList = state.discoveredPeers.filterNot { it.ipAddress == ipAddress } + manualPeer
            state.copy(
                discoveredPeers = updatedList,
                isManualIpDialogVisible = false,
                isCameraScannerVisible = false
            )
        }
    }

    fun onQrCodeScanned(qrCodeValue: String) {
        Log.d("MainViewModel", "QR Code Scanné : $qrCodeValue")
        try {
            val cleanValue = qrCodeValue.trim()
            val decrypted = if (cleanValue.startsWith("LANCEDROP:SEC:v1:")) {
                com.lancefer.lancedrop.utils.QrSecurityCrypto.decryptQrPayload(cleanValue) ?: cleanValue
            } else {
                cleanValue
            }

            if (decrypted.startsWith("{")) {
                val json = org.json.JSONObject(decrypted)
                val ip = json.optString("ip").trim()
                val port = json.optInt("port", 42420)
                val name = json.optString("name", "PC ($ip)")
                val pin = json.optString("pin", "")
                val sessionKey = json.optString("sessionKey", "")

                if (ip.isNotEmpty() && ip != "0.0.0.0" && !ip.startsWith("{")) {
                    addManualPeer(ipAddress = ip, port = port, name = name)
                }
            } else {
                val stripped = decrypted.removePrefix("lancedrop://").removePrefix("fastdrop://")
                val hostAndQuery = stripped.split("?")
                val hostAndPort = hostAndQuery[0].split(":")

                val ip = hostAndPort[0].trim()
                val port = if (hostAndPort.size > 1) hostAndPort[1].toIntOrNull() ?: 42420 else 42420

                if (ip.isNotEmpty() && !ip.startsWith("{")) {
                    addManualPeer(ipAddress = ip, port = port, name = "PC-Scan-QR ($ip)")
                }
            }
            _uiState.update { it.copy(isCameraScannerVisible = false) }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Erreur parsing QR Code", e)
            _uiState.update { it.copy(isCameraScannerVisible = false) }
        }
    }

    fun startFileTransferFromUri(peer: DiscoveredPeer, uri: Uri, fileName: String, fileSize: Long) {
        val app = getApplication<Application>()
        val transferId = "tr-${System.currentTimeMillis()}-${(1000..9999).random()}"

        // Marquer le transfert dans la DB comme INTERRUPTED au départ pour reprise en cas de crash
        val initialHistory = TransferHistoryItem(
            id = transferId,
            fileName = fileName,
            fileSize = fileSize,
            peerName = peer.name,
            fileUri = uri,
            isIncoming = false,
            status = "INTERRUPTED"
        )
        viewModelScope.launch(Dispatchers.IO) {
            historyDbHelper.insertOrUpdate(initialHistory)
        }

        val serviceIntent = Intent(app, TransferService::class.java).apply {
            action = TransferService.ACTION_START_TRANSFER
            putExtra(TransferService.EXTRA_FILE_NAME, fileName)
        }
        ContextCompat.startForegroundService(app, serviceIntent)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Calcul SHA-256 optimisé : à la volée pendant le streaming pour les gros fichiers (> 20 Mo) pour éviter le blocage RAM/I-O
                val precomputedSha256 = if (fileSize in 1..(20 * 1024 * 1024L)) {
                    app.contentResolver.openInputStream(uri)?.use { s ->
                        val md = MessageDigest.getInstance("SHA-256")
                        val b = ByteArray(64 * 1024)
                        var r: Int
                        while (s.read(b).also { r = it } != -1) {
                            md.update(b, 0, r)
                        }
                        md.digest().joinToString("") { "%02x".format(it) }
                    }
                } else null

                val inputStream = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Fichier inaccessible")

                val sendResult = SocketTransferEngine.sendStreamFdspResult(
                    targetIp = peer.ipAddress,
                    targetPort = peer.port,
                    fileName = fileName,
                    fileSize = fileSize,
                    inputStream = inputStream,
                    precomputedSha256 = precomputedSha256,
                    transferIdOverride = transferId,
                    onProgress = { progress ->
                        val now = System.currentTimeMillis()
                        if (now - lastSendUiUpdateTime >= 150L || progress.progressPercentage >= 1.0f || progress.progressPercentage == 0f) {
                            lastSendUiUpdateTime = now
                            _uiState.update { it.copy(activeTransferState = progress) }
                        }
                        if (now - lastSendNotifTime >= 300L || progress.progressPercentage >= 1.0f) {
                            lastSendNotifTime = now
                            val speedText = String.format(java.util.Locale.FRANCE, "%.1f Mo/s", progress.speedBytesPerSec / (1024.0 * 1024.0))
                            val updateIntent = Intent(app, TransferService::class.java).apply {
                                action = TransferService.ACTION_UPDATE_PROGRESS
                                putExtra(TransferService.EXTRA_FILE_NAME, fileName)
                                putExtra(TransferService.EXTRA_PROGRESS_PERCENT, (progress.progressPercentage * 100).toInt())
                                putExtra(TransferService.EXTRA_SPEED_TEXT, speedText)
                                putExtra(TransferService.EXTRA_IS_PAUSED, progress.isPaused)
                            }
                            try {
                                ContextCompat.startForegroundService(app, updateIntent)
                            } catch (_: Exception) {}
                        }
                    }
                )

                val completedHistory = TransferHistoryItem(
                    id = transferId,
                    fileName = fileName,
                    fileSize = fileSize,
                    peerName = peer.name,
                    sha256Checksum = sendResult.sha256,
                    fileUri = uri,
                    isIncoming = false,
                    durationMs = sendResult.durationMs,
                    averageSpeedBytesPerSec = sendResult.averageSpeedBytesPerSec,
                    peakSpeedBytesPerSec = sendResult.peakSpeedBytesPerSec,
                    status = "COMPLETED"
                )
                historyDbHelper.insertOrUpdate(completedHistory)

                _uiState.update { state ->
                    state.copy(
                        activeTransferState = TransferState.Completed(
                            fileName = fileName,
                            totalBytes = fileSize,
                            sha256Checksum = sendResult.sha256,
                            peerName = peer.name
                        ),
                        transferHistory = listOf(completedHistory) + state.transferHistory.filterNot { it.id == transferId },
                        interruptedTransfer = if (state.interruptedTransfer?.id == transferId) null else state.interruptedTransfer
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(activeTransferState = TransferState.Error(e.message ?: "Échec du transfert"))
                }
            } finally {
                val stopIntent = Intent(app, TransferService::class.java).apply {
                    action = TransferService.ACTION_STOP_TRANSFER
                }
                app.startService(stopIntent)
            }
        }
    }

    fun resumeInterruptedTransfer(item: TransferHistoryItem) {
        val peer = _uiState.value.discoveredPeers.firstOrNull { it.name == item.peerName }
            ?: _uiState.value.discoveredPeers.firstOrNull()
        if (peer != null && item.fileUri != null) {
            startFileTransferFromUri(peer, item.fileUri, item.fileName, item.fileSize)
            _uiState.update { it.copy(interruptedTransfer = null) }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                historyDbHelper.updateTransferStatus(item.id, "CANCELLED")
            }
            _uiState.update { it.copy(interruptedTransfer = null) }
        }
    }

    fun dismissInterruptedTransfer(item: TransferHistoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDbHelper.updateTransferStatus(item.id, "CANCELLED")
        }
        _uiState.update { it.copy(interruptedTransfer = null) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDbHelper.clearHistory()
        }
        _uiState.update { it.copy(transferHistory = emptyList()) }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDbHelper.deleteHistoryItem(id)
        }
        _uiState.update { state ->
            state.copy(transferHistory = state.transferHistory.filterNot { it.id == id })
        }
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setCustomDeviceName(name: String) {
        prefs.edit().putString("device_name", name).putBoolean("is_configured", true).apply()
        _uiState.update { state ->
            val updated = state.deviceInfo?.copy(deviceName = name, model = name)
            state.copy(deviceInfo = updated)
        }
    }

    fun isUserConfigured(): Boolean {
        return prefs.getBoolean("is_configured", false) || prefs.getString("device_name", null) != null
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        SoundEffectManager.isSoundEnabled = enabled
        _uiState.update { it.copy(soundEffectsEnabled = enabled) }
    }

    fun setConflictResolutionMode(mode: String) {
        transferServer?.conflictResolution = mode
        _uiState.update { it.copy(conflictResolutionMode = mode) }
    }

    fun pauseTransfer() {
        _uiState.update { state ->
            val active = state.activeTransferState
            if (active is TransferState.Transferring) {
                state.copy(activeTransferState = active.copy(isPaused = true))
            } else state
        }
    }

    fun resumeTransfer() {
        _uiState.update { state ->
            val active = state.activeTransferState
            if (active is TransferState.Transferring) {
                state.copy(activeTransferState = active.copy(isPaused = false))
            } else state
        }
    }

    fun cancelTransfer() {
        val app = getApplication<Application>()
        val stopIntent = Intent(app, TransferService::class.java).apply {
            action = TransferService.ACTION_STOP_TRANSFER
        }
        app.startService(stopIntent)

        _uiState.update { it.copy(activeTransferState = TransferState.Idle) }
    }

    fun generatePairingPin() {
        val pin = (100000..999999).random().toString()
        _uiState.update { it.copy(pairingPinCode = pin) }
    }

    fun clearPairingPin() {
        _uiState.update { it.copy(pairingPinCode = null) }
    }

    fun setManualIpDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isManualIpDialogVisible = visible) }
    }

    fun setQrCodeDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isQrCodeDialogVisible = visible) }
    }

    fun setCameraScannerVisible(visible: Boolean) {
        _uiState.update { it.copy(isCameraScannerVisible = visible) }
    }

    fun setSelectedPeerForTransfer(peer: DiscoveredPeer?) {
        _uiState.update { it.copy(selectedPeerForTransfer = peer) }
    }

    suspend fun promptConflictResolution(fileName: String): String =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            _uiState.update { state ->
                state.copy(
                    conflictDialogState = ConflictDialogState(
                        fileName = fileName,
                        onResolve = { decision ->
                            _uiState.update { it.copy(conflictDialogState = null) }
                            if (cont.isActive) {
                                cont.resumeWith(Result.success(decision))
                            }
                        }
                    )
                )
            }
            cont.invokeOnCancellation {
                _uiState.update { it.copy(conflictDialogState = null) }
            }
        }

    fun resolveConflict(action: String) {
        _uiState.value.conflictDialogState?.onResolve?.invoke(action)
    }

    fun dismissConflictDialog() {
        resolveConflict("SKIP")
    }

    fun startTransferServer() {
        if (transferServer != null) return
        val app = getApplication<Application>()
        transferServer = SocketTransferServer(
            context = app,
            port = 42420,
            conflictResolution = _uiState.value.conflictResolutionMode,
            onConflictPrompt = { fileName ->
                promptConflictResolution(fileName)
            },
            onProgress = { progress ->
                val now = System.currentTimeMillis()
                if (now - lastRecvUiUpdateTime >= 150L || progress.progressPercentage >= 1.0f || progress.progressPercentage == 0f) {
                    lastRecvUiUpdateTime = now
                    _uiState.update { it.copy(activeTransferState = progress) }
                }
                if (now - lastRecvNotifTime >= 300L || progress.progressPercentage >= 1.0f) {
                    lastRecvNotifTime = now
                    val speedText = String.format(java.util.Locale.FRANCE, "%.1f Mo/s", progress.speedBytesPerSec / (1024.0 * 1024.0))
                    val updateIntent = Intent(app, TransferService::class.java).apply {
                        action = TransferService.ACTION_UPDATE_PROGRESS
                        putExtra(TransferService.EXTRA_FILE_NAME, progress.fileName)
                        putExtra(TransferService.EXTRA_PROGRESS_PERCENT, (progress.progressPercentage * 100).toInt())
                        putExtra(TransferService.EXTRA_SPEED_TEXT, speedText)
                        putExtra(TransferService.EXTRA_IS_PAUSED, false)
                    }
                    try {
                        ContextCompat.startForegroundService(app, updateIntent)
                    } catch (_: Exception) {}
                }
            },
            onCompleted = { fileName, size, checksum, uri, durationMs, avgSpeed, peakSpeed, isDirectory ->
                val historyItem = TransferHistoryItem(
                    id = "tr-recv-${System.currentTimeMillis()}-${(1000..9999).random()}",
                    fileName = fileName,
                    fileSize = size,
                    peerName = "PC",
                    sha256Checksum = checksum,
                    fileUri = uri,
                    isIncoming = true,
                    durationMs = durationMs,
                    averageSpeedBytesPerSec = avgSpeed,
                    peakSpeedBytesPerSec = peakSpeed,
                    status = "COMPLETED",
                    isDirectory = isDirectory
                )
                viewModelScope.launch(Dispatchers.IO) {
                    historyDbHelper.insertOrUpdate(historyItem)
                }

                _uiState.update { state ->
                    state.copy(
                        activeTransferState = TransferState.Completed(
                            fileName = fileName,
                            totalBytes = size,
                            sha256Checksum = checksum,
                            peerName = "PC"
                        ),
                        transferHistory = listOf(historyItem) + state.transferHistory
                    )
                }
            },
            onError = { errMsg ->
                _uiState.update { it.copy(activeTransferState = TransferState.Error(errMsg)) }
            }
        )
        viewModelScope.launch(Dispatchers.IO) {
            transferServer?.start()
        }
    }

    fun stopTransferServer() {
        transferServer?.stop()
        transferServer = null
    }

    fun pauseDiscovery() {
        udpDiscoveryManager.pause()
    }

    fun resumeDiscovery() {
        udpDiscoveryManager.resume()
    }

    override fun onCleared() {
        super.onCleared()
        nsdManager.stopDiscovery()
        udpDiscoveryManager.stop()
        stopTransferServer()
    }
}
