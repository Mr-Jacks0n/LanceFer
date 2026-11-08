package com.lancefer.lancedrop.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lancefer.lancedrop.telemetry.TelemetryManager
import com.lancefer.lancedrop.ui.screen.MainScreen
import com.lancefer.lancedrop.ui.theme.LanceDropTheme
import com.lancefer.lancedrop.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TelemetryManager.init(applicationContext)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isDark = when (uiState.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            LanceDropTheme(darkTheme = isDark) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.resumeDiscovery()
    }

    override fun onStop() {
        super.onStop()
        viewModel.pauseDiscovery()
    }
}
