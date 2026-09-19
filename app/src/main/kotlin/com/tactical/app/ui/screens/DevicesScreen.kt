package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.MainUiState
import com.tactical.app.ui.PeerNodeUi
import com.tactical.app.ui.theme.*

@Composable
fun DevicesScreen(uiState: MainUiState, onScan: () -> Unit, onPair: (String) -> Unit = {}, onConnect: (String) -> Unit = {}, onRepair: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(RedTacticalBackground).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AVAILABLE DEVICES", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(if (uiState.isScanning) "Scanning nearby radios…" else "BLE + Wi‑Fi Direct", color = RedTacticalTextSecondary, fontSize = 12.sp)
            }
            Button(onClick = onScan, enabled = !uiState.isScanning, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = RedTacticalPrimary)) {
                Icon(if (uiState.isScanning) Icons.Default.Sync else Icons.Default.Search, null)
                Spacer(Modifier.width(6.dp))
                Text(if (uiState.isScanning) "SCANNING" else "SCAN")
            }
        }
        Spacer(Modifier.height(16.dp))
        if (uiState.squadPeers.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = RedTacticalSurface), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (uiState.isScanning) Icons.Default.Sync else Icons.Default.Devices, null, tint = RedTacticalPrimaryBright, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (uiState.isScanning) "Looking for nearby iTantra devices" else "No devices found yet", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap SCAN to search using BLE and Wi‑Fi Direct.", color = RedTacticalTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(uiState.squadPeers) { peer -> AvailableDeviceCard(peer, onPair, onConnect, onRepair) }
            }
        }
    }
}

@Composable
private fun AvailableDeviceCard(
    peer: PeerNodeUi,
    onPair: (String) -> Unit,
    onConnect: (String) -> Unit,
    onRepair: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(peer.callsign, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("${peer.linkText} • ${peer.distanceText}", color = RedTacticalTextSecondary, fontSize = 11.sp)
                }
                Text(
                    peer.bleState.name.replace('_', ' '),
                    color = if (peer.isConnected || peer.bleState == com.tactical.platform.api.ble.BleLinkState.PAIRED)
                        RedTacticalStatusGreen else RedTacticalStatusYellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when (peer.bleState) {
                    com.tactical.platform.api.ble.BleLinkState.NOT_PAIRED,
                    com.tactical.platform.api.ble.BleLinkState.FAILED -> {
                        OutlinedButton(onClick = { onPair(peer.deviceAddress) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("PAIR")
                        }
                    }
                    com.tactical.platform.api.ble.BleLinkState.PAIRED,
                    com.tactical.platform.api.ble.BleLinkState.DISCONNECTED -> {
                        Button(onClick = { onConnect(peer.deviceAddress) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("CONNECT")
                        }
                        OutlinedButton(onClick = { onRepair(peer.deviceAddress) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("REPAIR")
                        }
                    }
                    com.tactical.platform.api.ble.BleLinkState.CONNECTED -> {
                        OutlinedButton(onClick = { onRepair(peer.deviceAddress) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("REPAIR CONNECTION")
                        }
                    }
                    else -> {
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text("WORKING…")
                        }
                    }
                }
            }
        }
    }
}