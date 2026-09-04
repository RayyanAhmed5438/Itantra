package com.tactical.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.tactical.app.service.TacticalMeshService
import com.tactical.app.ui.components.*
import com.tactical.app.ui.screens.*
import com.tactical.app.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        startMeshService()

        setContent {
            RedTacticalTheme {
                val uiState by viewModel.uiState.collectAsState()
                var selectedTab by remember { mutableIntStateOf(0) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RedTacticalBackground)
                ) {
                    Scaffold(
                        topBar = { AppHeader() },
                        bottomBar = {
                            AppBottomNavigation(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it }
                            )
                        },
                        containerColor = RedTacticalBackground
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (selectedTab) {
                                0 -> PttHomeScreen(
                                    uiState = uiState,
                                    onLanguageSelected = { viewModel.selectLanguage(it) },
                                    onStartPtt = { viewModel.startPtt() },
                                    onStopPtt = { viewModel.stopPttAndTransmit() },
                                    onToggleWalkieTalkie = { viewModel.toggleWalkieTalkie() },
                                    onToggleVox = { viewModel.toggleVox() },
                                    onStartEmergencyHold = { viewModel.startEmergencyHold() },
                                    onCancelEmergencyHold = { viewModel.cancelEmergencyHold() },
                                    onOpenWalkieTalkieScreen = { viewModel.showWalkieTalkieScreen(true) },
                                    onOpenVoxOverlay = { viewModel.showVoxListeningOverlay(true) }
                                )

                                1 -> SquadScreen(
                                    uiState = uiState,
                                    onRefresh = {}
                                )

                                2 -> MessagesScreen(
                                    uiState = uiState,
                                    onSendMessage = { viewModel.sendTextMessage(it) }
                                )
                            }

                            // Walkie Talkie Screen
                            if (uiState.isWalkieTalkieScreenVisible) {
                                WalkieTalkieScreen(
                                    onDismiss = { viewModel.showWalkieTalkieScreen(false) }
                                )
                            }

                            // VOX Listening Overlay
                            if (uiState.isVoxListeningOverlayVisible) {
                                VoxListeningOverlay(
                                    onDismiss = { viewModel.showVoxListeningOverlay(false) }
                                )
                            }
                        }
                    }

                    // Emergency Alert Modal Overlay
                    uiState.activeEmergencyAlert?.let { alert ->
                        EmergencyAlertDialog(
                            alertData = alert,
                            onAcknowledge = { viewModel.dismissEmergencyAlert() }
                        )
                    }
                }
            }
        }
    }

    private fun startMeshService() {
        val intent = Intent(this, TacticalMeshService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }
}
