package com.tactical.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.MainUiState
import com.tactical.app.ui.PeerNodeUi
import com.tactical.app.ui.theme.*
import com.tactical.platform.api.ble.BleLinkState

@Composable
fun DevicesScreen(
    uiState: MainUiState,
    onScan: () -> Unit,
    onAddToSquad: (String) -> Unit = {},
    onEmergencyPress: () -> Unit = {},
    onEmergencyRelease: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var emergencyHeld by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    val emergencyHoldProgress by animateFloatAsState(
        targetValue = if (
            emergencyHeld && !uiState.emergencyComposerVisible
        ) {
            1f
        } else {
            0f
        },
        animationSpec = if (emergencyHeld) {
            tween(durationMillis = 2000)
        } else {
            tween(durationMillis = 150)
        },
        label = "devicesEmergencyHoldProgress"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RedTacticalBackground)
            .padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("AVAILABLE DEVICES", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text("Nearby iTantra devices", color = RedTacticalTextSecondary, fontSize = 12.sp)
            }
            Button(
                onClick = onScan,
                enabled = !uiState.isScanning,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedTacticalPrimary)
            ) {
                Icon(if (uiState.isScanning) Icons.Default.Sync else Icons.Default.Search, null)
                Spacer(Modifier.width(6.dp))
                Text(if (uiState.isScanning) "SCANNING" else "SCAN")
            }
        }
        Spacer(Modifier.height(14.dp))

        // Emergency is intentionally on Home/Available Devices so it is
        // immediately reachable without entering the Squad screen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .pointerInput(uiState.emergencyComposerVisible, uiState.pttContinuousSession) {
                    detectTapGestures(
                        onPress = {
                            if (
                                !uiState.emergencyComposerVisible &&
                                (
                                    uiState.pttSessionState == com.tactical.ptt.session.SessionState.IDLE ||
                                        uiState.pttContinuousSession
                                    )
                            ) {
                                emergencyHeld = true
                                onEmergencyPress()
                                try {
                                    awaitRelease()
                                } finally {
                                    emergencyHeld = false
                                    onEmergencyRelease()
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (uiState.emergencyComposerVisible) {
                            RedTacticalSurface
                        } else {
                            Color(0xFF5A1717)
                        }
                    )
                    .border(
                        1.5.dp,
                        if (uiState.emergencyComposerVisible) {
                            RedTacticalSurfaceBorder
                        } else {
                            RedTacticalPrimaryBright
                        },
                        RoundedCornerShape(14.dp)
                    )
            ) {
                if (!uiState.emergencyComposerVisible && emergencyHoldProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(emergencyHoldProgress)
                            .align(Alignment.CenterStart)
                            .background(RedTacticalPrimaryBright)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("🚨", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (uiState.emergencyComposerVisible) {
                            "EMERGENCY ACTIVE"
                        } else {
                            "HOLD FOR EMERGENCY"
                        },
                        color = if (uiState.emergencyComposerVisible) {
                            RedTacticalTextSecondary
                        } else {
                            Color.White
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            "Hold for 2 seconds to open the emergency recorder",
            color = RedTacticalTextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        if (uiState.availablePeers.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (uiState.isScanning) Icons.Default.Sync else Icons.Default.Devices, null, tint = RedTacticalPrimaryBright, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (uiState.isScanning) "Looking for nearby iTantra devices" else "No devices found yet", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Discovery runs automatically every 10 seconds.", color = RedTacticalTextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                items(uiState.availablePeers, key = { it.deviceAddress }) { peer ->
                    AvailableDeviceCard(peer, onAddToSquad)
                }
            }
        }
    }
}

@Composable
private fun AvailableDeviceCard(
    peer: PeerNodeUi,
    onAddToSquad: (String) -> Unit
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
                    Text(peer.linkText + " • " + peer.distanceText, color = RedTacticalTextSecondary, fontSize = 11.sp)
                }
                Text(
                    when (peer.bleState) {
                        BleLinkState.CONNECTED -> "CONNECTED"
                        BleLinkState.CONNECTING -> "CONNECTING"
                        BleLinkState.FAILED -> "AVAILABLE"
                        else -> "AVAILABLE"
                    },
                    color = if (peer.bleState == BleLinkState.CONNECTED) {
                        RedTacticalStatusGreen
                    } else {
                        RedTacticalStatusYellow
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onAddToSquad(peer.deviceAddress) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("ADD TO SQUAD")
            }
        }
    }
}
