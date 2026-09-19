package com.tactical.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.MainUiState
import com.tactical.app.ui.PeerNodeUi
import com.tactical.app.ui.theme.*

@Composable
fun SquadScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RedTacticalBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("SQUAD", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(3.dp))
                Text(uiState.pairedPeers.size.toString() + " PAIRED • BLE MESH", color = RedTacticalTextSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            if (uiState.pairedPeers.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NO PAIRED DEVICES", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text("Pair a nearby device from the home screen to add it to the squad.", color = RedTacticalTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(uiState.pairedPeers, key = { it.deviceAddress }) { peer ->
                    PeerCard(peer)
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedTacticalPrimary)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("PUSH TO TALK", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Voice transmission placeholder • audio/STT will be added later",
                    color = RedTacticalTextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PeerCard(peer: PeerNodeUi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
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
