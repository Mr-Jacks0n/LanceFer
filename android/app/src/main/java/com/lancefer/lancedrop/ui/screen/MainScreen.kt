package com.lancefer.lancedrop.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lancefer.lancedrop.ui.remote.RemoteExplorerScreen
import com.lancefer.lancedrop.ui.remote.RemoteExplorerViewModel
import com.lancefer.lancedrop.model.TransferState
import com.lancefer.lancedrop.ui.components.CameraQrScannerDialog
import com.lancefer.lancedrop.ui.components.ConflictResolutionDialog
import com.lancefer.lancedrop.ui.components.ManualIpDialog
import com.lancefer.lancedrop.ui.components.PermissionRequestDialog
import com.lancefer.lancedrop.ui.theme.*
import com.lancefer.lancedrop.ui.viewmodel.MainViewModel
import com.lancefer.lancedrop.utils.FileHelper

enum class AppNavScreen {
    SPLASH,
    LOGIN,
    HOME,
    SEND_MODE,
    FILES,
    TRANSFERS,
    STORAGE,
    RECEIVE_MODE,
    CLIPBOARD,
    FILE_PREVIEW,
    SETTINGS,
    RemoteExplorer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val remoteExplorerViewModel: RemoteExplorerViewModel = viewModel()

    var currentScreen by remember { mutableStateOf(AppNavScreen.SPLASH) }
    var selectedCategory by remember { mutableStateOf("Tous") }
    var previewFileName by remember { mutableStateOf("") }
    var previewFileUri by remember { mutableStateOf<Uri?>(null) }
    var previewFilePath by remember { mutableStateOf<String?>(null) }
    var previewFileSize by remember { mutableStateOf("") }
    var previewFileDate by remember { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf(true) }

    LaunchedEffect(currentScreen, uiState.selectedPeerForTransfer, uiState.discoveredPeers) {
        if (currentScreen == AppNavScreen.RemoteExplorer) {
            val peer = uiState.selectedPeerForTransfer ?: uiState.discoveredPeers.firstOrNull()
            if (peer != null) {
                remoteExplorerViewModel.connectToPeer(peer)
            } else {
                remoteExplorerViewModel.loadShares()
            }
        }
    }

    // Demande automatique des permissions nécessaires dès l'ouverture de l'application
    val requiredPermissions = remember {
        val list = mutableListOf(
            android.Manifest.permission.CAMERA
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.POST_NOTIFICATIONS)
            list.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            list.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            list.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            list.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            list.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            list.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        list
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _: Map<String, Boolean> ->
        showPermissionDialog = false
    }

    LaunchedEffect(currentScreen) {
        if ((currentScreen == AppNavScreen.HOME || currentScreen == AppNavScreen.LOGIN) && showPermissionDialog) {
            val missing = requiredPermissions.filter {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                requestPermissionsLauncher.launch(missing.toTypedArray())
            } else {
                showPermissionDialog = false
            }
        }
    }
    BackHandler(enabled = currentScreen != AppNavScreen.HOME && currentScreen != AppNavScreen.LOGIN && currentScreen != AppNavScreen.SPLASH) {
        when (currentScreen) {
            AppNavScreen.RECEIVE_MODE, AppNavScreen.SEND_MODE, AppNavScreen.STORAGE, AppNavScreen.SETTINGS, AppNavScreen.CLIPBOARD, AppNavScreen.RemoteExplorer -> {
                currentScreen = AppNavScreen.HOME
            }
            AppNavScreen.FILE_PREVIEW -> {
                currentScreen = AppNavScreen.FILES
            }
            AppNavScreen.FILES, AppNavScreen.TRANSFERS -> {
                currentScreen = AppNavScreen.HOME
            }
            else -> {}
        }
    }

    Scaffold(
        bottomBar = {
            // Afficher la barre de navigation sur les écrans principaux
            if (currentScreen in listOf(AppNavScreen.HOME, AppNavScreen.FILES, AppNavScreen.TRANSFERS, AppNavScreen.SETTINGS, AppNavScreen.RemoteExplorer)) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    NavigationBarItem(
                        selected = currentScreen == AppNavScreen.HOME,
                        onClick = { currentScreen = AppNavScreen.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") },
                        label = { Text("Accueil", fontSize = 11.sp, fontWeight = if (currentScreen == AppNavScreen.HOME) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LanceDropBlue,
                            selectedTextColor = LanceDropBlue,
                            indicatorColor = LanceDropBlueLight,
                            unselectedIconColor = LanceDropTextMutedLight,
                            unselectedTextColor = LanceDropTextMutedLight
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppNavScreen.FILES,
                        onClick = {
                            selectedCategory = "Tous"
                            currentScreen = AppNavScreen.FILES
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Fichiers") },
                        label = { Text("Fichiers", fontSize = 11.sp, fontWeight = if (currentScreen == AppNavScreen.FILES) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LanceDropBlue,
                            selectedTextColor = LanceDropBlue,
                            indicatorColor = LanceDropBlueLight,
                            unselectedIconColor = LanceDropTextMutedLight,
                            unselectedTextColor = LanceDropTextMutedLight
                        )
                    )

                    val activeTransferCount = if (uiState.activeTransferState is TransferState.Transferring) 1 else 0
                    NavigationBarItem(
                        selected = currentScreen == AppNavScreen.TRANSFERS,
                        onClick = { currentScreen = AppNavScreen.TRANSFERS },
                        icon = {
                            if (activeTransferCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = LanceDropBlue) {
                                            Text("$activeTransferCount", color = Color.White)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.SwapVert, contentDescription = "Transferts")
                                }
                            } else {
                                Icon(Icons.Default.SwapVert, contentDescription = "Transferts")
                            }
                        },
                        label = { Text("Transferts", fontSize = 11.sp, fontWeight = if (currentScreen == AppNavScreen.TRANSFERS) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LanceDropBlue,
                            selectedTextColor = LanceDropBlue,
                            indicatorColor = LanceDropBlueLight,
                            unselectedIconColor = LanceDropTextMutedLight,
                            unselectedTextColor = LanceDropTextMutedLight
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppNavScreen.RemoteExplorer,
                        onClick = { currentScreen = AppNavScreen.RemoteExplorer },
                        icon = { Icon(Icons.Default.Dns, contentDescription = "Explorer PC") },
                        label = { Text("Explorer PC", fontSize = 11.sp, fontWeight = if (currentScreen == AppNavScreen.RemoteExplorer) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LanceDropBlue,
                            selectedTextColor = LanceDropBlue,
                            indicatorColor = LanceDropBlueLight,
                            unselectedIconColor = LanceDropTextMutedLight,
                            unselectedTextColor = LanceDropTextMutedLight
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppNavScreen.SETTINGS,
                        onClick = { currentScreen = AppNavScreen.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Paramètres") },
                        label = { Text("Paramètres", fontSize = 11.sp, fontWeight = if (currentScreen == AppNavScreen.SETTINGS) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LanceDropBlue,
                            selectedTextColor = LanceDropBlue,
                            indicatorColor = LanceDropBlueLight,
                            unselectedIconColor = LanceDropTextMutedLight,
                            unselectedTextColor = LanceDropTextMutedLight
                        )
                    )
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
            when (currentScreen) {
                AppNavScreen.SPLASH -> {
                    SplashScreen(
                        onTimeout = {
                            currentScreen = if (viewModel.isUserConfigured()) AppNavScreen.HOME else AppNavScreen.LOGIN
                        }
                    )
                }

                AppNavScreen.LOGIN -> {
                    LoginScreen(
                        onLoginSuccess = { chosenDeviceName ->
                            if (!chosenDeviceName.isNullOrBlank()) {
                                viewModel.setCustomDeviceName(chosenDeviceName)
                            }
                            currentScreen = AppNavScreen.HOME
                        }
                    )
                }

                AppNavScreen.HOME -> {
                    HomeScreen(
                        recentTransfers = uiState.transferHistory,
                        interruptedTransfer = uiState.interruptedTransfer,
                        onResumeInterrupted = { viewModel.resumeInterruptedTransfer(it) },
                        onDismissInterrupted = { viewModel.dismissInterruptedTransfer(it) },
                        discoveredPeers = uiState.discoveredPeers,
                        onSelectPeer = { peer ->
                            viewModel.setSelectedPeerForTransfer(peer)
                            selectedCategory = "Tous"
                            currentScreen = AppNavScreen.FILES
                        },
                        onAddDevice = {
                            viewModel.setManualIpDialogVisible(true)
                        },
                        onOpenQrScanner = {
                            viewModel.setCameraScannerVisible(true)
                        },
                        onNavigateToFiles = { category ->
                            selectedCategory = category ?: "Tous"
                            currentScreen = AppNavScreen.FILES
                        },
                        onNavigateToTransfers = {
                            currentScreen = AppNavScreen.TRANSFERS
                        },
                        onNavigateToStorage = {
                            currentScreen = AppNavScreen.STORAGE
                        },
                        onNavigateToSendMode = {
                            currentScreen = AppNavScreen.SEND_MODE
                        },
                        onNavigateToReceiveMode = {
                            currentScreen = AppNavScreen.RECEIVE_MODE
                        },
                        onNavigateToClipboard = {
                            currentScreen = AppNavScreen.CLIPBOARD
                        },
                        onNavigateToSettings = {
                            currentScreen = AppNavScreen.SETTINGS
                        },
                        deviceInfo = uiState.deviceInfo,
                        localIpAddress = uiState.localIpAddress,
                        wifiSSID = uiState.wifiSSID
                    )
                }

                AppNavScreen.SEND_MODE -> {
                    SendModeScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            currentScreen = AppNavScreen.HOME
                        },
                        onNavigateToTransfers = {
                            currentScreen = AppNavScreen.TRANSFERS
                        },
                        onNavigateToCameraScanner = {
                            viewModel.setCameraScannerVisible(true)
                        }
                    )
                }

                AppNavScreen.FILES -> {
                    MyFilesScreen(
                        initialCategory = selectedCategory,
                        viewModel = viewModel,
                        onNavigateToPreview = { name, uri, size, date ->
                            previewFileName = name
                            previewFileUri = uri
                            previewFilePath = null
                            previewFileSize = size
                            previewFileDate = date
                            currentScreen = AppNavScreen.FILE_PREVIEW
                        },
                        onNavigateToTransfers = {
                            currentScreen = AppNavScreen.TRANSFERS
                        },
                        onNavigateBack = {
                            currentScreen = AppNavScreen.HOME
                        }
                    )
                }

                AppNavScreen.TRANSFERS -> {
                    ActiveTransfersScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            currentScreen = AppNavScreen.HOME
                        }
                    )
                }

                AppNavScreen.STORAGE -> {
                    PhoneStorageScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            currentScreen = AppNavScreen.HOME
                        },
                        onCategoryClick = { category ->
                            selectedCategory = category
                            currentScreen = AppNavScreen.FILES
                        }
                    )
                }

                AppNavScreen.RECEIVE_MODE -> {
                    ReceiveModeScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            currentScreen = AppNavScreen.HOME
                        }
                    )
                }

                AppNavScreen.CLIPBOARD -> {
                    ClipboardScreen(
                        onNavigateBack = {
                            currentScreen = AppNavScreen.HOME
                        },
                        onSendTextToPc = { text ->
                            Toast.makeText(context, "Texte envoyé au PC connecté", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                AppNavScreen.FILE_PREVIEW -> {
                    FilePreviewScreen(
                        fileName = previewFileName,
                        fileUri = previewFileUri,
                        filePath = previewFilePath,
                        fileSize = previewFileSize,
                        fileDate = previewFileDate,
                        onNavigateBack = {
                            currentScreen = AppNavScreen.FILES
                        },
                        onSendToPc = {
                            val peer = uiState.selectedPeerForTransfer ?: uiState.discoveredPeers.firstOrNull()
                            if (peer != null) {
                                if (previewFileUri != null) {
                                    val sizeBytes = try { FileHelper.getFileSize(context, previewFileUri!!) } catch (_: Exception) { 0L }
                                    viewModel.startFileTransferFromUri(peer, previewFileUri!!, previewFileName, sizeBytes)
                                    Toast.makeText(context, "Envoi de $previewFileName vers ${peer.name}...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Envoi vers ${peer.name}...", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Aucun PC connecté. Utilisez le scanner QR ou ajoutez une IP.", Toast.LENGTH_SHORT).show()
                            }
                            currentScreen = AppNavScreen.TRANSFERS
                        }
                    )
                }

                AppNavScreen.SETTINGS -> {
                    SettingsScreen(
                        onNavigateBack = {
                            currentScreen = AppNavScreen.HOME
                        },
                        onLogout = {
                            currentScreen = AppNavScreen.LOGIN
                        },
                        themeMode = uiState.themeMode,
                        onThemeModeChange = { viewModel.setThemeMode(it) },
                        soundEnabled = uiState.soundEffectsEnabled,
                        onSoundEnabledChange = { viewModel.setSoundEffectsEnabled(it) },
                        conflictResolution = uiState.conflictResolutionMode,
                        onConflictResolutionChange = { viewModel.setConflictResolutionMode(it) },
                        onClearHistory = {
                            viewModel.clearHistory()
                            Toast.makeText(context, "Historique effacé avec succès", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                AppNavScreen.RemoteExplorer -> {
                    RemoteExplorerScreen(
                        viewModel = remoteExplorerViewModel,
                        onNavigateBack = {
                            currentScreen = AppNavScreen.HOME
                        }
                    )
                }
            }
        }
    }

    // Dialogue d'autorisations au premier démarrage
    if (showPermissionDialog) {
        PermissionRequestDialog(
            onDismiss = { showPermissionDialog = false },
            onAllPermissionsGranted = {
                showPermissionDialog = false
                viewModel.startDiscovery()
                viewModel.refreshMediaStats()
            }
        )
    }

    // Dialogue Scanner Caméra QR Code en temps réel
    if (uiState.isCameraScannerVisible) {
        CameraQrScannerDialog(
            onDismiss = { viewModel.setCameraScannerVisible(false) },
            onQrScanned = { qrContent ->
                viewModel.onQrCodeScanned(qrContent)
                viewModel.setCameraScannerVisible(false)
                Toast.makeText(context, "Appareil détecté via QR Code !", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialogue d'ajout manuel d'adresse IP
    if (uiState.isManualIpDialogVisible) {
        ManualIpDialog(
            onDismiss = { viewModel.setManualIpDialogVisible(false) },
            onConfirm = { ip, port ->
                viewModel.addManualPeer(ipAddress = ip, port = port, name = "PC ($ip)")
                viewModel.setManualIpDialogVisible(false)
                Toast.makeText(context, "Connexion ajoutée à $ip:$port", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialogue de résolution de conflits
    uiState.conflictDialogState?.let { conflictState ->
        ConflictResolutionDialog(
            fileName = conflictState.fileName,
            onResolve = { action ->
                viewModel.resolveConflict(action)
            },
            onDismiss = {
                viewModel.dismissConflictDialog()
            }
        )
    }
}
