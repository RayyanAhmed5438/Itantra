package com.tactical.app.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.wifi.WifiManager
import android.location.LocationManager
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
    private var startupCheckPending = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            ensureWirelessEnabled()
        } else {
            openAppWirelessSettings()
        }
    }

    private val requestBluetoothEnable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ensureWirelessEnabled()
        } else {
            // Do not trap the user in the Bluetooth prompt. Open the normal
            // wireless settings page so they can enable it manually.
            openBluetoothSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RedTacticalTheme {
                val state by viewModel.uiState.collectAsState()
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    topBar = { AppHeader(deviceCount = state.squadPeers.size) },
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
                                onPair = viewModel::pairPeer,
                                onConnect = viewModel::connectPeer,
                                onRepair = viewModel::repairPeer
                            )
                            1 -> SquadScreen(state, onRefresh = viewModel::startDiscovery)
                            2 -> MessagesScreen(state, viewModel::sendTextMessage)
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

        val bluetoothAdapter =
            getSystemService(BluetoothManager::class.java)?.adapter

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            startupCheckPending = true

            runCatching {
                requestBluetoothEnable.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )
            }.onFailure {
                openBluetoothSettings()
            }
            return
        }

        val wifiManager = getSystemService(WIFI_SERVICE) as? WifiManager
        val wifiEnabled = wifiManager?.isWifiEnabled == true

        if (!wifiEnabled) {
            startupCheckPending = true
            openWifiSettings()
            return
        }

        // Wi-Fi Direct service discovery requires Location Services to be
        // enabled by the system even when the app does not use location data.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locationEnabled =
                (getSystemService(LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled == true
            if (!locationEnabled) {
                startupCheckPending = true
                openLocationSettings()
                return
            }
        }

        startupCheckPending = false
        startMeshService()
    }

    private fun openBluetoothSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }.onFailure {
            openAppWirelessSettings()
        }
    }

    private fun openWifiSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }.onFailure {
            openAppWirelessSettings()
        }
    }

    private fun openLocationSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }.onFailure {
            openAppWirelessSettings()
        }
    }

    private fun openAppWirelessSettings() {
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

        if (!startupCheckPending) return

        val bluetoothOn =
            getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

        val wifiOn =
            (getSystemService(WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true

        val locationOn =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                (getSystemService(LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled == true

        if (bluetoothOn && wifiOn && locationOn) {
            startupCheckPending = false
            startMeshService()
        } else if (bluetoothOn && !wifiOn) {
            openWifiSettings()
        } else if (bluetoothOn && wifiOn && !locationOn) {
            openLocationSettings()
        }
    }
}
