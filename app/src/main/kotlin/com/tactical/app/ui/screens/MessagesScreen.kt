package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.tactical.app.ui.ChatMessageUi
import com.tactical.app.ui.MainUiState
import com.tactical.app.ui.components.EmergencyAlertDialog
import com.tactical.app.ui.theme.*

@Composable
fun MessagesScreen(
    uiState: MainUiState,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var selectedEmergency by remember { mutableStateOf<ChatMessageUi?>(null) }

    val currentMessages = if (selected == 0) {
        uiState.receivedMessages
    } else {
        uiState.sentMessages
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RedTacticalBackground)
            .padding(20.dp)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Text(
            "MESSAGES",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(10.dp))

        TabRow(
            selectedTabIndex = selected,
            containerColor = RedTacticalBackground,
            contentColor = RedTacticalPrimaryBright
        ) {
            Tab(
                selected = selected == 0,
                onClick = { selected = 0 },
                text = { Text("RECEIVED") }
            )
            Tab(
                selected = selected == 1,
                onClick = { selected = 1 },
                text = { Text("SENT") }
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (currentMessages.isEmpty()) {
                item {
                    Text(
                        if (selected == 0) "No received messages" else "No sent messages",
                        color = RedTacticalTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            items(
                items = currentMessages,
                key = { message ->
                    message.timestampText + "_" + message.sender + "_" + message.text.hashCode()
                }
            ) { message ->
                MessageRow(
                    message = message,
                    isSent = selected == 1,
                    onClick = {
                        if (message.isAlert && message.emergencyData != null) {
                            selectedEmergency = message
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text("Write message…", color = RedTacticalTextSecondary)
                },
                modifier = Modifier.weight(1f),
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = RedTacticalSurface,
                    unfocusedContainerColor = RedTacticalSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = RedTacticalPrimary,
                    unfocusedBorderColor = RedTacticalSurfaceBorder
                ),
                shape = RoundedCornerShape(14.dp)
            )

            IconButton(
                onClick = {
                    val message = input.trim()
                    if (message.isNotEmpty()) {
                        onSendMessage(message)
                        input = ""
                    }
                },
                enabled = input.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank()) {
                        RedTacticalPrimaryBright
                    } else {
                        RedTacticalTextSecondary
                    }
                )
            }
        }
    }

    selectedEmergency?.emergencyData?.let { alert ->
        EmergencyAlertDialog(
            alertData = alert,
            onAcknowledge = { selectedEmergency = null }
        )
    }
}

@Composable
private fun MessageRow(
    message: ChatMessageUi,
    isSent: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        message.isAlert -> RedTacticalPrimaryBright
        message.isVoice -> RedTacticalVoiceOrange
        else -> RedTacticalStatusGreen
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (message.isAlert) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (message.isAlert) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (message.isAlert) "🚨 " + message.sender else message.sender,
                    color = if (message.isAlert) {
                        RedTacticalPrimaryBright
                    } else {
                        Color.White
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    message.timestampText,
                    color = RedTacticalTextSecondary,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                message.text,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = if (message.isAlert) 2 else Int.MAX_VALUE
            )

            Spacer(Modifier.height(5.dp))

            Text(
                text = when {
                    message.isAlert -> "EMERGENCY • TAP FOR DETAILS"
                    message.isVoice -> message.statusText
                    else -> message.statusText
                },
                color = borderColor,
                fontSize = 10.sp,
                fontWeight = if (message.isAlert) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
