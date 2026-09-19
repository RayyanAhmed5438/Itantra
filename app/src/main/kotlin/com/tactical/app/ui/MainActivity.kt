package com.tactical.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tactical.app.service.TacticalMeshService
import com.tactical.app.ui.components.*
import com.tactical.app.ui.screens.*
import com.tactical.app.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) ensureWirelessEnabled() else openWirelessSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RedTacticalTheme {
                val state by viewModel.uiState.collectAsState()
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    topBar = {
                        AppHeader(deviceCount = state.squadPeers.size)
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
                                onScan = viewModel::startDiscovery
                            )
                            1 -> SquadScreen(
                                uiState = state,
                                onRefresh = viewModel::startDiscovery
                            )
                            2 -> MessagesScreen(
                                uiState = state,
                                onSendMessage = viewModel::sendTextMessage
                            )
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
            }

            add(Manifest.permission.ACCESS_FINE_LOCATION)
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
        val bluetoothOff =
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == false

        val wifiOff =
            (getSystemService(WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == false

        if (bluetoothOff || wifiOff) {
            openWirelessSettings()
        } else {
            startMeshService()
        }
    }

    private fun openWirelessSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
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

        if (
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == true &&
            (getSystemService(WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
        ) {
            startMeshService()
        }
    }
}
