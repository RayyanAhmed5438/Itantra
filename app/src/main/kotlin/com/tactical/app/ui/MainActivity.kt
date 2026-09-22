package com.tactical.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.lifecycleScope
import com.tactical.app.service.TacticalMeshService
import com.tactical.app.ui.components.AppBottomNavigation
import com.tactical.app.ui.components.EmergencyRecordingDialog
import com.tactical.app.ui.screens.DevicesScreen
import com.tactical.app.ui.screens.MessagesScreen
import com.tactical.app.ui.screens.SquadScreen
import com.tactical.app.ui.screens.SettingsScreen
import com.tactical.app.ui.screens.TtsTestScreen
import com.tactical.app.ui.theme.RedTacticalBackground
import com.tactical.app.ui.theme.RedTacticalTheme
import com.tactical.platform.speech.mms.MmsTtsEngine
import com.tactical.platform.speech.mms.MmsTtsModelStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var ttsModelStore: MmsTtsModelStore

    @Inject
    lateinit var mmsTtsEngine: MmsTtsEngine

    private var startupCheckPending = false
    private var meshServiceStarted = false
    private val wirelessWarning = mutableStateOf<String?>(null)
    private val ttsLoading = mutableStateOf(false)
    private val ttsMessage = mutableStateOf<String?>(null)
    private val ttsLanguages = mutableStateOf<List<com.tactical.platform.speech.mms.MmsTtsLanguage>>(emptyList())

    private fun loadBundledTtsModels() {
        if (ttsLoading.value) return
        lifecycleScope.launch {
            ttsLoading.value = true
            ttsMessage.value = "Loading bundled TTS models…"
            runCatching { ttsModelStore.ensureBundledModelsAvailable() }
                .onSuccess {
                    ttsLanguages.value = ttsModelStore.installedLanguages()
                    ttsMessage.value = "TTS models ready: " + ttsLanguages.value.size + " bundled languages."
                }
                .onFailure { error ->
                    ttsLanguages.value = ttsModelStore.installedLanguages()
                    ttsMessage.value = "TTS model load failed: " + (error.message ?: error.javaClass.simpleName)
                }
            ttsLoading.value = false
        }
    }

    private val wirelessStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                WifiManager.WIFI_STATE_CHANGED_ACTION -> ensureWirelessEnabled()
            }
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            ensureWirelessEnabled()
        } else {
            wirelessWarning.value = "Bluetooth / Wi-Fi / microphone permissions are required."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RedTacticalTheme {
                val state by viewModel.uiState.collectAsState()
                var selectedTab by remember { mutableIntStateOf(0) }
                var showTtsLab by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }

                LaunchedEffect(showTtsLab) {
                    if (showTtsLab) loadBundledTtsModels()
                }

                if (showTtsLab) {
                    TtsTestScreen(
                        installedLanguages = ttsLanguages.value,
                        isLoadingModels = ttsLoading.value,
                        modelMessage = ttsMessage.value,
                        onSpeak = { language, text ->
                            mmsTtsEngine.synthesizeAndPlay(language, text)
                        },
                        onDismiss = { showTtsLab = false }
                    )
                } else {
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
                                        if (!showSettings) {
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
                                        IconButton(onClick = { showTtsLab = true }) {
                                            Icon(
                                                Icons.Default.RecordVoiceOver,
                                                contentDescription = "TTS Model Lab",
                                                tint = Color.White
                                            )
                                        }
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
                                    containerColor = RedTacticalBackground,
                                    titleContentColor = Color.White
                                )
                            )
                        },
                        bottomBar = {
                            if (!showSettings) {
                                AppBottomNavigation(
                                    selectedTab = selectedTab,
                                    onTabSelected = { tab -> selectedTab = tab }
                                )
                            }
                        },
                        containerColor = RedTacticalBackground
                    ) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            if (showSettings) {
                                SettingsScreen(
                                    selectedLanguageCode = state.selectedLanguageCode,
                                    onLanguageSelected = viewModel::setSelectedLanguage,
                                    username = state.username,
                                    onUsernameSave = viewModel::setUsername
                                )
                            } else {
                                when (selectedTab) {
                                    0 -> DevicesScreen(
                                    uiState = state,
                                    onScan = viewModel::forceDiscovery,
                                    onPair = viewModel::pairPeer
                                )
                                1 -> SquadScreen(
                                    uiState = state,
                                    onRefresh = viewModel::forceDiscovery,
                                    onPttPress = viewModel::pressPtt,
                                    onPttRelease = viewModel::releasePtt,
                                    onEmergencyPress = viewModel::startEmergencyHold,
                                    onEmergencyRelease = viewModel::releaseEmergencyHold
                                )
                                    2 -> MessagesScreen(
                                        state,
                                        viewModel::sendTextMessage
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
                        }
                    }
                }
            }
        }

        requestStartupPermissions()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
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

    private fun requestStartupPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
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
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private fun ensureWirelessEnabled() {
        if (isFinishing || isDestroyed) return

        val bluetoothOn =
            getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
        val wifiOn =
            (getSystemService(WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true

        wirelessWarning.value = when {
            bluetoothOn && wifiOn -> null
            !bluetoothOn && !wifiOn -> "Bluetooth or Wi-Fi is off. Turn them on."
            !bluetoothOn -> "Bluetooth is off. Turn it on."
            else -> "Wi-Fi is off. Turn it on."
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
        ensureWirelessEnabled()
    }
}
