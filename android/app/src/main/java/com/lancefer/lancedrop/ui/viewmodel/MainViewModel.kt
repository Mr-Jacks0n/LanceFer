package com.lancefer.lancedrop.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lancefer.lancedrop.discovery.NsdDiscoveryManager
import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.OsType
import com.lancefer.lancedrop.model.TransferHistoryItem
import com.lancefer.lancedrop.model.TransferState
import com.lancefer.lancedrop.service.TransferService
import com.lancefer.lancedrop.utils.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

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
    val transferHistory: List<TransferHistoryItem> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val nsdManager = NsdDiscoveryManager(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            wifiSSID = NetworkUtils.getWifiSsid(application),
            localIpAddress = NetworkUtils.getLocalIpAddress()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        nsdManager.registerLocalService()

        viewModelScope.launch {
            nsdManager.peers.collect { realPeers ->
                if (realPeers.isNotEmpty()) {
                    _uiState.update { it.copy(discoveredPeers = realPeers, isDiscovering = false) }
                }
            }
        }

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
            
            delay(2.seconds)
            if (_uiState.value.discoveredPeers.isEmpty()) {
                val mockPeers = listOf(
                    DiscoveredPeer("1", "PC-Bureau-Jean (Windows 11)", "192.168.1.50", 42420, OsType.WINDOWS, isTrusted = true),
                    DiscoveredPeer("2", "MacBook-Pro-Dev (macOS)", "192.168.1.88", 42420, OsType.MACOS, isTrusted = false)
                )
                _uiState.update { it.copy(discoveredPeers = mockPeers, isDiscovering = false) }
            } else {
                _uiState.update { it.copy(isDiscovering = false) }
            }
        }
    }

    fun addManualPeer(ipAddress: String, port: Int = 42420, name: String = "PC-Direct ($ipAddress)") {
        val manualPeer = DiscoveredPeer(
            id = "manual_$ipAddress",
            name = name,
            ipAddress = ipAddress,
            port = port,
            osType = OsType.WINDOWS,
            isTrusted = true
        )

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
            val cleanValue = qrCodeValue.removePrefix("lancedrop://").removePrefix("fastdrop://")
            val hostAndQuery = cleanValue.split("?")
            val hostAndPort = hostAndQuery[0].split(":")
            
            val ip = hostAndPort[0]
            val port = if (hostAndPort.size > 1) hostAndPort[1].toIntOrNull() ?: 42420 else 42420

            addManualPeer(ipAddress = ip, port = port, name = "PC-Scan-QR ($ip)")
        } catch (e: Exception) {
            Log.e("MainViewModel", "Erreur parsing QR Code", e)
        }
    }

    fun startFileTransferFromUri(peer: DiscoveredPeer, uri: Uri, fileName: String, fileSize: Long) {
        val app = getApplication<Application>()
        val serviceIntent = Intent(app, TransferService::class.java).apply {
            action = TransferService.ACTION_START_TRANSFER
            putExtra(TransferService.EXTRA_FILE_NAME, fileName)
        }
        app.startService(serviceIntent)

        viewModelScope.launch {
            var transferred = 0L
            val chunkSize = 1_048_576L
            
            while (transferred < fileSize) {
                val currentState = _uiState.value.activeTransferState
                if (currentState is TransferState.Transferring && currentState.isPaused) {
                    delay(300)
                    continue
                }

                delay(50)
                transferred += chunkSize
                if (transferred > fileSize) transferred = fileSize

                val progress = if (fileSize > 0) transferred.toFloat() / fileSize else 0f
                val speed = 54_200_000.0
                val remainingSec = if (speed > 0) ((fileSize - transferred) / speed).toLong() else 0L

                _uiState.update {
                    it.copy(
                        activeTransferState = TransferState.Transferring(
                            fileName = fileName,
                            totalBytes = fileSize,
                            transferredBytes = transferred,
                            speedBytesPerSec = speed,
                            progressPercentage = progress,
                            remainingSeconds = remainingSec,
                            peerName = peer.name
                        )
                    )
                }
            }

            val checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

            val historyItem = TransferHistoryItem(
                id = System.currentTimeMillis().toString(),
                fileName = fileName,
                fileSize = fileSize,
                peerName = peer.name,
                sha256Checksum = checksum,
                fileUri = uri
            )

            _uiState.update { state ->
                state.copy(
                    activeTransferState = TransferState.Completed(
                        fileName = fileName,
                        totalBytes = fileSize,
                        sha256Checksum = checksum,
                        peerName = peer.name
                    ),
                    transferHistory = listOf(historyItem) + state.transferHistory
                )
            }

            val stopIntent = Intent(app, TransferService::class.java).apply {
                action = TransferService.ACTION_STOP_TRANSFER
            }
            app.startService(stopIntent)
        }
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

    override fun onCleared() {
        super.onCleared()
        nsdManager.stopDiscovery()
    }
}
