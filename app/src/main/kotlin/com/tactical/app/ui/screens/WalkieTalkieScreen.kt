package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.theme.*

@Composable
fun WalkieTalkieScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RedTacticalBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "WALKIE TALKIE",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Local vs Remote Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You", color = RedTacticalTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Icon(Icons.Default.Mic, contentDescription = "You", tint = RedTacticalPrimaryBright)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Remote", color = RedTacticalTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Icon(Icons.Default.VolumeUp, contentDescription = "Remote", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Central Dial
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(RedTacticalSurface)
                .border(3.dp, RedTacticalPrimary, CircleShape)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = RedTacticalPrimaryBright, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("TWO-WAY", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("ACTIVE", color = RedTacticalPrimaryBright, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Hold to talk. Release to listen.",
            color = RedTacticalTextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Waveform graphic
        Text(
            text = "|||||•|||||•|||||•|||||•|||||",
            color = RedTacticalPrimaryBright,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(0.1f))

        // Status Card Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = RedTacticalPrimaryBright, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("YOU", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Ready to talk", color = RedTacticalTextSecondary, fontSize = 10.sp)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("REMOTE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Listening", color = RedTacticalTextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
