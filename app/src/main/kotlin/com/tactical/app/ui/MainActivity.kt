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
    ) { result ->

        val allGranted = result.values.all { it }

        if (allGranted) {
            startMeshService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


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
                        topBar = { AppHeader(deviceCount = uiState.squadPeers.count { it.isConnected }) },
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
        checkAndRequestPermissions()
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
        val permissions = mutableListOf<String>()

        fun addIfMissing(permission: String) {
            if (
                checkSelfPermission(permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(permission)
            }
        }

        addIfMissing(Manifest.permission.RECORD_AUDIO)
        addIfMissing(Manifest.permission.ACCESS_FINE_LOCATION)
        addIfMissing(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(Manifest.permission.BLUETOOTH_SCAN)
            addIfMissing(Manifest.permission.BLUETOOTH_CONNECT)
            addIfMissing(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isEmpty()) {
            startMeshService()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
