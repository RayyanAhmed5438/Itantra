package com.tactical.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.theme.*

@Composable
fun VoxListeningOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "earPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier.fillMaxSize().background(RedTacticalBackground).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("LISTENING (VOX)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        Spacer(Modifier.weight(0.15f))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(180.dp).scale(pulseScale).clip(CircleShape).background(RedTacticalSurface).border(2.dp, RedTacticalPrimary, CircleShape)
        ) {
            Icon(Icons.Default.Hearing, contentDescription = "Listening", tint = RedTacticalPrimaryBright, modifier = Modifier.size(64.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text("Listening for incoming voice...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("Speak to start transmission", color = RedTacticalTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))

        Text("|||||•|||||•|||||•|||||•|||||", color = RedTacticalPrimaryBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.weight(0.15f))

        Card(
            colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RedTacticalPrimary)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = RedTacticalPrimaryBright, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("INCOMING (VOX)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("VOICE ACTIVITY DETECTED", color = RedTacticalPrimaryBright, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}