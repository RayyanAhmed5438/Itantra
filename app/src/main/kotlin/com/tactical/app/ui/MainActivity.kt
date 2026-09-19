package com.tactical.app.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import com.tactical.platform.api.ble.BleLinkState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tactical.app.service.TacticalMeshService
import com.tactical.app.ui.components.*
import com.tactical.app.ui.screens.*
import com.tactical.app.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var startupCheckPending = false
    private var meshServiceStarted = false
    private val wirelessWarning = mutableStateOf<String?>(null)

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            ensureWirelessEnabled()
        } else {
            wirelessWarning.value = "Bluetooth / Wi-Fi permissions are required."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RedTacticalTheme {
                val state by viewModel.uiState.collectAsState()
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Itantra",
                                    color = Color.White
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = RedTacticalBackground,
                                titleContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        AppBottomNavigation(
                            selectedTab = selectedTab,
                            onTabSelected = { tab -> selectedTab = tab }
                        )
                    },
                    containerColor = RedTacticalBackground
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (selectedTab) {
                            0 -> DevicesScreen(
                                uiState = state,
                                onScan = viewModel::forceDiscovery,
                                onPair = viewModel::pairPeer
                            )
                            1 -> SquadScreen(state, onRefresh = viewModel::forceDiscovery)
                            2 -> MessagesScreen(state, viewModel::sendTextMessage)
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
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        requestStartupPermissions()
    }

    private fun requestStartupPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                // Required by Wi-Fi Direct on Android 12L and lower.
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

        if (bluetoothOn && wifiOn) {
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
