package com.tactical.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.MainUiState
import com.tactical.app.ui.PttState
import com.tactical.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PttHomeScreen(
    uiState: MainUiState,
    onLanguageSelected: (String) -> Unit,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit,
    onToggleWalkieTalkie: () -> Unit,
    onToggleVox: () -> Unit,
    onStartEmergencyHold: () -> Unit,
    onCancelEmergencyHold: () -> Unit,
    onOpenWalkieTalkieScreen: () -> Unit,
    onOpenVoxOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    val languages = listOf("हिन्दी", "English", "मराठी", "தமிழ்")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RedTacticalBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Language Selector Card
        ExposedDropdownMenuBox(
            expanded = languageDropdownExpanded,
            onExpandedChange = { languageDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.selectedLanguage,
                onValueChange = {},
                readOnly = true,
                label = { Text("LANGUAGE", color = RedTacticalTextSecondary, fontSize = 11.sp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = RedTacticalSurface,
                    unfocusedContainerColor = RedTacticalSurface,
                    focusedBorderColor = RedTacticalPrimary,
                    unfocusedBorderColor = RedTacticalSurfaceBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = languageDropdownExpanded,
                onDismissRequest = { languageDropdownExpanded = false },
                modifier = Modifier.background(RedTacticalSurface)
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang, color = Color.White) },
                        onClick = {
                            onLanguageSelected(lang)
                            languageDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Central PTT Dial / Button with 5 States
        PttDialButton(
            pttState = uiState.pttState,
            recordingSeconds = uiState.recordingSeconds,
            sendingSeconds = uiState.sendingSeconds,
            onStartPtt = onStartPtt,
            onStopPtt = onStopPtt
        )

        Spacer(modifier = Modifier.weight(0.1f))

        // Listening (VOX) Bar Card
        VoxStatusCard(
            pttState = uiState.pttState,
            onClick = onOpenVoxOverlay
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Action Row (Walkie Talkie Mode & VOX Mode)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                title = "WALKIE TALKIE",
                subtitle = if (uiState.isWalkieTalkieOn) "ON" else "OFF",
                icon = Icons.Default.Mic,
                isActive = uiState.isWalkieTalkieOn,
                onClick = {
                    onToggleWalkieTalkie()
                    onOpenWalkieTalkieScreen()
                },
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                title = "VOX",
                subtitle = if (uiState.isVoxOn) "ON" else "OFF",
                icon = Icons.Default.GraphicEq,
                isActive = uiState.isVoxOn,
                onClick = onToggleVox,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Emergency Panic Banner
        EmergencyBanner(
            holdProgress = uiState.emergencyHoldProgress,
            onStartHold = onStartEmergencyHold,
            onCancelHold = onCancelEmergencyHold
        )
    }
}

@Composable
fun PttDialButton(
    pttState: PttState,
    recordingSeconds: Int,
    sendingSeconds: Int,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pttPulsing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            .scale(if (pttState == PttState.RECORDING) pulseScale else 1f)
            .clip(CircleShape)
            .background(
                when (pttState) {
                    PttState.RECORDING -> RedTacticalDarkBorder
                    PttState.SENT -> Color(0xFF1B381E)
                    else -> RedTacticalSurface
                }
            )
            .border(
                width = 3.dp,
                color = when (pttState) {
                    PttState.RECORDING -> RedTacticalPrimaryBright
                    PttState.PROCESSING -> RedTacticalStatusYellow
                    PttState.SENDING -> RedTacticalPrimary
                    PttState.SENT -> RedTacticalStatusGreen
                    PttState.IDLE -> RedTacticalPrimary
                },
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onStartPtt()
                        tryAwaitRelease()
                        onStopPtt()
                    }
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (pttState) {
                PttState.IDLE -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Hold to Talk",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "HOLD TO",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "TALK",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                PttState.RECORDING -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Recording",
                        tint = RedTacticalPrimaryBright,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "RECORDING",
                        color = RedTacticalPrimaryBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("00:%02d", recordingSeconds),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                PttState.PROCESSING -> {
                    CircularProgressIndicator(
                        color = RedTacticalStatusYellow,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "PROCESSING",
                        color = RedTacticalStatusYellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "...",
                        color = RedTacticalTextSecondary,
                        fontSize = 16.sp
                    )
                }

                PttState.SENDING -> {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Sending",
                        tint = RedTacticalPrimaryBright,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SENDING",
                        color = RedTacticalPrimaryBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("00:%02d", sendingSeconds),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                PttState.SENT -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Sent",
                        tint = RedTacticalStatusGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SENT",
                        color = RedTacticalStatusGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun VoxStatusCard(
    pttState: PttState,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RedTacticalSurfaceBorder)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "LISTENING (VOX)",
                color = RedTacticalTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "|||||•|||•|||",
                    color = RedTacticalPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = when (pttState) {
                        PttState.RECORDING -> "Listening..."
                        PttState.PROCESSING -> "Processing speech..."
                        PttState.SENDING -> "Sending to squad..."
                        PttState.SENT -> "Sent successfully"
                        PttState.IDLE -> "Idle"
                    },
                    color = RedTacticalTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = RedTacticalSurface,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isActive) RedTacticalPrimary else RedTacticalSurfaceBorder
            )
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isActive) RedTacticalPrimaryBright else RedTacticalTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = if (isActive) RedTacticalPrimaryBright else RedTacticalTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun EmergencyBanner(
    holdProgress: Float,
    onStartHold: () -> Unit,
    onCancelHold: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C0F10)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(RedTacticalPrimaryBright)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onStartHold()
                        tryAwaitRelease()
                        onCancelHold()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Emergency",
                    tint = RedTacticalPrimaryBright,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EMERGENCY",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text = "Hold for 2 seconds",
                color = RedTacticalTextSecondary,
                fontSize = 11.sp
            )

            if (holdProgress > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { holdProgress },
                    color = RedTacticalPrimaryBright,
                    trackColor = RedTacticalDarkBorder,
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
        }
    }
}
