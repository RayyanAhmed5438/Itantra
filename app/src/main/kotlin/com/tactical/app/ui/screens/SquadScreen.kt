package com.tactical.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.tactical.ptt.session.SessionState
import com.tactical.app.ui.theme.*

@Composable
fun SquadScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onPttToggle: () -> Unit,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
    onPttCancel: () -> Unit,
    onEmergencyPress: () -> Unit,
    onEmergencyRelease: () -> Unit,
    onRemoveFromSquad: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val connectedPeers = uiState.squadPeers.filter { it.isConnected }
    val squadOfflinePeers = uiState.squadPeers.filter { !it.isConnected }
    val hasConnection = connectedPeers.isNotEmpty()
    val pttButtonEnabled = uiState.pttEnabled &&
        hasConnection &&
        uiState.pttSessionState != SessionState.TRANSMITTING &&
        uiState.pttSessionState != SessionState.ARMED

    var pttHeld by remember { mutableStateOf(false) }
    var emergencyHeld by remember { mutableStateOf(false) }
    var removeArmedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }

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
        label = "emergencyHoldProgress"
    )

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
                        "${connectedPeers.size} CONNECTED • ${squadOfflinePeers.size} IN SQUAD",
                        color = RedTacticalTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onPttToggle,
                        enabled = uiState.pttSessionState == SessionState.IDLE ||
                            uiState.pttContinuousSession,
                        color = if (uiState.pttEnabled) {
                            RedTacticalPrimary
                        } else {
                            RedTacticalSurface
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.pttEnabled) {
                                RedTacticalPrimaryBright
                            } else {
                                RedTacticalSurfaceBorder
                            }
                        )
                    ) {
                        Text(
                            if (uiState.pttEnabled) "PTT ON" else "CALL",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 6.dp
                            )
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
        }

        item {
            var transmissionExpanded by rememberSaveable { mutableStateOf(false) }

            if (
                uiState.pttTransmissionHistory.isNotEmpty() ||
                !uiState.pttEnabled ||
                (
                    uiState.pttEnabled &&
                        uiState.pttSessionState == SessionState.RECORDING &&
                        !uiState.pttLastTranscription.isNullOrBlank()
                    )
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RedTacticalSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = uiState.pttTransmissionHistory.isNotEmpty()) {
                            transmissionExpanded = !transmissionExpanded
                        }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        !uiState.pttEnabled ->
                                            "CALL MODE • VOICE TRANSMISSIONS"
                                        uiState.pttSessionState == SessionState.RECORDING ->
                                            "LIVE PTT TRANSCRIPTION"
                                        else ->
                                            "LAST PTT TRANSMISSION"
                                    },
                                    color = RedTacticalTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                if (!uiState.pttEnabled) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        if (uiState.pttContinuousSession) {
                                            "Listening continuously"
                                        } else {
                                            "Continuous voice mode ready"
                                        },
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (uiState.pttTransmissionHistory.isNotEmpty()) {
                                Text(
                                    if (transmissionExpanded) "▲" else "▼",
                                    color = RedTacticalTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (
                            uiState.pttEnabled &&
                                uiState.pttSessionState == SessionState.RECORDING &&
                                !uiState.pttLastTranscription.isNullOrBlank()
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                uiState.pttLastTranscription.orEmpty(),
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Recording…",
                                    color = RedTacticalVoiceOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "LIVE",
                                    color = RedTacticalTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (uiState.pttTransmissionHistory.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))

                            if (!transmissionExpanded) {
                                val latest = uiState.pttTransmissionHistory.first()
                                Text(
                                    latest.text,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        latest.statusText,
                                        color = transmissionStatusColor(latest.statusText),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        formatPttTimestamp(latest.timestampEpochMs),
                                        color = RedTacticalTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            } else {
                                uiState.pttTransmissionHistory
                                    .asReversed()
                                    .forEachIndexed { index, transmission ->
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        transmission.text,
                                                        color = Color.White,
                                                        fontSize = 13.sp
                                                    )
                                                    Spacer(Modifier.height(3.dp))
                                                    Text(
                                                        formatPttTimestamp(transmission.timestampEpochMs),
                                                        color = RedTacticalTextSecondary,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    transmission.statusText,
                                                    color = transmissionStatusColor(
                                                        transmission.statusText
                                                    ),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            if (
                                                index <
                                                uiState.pttTransmissionHistory.lastIndex
                                            ) {
                                                Spacer(Modifier.height(8.dp))
                                                HorizontalDivider(
                                                    color = RedTacticalSurfaceBorder
                                                )
                                                Spacer(Modifier.height(8.dp))
                                            }
                                        }
                                    }
                            }
                        } else if (!uiState.pttEnabled) {
                            Spacer(Modifier.height(7.dp))
                            uiState.pttLastTranscription
                                ?.takeIf { it.isNotBlank() }
                                ?.let { liveText ->
                                    Text(
                                        "LIVE: $liveText",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                ?: Text(
                                    "Speak normally. Each finalized sentence is sent automatically.",
                                    color = RedTacticalTextSecondary,
                                    fontSize = 10.sp
                                )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
            }

            if (uiState.pttEnabled) {
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
                                    !pttButtonEnabled -> RedTacticalSurface
                                    pttHeld -> RedTacticalVoiceOrange
                                    else -> RedTacticalPrimary
                                }
                            )
                            .border(
                                width = if (pttButtonEnabled) 2.dp else 1.dp,
                                color = when {
                                    !pttButtonEnabled -> RedTacticalSurfaceBorder
                                    pttHeld -> RedTacticalVoiceOrange
                                    else -> RedTacticalPrimary
                                },
                                shape = CircleShape
                            )
                            .then(
                                if (pttButtonEnabled) {
                                    Modifier.pointerInput(Unit) {
                                        var cancelled = false
                                        var totalDragX = 0f
                                        var totalDragY = 0f

                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                cancelled = false
                                                totalDragX = 0f
                                                totalDragY = 0f
                                                pttHeld = true
                                                onPttPress()
                                            },
                                            onDrag = { change, dragAmount ->
                                                totalDragX += dragAmount.x
                                                totalDragY += dragAmount.y

                                                if (
                                                    !cancelled &&
                                                    totalDragX > 80.dp.toPx() &&
                                                    totalDragX > kotlin.math.abs(totalDragY) * 1.2f
                                                ) {
                                                    cancelled = true
                                                    pttHeld = false
                                                    onPttCancel()
                                                }

                                                change.consume()
                                            },
                                            onDragEnd = {
                                                if (!cancelled && pttHeld) {
                                                    pttHeld = false
                                                    onPttRelease()
                                                }
                                            },
                                            onDragCancel = {
                                                if (!cancelled && pttHeld) {
                                                    pttHeld = false
                                                    onPttCancel()
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (pttButtonEnabled) Color.White else RedTacticalTextSecondary,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                pttLabel,
                                color = if (pttButtonEnabled) Color.White else RedTacticalTextSecondary,
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
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RedTacticalSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = RedTacticalPrimaryBright,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "CALL MODE",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Continuous STT • sentence-by-sentence transmission",
                                color = RedTacticalTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

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
                            if (
                                !uiState.emergencyComposerVisible &&
                                (
                                    uiState.pttSessionState == SessionState.IDLE ||
                                        uiState.pttContinuousSession
                                    )
                            ) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            emergencyHeld = true
                                            onEmergencyPress()
                                            try {
                                                awaitRelease()
                                            } finally {
                                                emergencyHeld = false
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
                PeerCard(
                    peer = peer,
                    removeArmed = removeArmedDeviceId == peer.deviceAddress,
                    onLongPress = { removeArmedDeviceId = peer.deviceAddress },
                    onRemove = {
                        onRemoveFromSquad(peer.deviceAddress)
                        removeArmedDeviceId = null
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(2.dp))
            SectionDividerLabel("SQUAD MEMBERS")
        }

        if (squadOfflinePeers.isEmpty()) {
            item {
                EmptySquadSection(
                    if (uiState.squadPeers.isEmpty()) {
                        "No squad members"
                    } else {
                        "All squad members are connected"
                    }
                )
            }
        } else {
            items(
                squadOfflinePeers,
                key = { "squad_" + it.deviceAddress }
            ) { peer ->
                PeerCard(peer, onRemoveFromSquad)
            }
        }

    }
}

private fun formatPttTimestamp(epochMs: Long): String =
    if (epochMs > 0L) {
        java.text.SimpleDateFormat(
            "HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(epochMs))
    } else {
        "Unknown"
    }

private fun transmissionStatusColor(status: String): Color =
    when (status) {
        "Sent" -> RedTacticalStatusGreen
        "Sending…" -> RedTacticalPrimaryBright
        else -> RedTacticalTextSecondary
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
fun PeerCard(
    peer: PeerNodeUi,
    removeArmed: Boolean = false,
    onLongPress: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(peer.deviceAddress) {
                detectTapGestures(
                    onLongPress = onLongPress
                )
            }
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
                    if (peer.isConnected) "Connected • " + peer.distanceText else "In squad • " + peer.distanceText,
                    color = if (peer.isConnected) RedTacticalStatusGreen else RedTacticalTextSecondary,
                    fontSize = 11.sp
                )
            }
            if (removeArmed) {
                TextButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        "REMOVE FROM SQUAD",
                        color = RedTacticalPrimaryBright,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                }
            } else {
                Icon(
                    Icons.Default.SignalCellularAlt,
                    contentDescription = "Signal",
                    tint = if (peer.isConnected) RedTacticalStatusGreen else RedTacticalTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
