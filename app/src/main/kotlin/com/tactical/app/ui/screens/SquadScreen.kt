package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.MainUiState
import com.tactical.app.ui.PeerNodeUi
import com.tactical.ptt.session.SessionState
import com.tactical.app.ui.theme.*

@Composable
fun SquadScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
    onPttCancel: () -> Unit,
    onEmergencyPress: () -> Unit,
    onEmergencyRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectedPeers = uiState.pairedPeers.filter { it.isConnected }
    val pairedPeers = uiState.pairedPeers.filter { !it.isConnected }
    val hasConnection = connectedPeers.isNotEmpty()
    val pttEnabled = hasConnection &&
        uiState.pttSessionState != SessionState.TRANSMITTING &&
        uiState.pttSessionState != SessionState.ARMED

    var pttHeld by remember { mutableStateOf(false) }

    val pttLabel = when (uiState.pttSessionState) {
        SessionState.ARMED -> "STARTING"
        SessionState.RECORDING -> "RECORDING"
        SessionState.TRANSMITTING -> "SENDING"
        else -> if (hasConnection) "PUSH TO TALK" else "NO CONNECTION"
    }

    val pttHint = when (uiState.pttSessionState) {
        SessionState.RECORDING -> "Release to send • swipe right to cancel"
        SessionState.TRANSMITTING -> "Transcribing and sending…"
        else -> if (hasConnection) {
            "Hold to record • release to send • swipe right to cancel"
        } else {
            "Connect to a squad member to enable PTT"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RedTacticalBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "SQUAD",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${connectedPeers.size} CONNECTED • ${pairedPeers.size} PAIRED",
                        color = RedTacticalTextSecondary,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !pttEnabled -> RedTacticalSurface
                                pttHeld -> RedTacticalPrimaryBright
                                else -> RedTacticalPrimary
                            }
                        )
                        .border(
                            width = if (pttEnabled) 2.dp else 1.dp,
                            color = when {
                                !pttEnabled -> RedTacticalSurfaceBorder
                                pttHeld -> RedTacticalPrimaryBright
                                else -> RedTacticalPrimary
                            },
                            shape = CircleShape
                        )
                        .then(
                            if (pttEnabled) {
                                Modifier.pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        pttHeld = true
                                        onPttPress()

                                        var cancelled = false
                                        val cancelDistance = 80.dp.toPx()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break

                                            if (change.changedToUp()) {
                                                change.consume()
                                                if (!cancelled) {
                                                    pttHeld = false
                                                    onPttRelease()
                                                }
                                                break
                                            }

                                            val dx = change.position.x - down.position.x
                                            val dy = change.position.y - down.position.y

                                            if (
                                                !cancelled &&
                                                dx > cancelDistance &&
                                                kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f
                                            ) {
                                                cancelled = true
                                                pttHeld = false
                                                change.consume()
                                                onPttCancel()
                                            } else {
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (pttEnabled) Color.White else RedTacticalTextSecondary,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            pttLabel,
                            color = if (pttEnabled) Color.White else RedTacticalTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                pttHint,
                color = RedTacticalTextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (uiState.emergencyComposerVisible) {
                                RedTacticalSurface
                            } else {
                                Color(0xFF5A1717)
                            }
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (uiState.emergencyComposerVisible) {
                                RedTacticalSurfaceBorder
                            } else {
                                RedTacticalPrimaryBright
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .then(
                            if (!uiState.emergencyComposerVisible && uiState.pttSessionState == SessionState.IDLE) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            onEmergencyPress()
                                            try {
                                                awaitRelease()
                                            } finally {
                                                onEmergencyRelease()
                                            }
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚨",
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (uiState.emergencyComposerVisible) {
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
        }

        item {
            SectionDividerLabel("CONNECTED DEVICES")
        }

        if (connectedPeers.isEmpty()) {
            item {
                EmptySquadSection("No connected devices")
            }
        } else {
            items(
                connectedPeers,
                key = { "connected_" + it.deviceAddress }
            ) { peer ->
                PeerCard(peer)
            }
        }

        item {
            Spacer(Modifier.height(2.dp))
            SectionDividerLabel("PAIRED DEVICES")
        }

        if (pairedPeers.isEmpty()) {
            item {
                EmptySquadSection(
                    if (uiState.pairedPeers.isEmpty()) {
                        "No paired devices"
                    } else {
                        "All paired devices are connected"
                    }
                )
            }
        } else {
            items(
                pairedPeers,
                key = { "paired_" + it.deviceAddress }
            ) { peer ->
                PeerCard(peer)
            }
        }

        uiState.pttLastTranscription?.takeIf { it.isNotBlank() }?.let { transcript ->
            item {
                Spacer(Modifier.height(2.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RedTacticalSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "LAST PTT TRANSCRIPTION",
                            color = RedTacticalTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            transcript,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionDividerLabel(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = RedTacticalSurfaceBorder
        )
        Text(
            text = label,
            color = RedTacticalTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = RedTacticalSurfaceBorder
        )
    }
}

@Composable
private fun EmptySquadSection(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = RedTacticalTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PeerCard(peer: PeerNodeUi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (peer.isConnected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = RedTacticalStatusGreen,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(RedTacticalBackground)
            ) {
                Icon(Icons.Default.Person, contentDescription = peer.callsign, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.callsign, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (peer.isConnected) "Connected • " + peer.distanceText else "Paired • " + peer.distanceText,
                    color = if (peer.isConnected) RedTacticalStatusGreen else RedTacticalTextSecondary,
                    fontSize = 11.sp
                )
            }
            Icon(
                Icons.Default.SignalCellularAlt,
                contentDescription = "Signal",
                tint = if (peer.isConnected) RedTacticalStatusGreen else RedTacticalTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
