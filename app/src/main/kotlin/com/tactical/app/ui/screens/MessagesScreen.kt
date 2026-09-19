package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.ChatMessageUi
import com.tactical.app.ui.MainUiState
import com.tactical.app.ui.theme.*

@Composable
fun MessagesScreen(uiState: MainUiState, onSendMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    val currentMessages = if (selected == 0) uiState.receivedMessages else uiState.sentMessages

    Column(modifier.fillMaxSize().background(RedTacticalBackground).padding(20.dp)) {
        Text("MESSAGES", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        TabRow(selectedTabIndex = selected, containerColor = RedTacticalBackground, contentColor = RedTacticalPrimaryBright) {
            Tab(selected == 0, { selected = 0 }, text = { Text("RECEIVED") })
            Tab(selected == 1, { selected = 1 }, text = { Text("SENT") })
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            if (currentMessages.isEmpty()) item { Text(if (selected == 0) "No received messages" else "No sent messages", color = RedTacticalTextSecondary, fontSize = 13.sp) }
            items(currentMessages) { message -> MessageRow(message) }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            OutlinedTextField(value = input, onValueChange = { input = it }, placeholder = { Text("Write message…", color = RedTacticalTextSecondary) }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = RedTacticalSurface, unfocusedContainerColor = RedTacticalSurface, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = RedTacticalPrimary, unfocusedBorderColor = RedTacticalSurfaceBorder), shape = RoundedCornerShape(14.dp))
            IconButton(onClick = { val message = input.trim(); if (message.isNotEmpty()) { onSendMessage(message); input = "" } }) { Icon(Icons.Default.Send, "Send", tint = RedTacticalPrimaryBright) }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessageUi) {
    Card(colors = CardDefaults.cardColors(containerColor = RedTacticalSurface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(message.sender, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(message.timestampText, color = RedTacticalTextSecondary, fontSize = 10.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(message.text, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(5.dp))
            Text(message.statusText, color = RedTacticalTextSecondary, fontSize = 10.sp)
        }
    }
}