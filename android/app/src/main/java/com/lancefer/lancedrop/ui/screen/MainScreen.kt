package com.lancefer.lancedrop.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.lancefer.lancedrop.model.TransferState
import com.lancefer.lancedrop.ui.theme.*
import com.lancefer.lancedrop.ui.viewmodel.MainViewModel

enum class AppNavScreen {
    SPLASH,
    LOGIN,
    HOME,
    FILES,
    TRANSFERS,
    STORAGE,
    RECEIVE_MODE,
    CLIPBOARD,
    FILE_PREVIEW,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf(AppNavScreen.SPLASH) }
    var selectedCategory by remember { mutableStateOf("Tous") }
    var previewFileName by remember { mutableStateOf("Paysage_Alpes.jpg") }
    var previewFileSize by remember { mutableStateOf("4,2 Mo") }

    // Gestion du bouton Retour Android matériel / gestuel
    BackHandler(enabled = currentScreen != AppNavScreen.HOME && currentScreen != AppNavScreen.LOGIN && currentScreen != AppNavScreen.SPLASH) {
        when (currentScreen) {
            AppNavScreen.RECEIVE_MODE, AppNavScreen.STORAGE, AppNavScreen.SETTINGS, AppNavScreen.CLIPBOARD -> {
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
            if (currentScreen in listOf(AppNavScreen.HOME, AppNavScreen.FILES, AppNavScreen.TRANSFERS, AppNavScreen.SETTINGS)) {
                NavigationBar(
                    containerColor = LanceDropCardLight,
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
        containerColor = LanceDropBgLight
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
                            currentScreen = AppNavScreen.HOME
                        }
                    )
                }

                AppNavScreen.LOGIN -> {
                    LoginScreen(
                        onLoginSuccess = {
                            currentScreen = AppNavScreen.HOME
                        }
                    )
                }

                AppNavScreen.HOME -> {
                    HomeScreen(
                        recentTransfers = uiState.transferHistory,
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
                        onNavigateToReceiveMode = {
                            currentScreen = AppNavScreen.RECEIVE_MODE
                        },
                        onNavigateToClipboard = {
                            currentScreen = AppNavScreen.CLIPBOARD
                        },
                        onNavigateToSettings = {
                            currentScreen = AppNavScreen.SETTINGS
                        }
                    )
                }

                AppNavScreen.FILES -> {
                    MyFilesScreen(
                        initialCategory = selectedCategory,
                        onNavigateToPreview = { fileName ->
                            previewFileName = fileName
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
                        fileSize = previewFileSize,
                        onNavigateBack = {
                            currentScreen = AppNavScreen.FILES
                        },
                        onSendToPc = {
                            val peer = uiState.selectedPeerForTransfer ?: uiState.discoveredPeers.firstOrNull()
                            if (peer != null) {
                                Toast.makeText(context, "Envoi de $previewFileName vers ${peer.name}...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Envoi vers le PC appairé...", Toast.LENGTH_SHORT).show()
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
                        }
                    )
                }
            }
        }
    }
}
