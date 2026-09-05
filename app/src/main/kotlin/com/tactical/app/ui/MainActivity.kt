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
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            android.util.Log.w("MainActivity", "Some permissions denied: $denied")
        }

        // Only the permissions actually required to start scanning/advertising
        // for THIS API level — mirrors requiredTransportPermissions() below,
        // rather than hardcoding ACCESS_FINE_LOCATION which isn't requested
        // (or needed) at all on API 33+.
        val transportPermissionsGranted = requiredTransportPermissions()
            .all { result[it] == true }

        if (transportPermissionsGranted) {
            startMeshService()
        } else {
            android.util.Log.e(
                "MainActivity",
                "Mesh service not started: required transport permissions were denied"
            )
        }
    }

    private fun requiredTransportPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return permissions
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
        startForegroundService(intent)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        permissions += requiredTransportPermissions()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissions.distinct().toTypedArray())
    }
}
