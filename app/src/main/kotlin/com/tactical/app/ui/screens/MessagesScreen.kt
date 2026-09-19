package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.tactical.app.ui.ChatMessageUi
import com.tactical.app.ui.MainUiState
import com.tactical.app.ui.theme.*

@Composable
fun MessagesScreen(
    uiState: MainUiState,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }

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
            items(currentMessages, key = { it.timestampText + it.sender + it.text }) {
                message -> MessageRow(message, selected == 1)
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
                    tint = if (input.isNotBlank()) RedTacticalPrimaryBright else RedTacticalTextSecondary
                )
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: ChatMessageUi,
    isSent: Boolean
) {
    val borderColor = when {
        isSent && message.isVoice -> RedTacticalPrimary
        isSent -> RedTacticalStatusGreen
        else -> Color.Transparent
    }

    val borderWidth = if (isSent) 1.dp else 0.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSent) {
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(12.dp))
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
                    message.sender,
                    color = Color.White,
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
                fontSize = 14.sp
            )

            Spacer(Modifier.height(5.dp))

            Text(
                message.statusText,
                color = if (isSent && message.isVoice) RedTacticalPrimary else if (isSent) RedTacticalStatusGreen else RedTacticalTextSecondary,
                fontSize = 10.sp
            )
        }
    }
}
