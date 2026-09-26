package com.tactical.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tactical.app.service.TacticalMeshService
import com.tactical.app.ui.components.AppBottomNavigation
import com.tactical.app.ui.components.EmergencyRecordingDialog
import com.tactical.app.ui.screens.DevicesScreen
import com.tactical.app.ui.screens.MessagesScreen
import com.tactical.app.ui.screens.SquadScreen
import com.tactical.app.ui.screens.SettingsScreen
import com.tactical.app.ui.theme.RedTacticalBackground
import com.tactical.app.ui.theme.RedTacticalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private var startupCheckPending = false
    private var meshServiceStarted = false
    private val wirelessWarning = mutableStateOf<String?>(null)
    private val wirelessStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                ensureWirelessEnabled()
            }
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val criticalDenied = grants.any { (permission, granted) ->
            permission != Manifest.permission.POST_NOTIFICATIONS && !granted
        }

        if (!criticalDenied) {
            ensureWirelessEnabled()
            ensureVoiceModeIfPermissionGranted()
        } else {
            wirelessWarning.value = "Bluetooth / microphone permissions are required."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RedTacticalTheme {
                val state by viewModel.uiState.collectAsState()
                var selectedTab by remember { mutableIntStateOf(0) }
                var showSettings by remember { mutableStateOf(false) }
                val blueTheme = !showSettings && (selectedTab == 0 || selectedTab == 1)

                Scaffold(
                        topBar = {
                            TopAppBar(
                                navigationIcon = {
                                    if (showSettings) {
                                        IconButton(onClick = { showSettings = false }) {
                                            Icon(
                                                Icons.Default.ArrowBack,
                                                contentDescription = "Back",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                },
                                title = {
                                    androidx.compose.foundation.layout.Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (showSettings) "Settings" else "Itantra",
                                            color = Color.White
                                        )
                                        if (!showSettings && !blueTheme) {
                                            androidx.compose.foundation.layout.Spacer(
                                                Modifier.width(6.dp)
                                            )
                                            Surface(
                                                color = Color(0xFF3A1414),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = state.selectedLanguage,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    modifier = Modifier.padding(
                                                        horizontal = 7.dp,
                                                        vertical = 4.dp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                                actions = {
                                    if (!showSettings) {
                                        IconButton(onClick = { showSettings = true }) {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = if (blueTheme) {
                                        com.tactical.app.ui.theme.SquadBlueBackground
                                    } else {
                                        RedTacticalBackground
                                    },
                                    titleContentColor = Color.White
                                )
                            )
                        },
                        bottomBar = {
                            if (!showSettings) {
                                AppBottomNavigation(
                                    selectedTab = selectedTab,
                                    unreadMessageCount = state.unreadMessageCount,
                                    onTabSelected = { tab -> selectedTab = tab },
                                    squadTheme = blueTheme
                                )
                            }
                        },
                        containerColor = if (blueTheme) {
                            com.tactical.app.ui.theme.SquadBlueBackground
                        } else {
                            RedTacticalBackground
                        }
                    ) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            if (showSettings) {
                                SettingsScreen(
                                    selectedLanguageCode = state.selectedLanguageCode,
                                    languageLoadingCode = state.languageLoadingCode,
                                    onLanguageSelected = viewModel::setSelectedLanguage,
                                    ttsPlaybackMode = state.ttsPlaybackMode,
                                    onTtsPlaybackModeSelected = viewModel::setTtsPlaybackMode,
                                    username = state.username,
                                    onUsernameSave = viewModel::setUsername
                                )
                            } else {
                                when (selectedTab) {
                                    0 -> DevicesScreen(
                                        uiState = state,
                                        onScan = viewModel::forceDiscovery,
                                        onAddToSquad = viewModel::addPeerToSquad,
                                        onEmergencyPress = viewModel::startEmergencyHold,
                                        onEmergencyRelease = viewModel::releaseEmergencyHold
                                    )
                                1 -> SquadScreen(
                                    uiState = state,
                                    onLanguageSelected = viewModel::setSelectedLanguage,
                                    onPttToggle = {
                                        viewModel.setPttEnabled(!state.pttEnabled)
                                    },
                                    onPttPress = viewModel::pressPtt,
                                    onPttRelease = viewModel::releasePtt,
                                    onPttCancel = viewModel::cancelPtt,
                                    onRemoveFromSquad = viewModel::removePeerFromSquad
                                )
                                    2 -> MessagesScreen(
                                        state,
                                        viewModel::sendTextMessage,
                                        viewModel::markMessagesRead,
                                        viewModel::deleteMessages
                                    )
                                }
                            }

                            wirelessWarning.value?.let { message ->
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth(),
                                    color = Color(0xFF7A1F1F)
                                ) {
                                    Text(
                                        text = message,
                                        color = Color.White,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 10.dp
                                        )
                                    )
                                }
                            }

                            if (state.emergencyComposerVisible) {
                                EmergencyRecordingDialog(
                                    transcription = state.emergencyTranscription,
                                    isRecording = state.emergencyRecording,
                                    isSending = state.emergencySending,
                                    error = state.emergencyError,
                                    onSend = viewModel::sendEmergency,
                                    onCancel = viewModel::cancelEmergency
                                )
                            }

                            state.pendingSquadRequest?.let { request ->
                                val isResponding =
                                    state.respondingSquadRequestId == request.deviceId

                                AlertDialog(
                                    onDismissRequest = { },
                                    title = {
                                        Text(
                                            if (state.pendingSquadRequestCount > 1) {
                                                "SQUAD REQUEST 1/" +
                                                    state.pendingSquadRequestCount
                                            } else {
                                                "SQUAD REQUEST"
                                            }
                                        )
                                    },
                                    text = {
                                        androidx.compose.foundation.layout.Column {
                                            Text(
                                                request.callsign +
                                                    " wants to add you to their squad."
                                            )
                                            state.squadRequestError?.let { error ->
                                                androidx.compose.foundation.layout.Spacer(
                                                    Modifier.height(8.dp)
                                                )
                                                Text(
                                                    error,
                                                    color = Color(0xFFFF8A80),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                viewModel.respondToSquadRequest(
                                                    request.deviceId,
                                                    true
                                                )
                                            },
                                            enabled = !isResponding
                                        ) {
                                            Text(
                                                if (isResponding) {
                                                    "SENDING..."
                                                } else {
                                                    "APPROVE"
                                                }
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = {
                                                viewModel.respondToSquadRequest(
                                                    request.deviceId,
                                                    false
                                                )
                                            },
                                            enabled = !isResponding
                                        ) {
                                            Text("REJECT")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        requestStartupPermissions()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                wirelessStateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(wirelessStateReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(wirelessStateReceiver) }
        super.onStop()
    }

    private fun ensureVoiceModeIfPermissionGranted() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.ensureVoiceMode()
        }
    }

    private fun requestStartupPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.distinct()

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            ensureWirelessEnabled()
            ensureVoiceModeIfPermissionGranted()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private fun ensureWirelessEnabled() {
        if (isFinishing || isDestroyed) return

        val bluetoothOn =
            getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        wirelessWarning.value = if (bluetoothOn) {
            null
        } else {
            "Bluetooth is off. Turn it on."
        }

        if (bluetoothOn) {
            startupCheckPending = false
            if (!meshServiceStarted) {
                meshServiceStarted = true
                startMeshService()
            }
        } else {
            startupCheckPending = true
        }
    }

    private fun startMeshService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, TacticalMeshService::class.java)
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshReceivedMessages()
        ensureWirelessEnabled()
        ensureVoiceModeIfPermissionGranted()
    }
}
