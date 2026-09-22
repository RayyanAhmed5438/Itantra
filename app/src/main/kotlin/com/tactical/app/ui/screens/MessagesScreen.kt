package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    onMessagesOpened: () -> Unit,
    onDeleteMessages: (Set<ChatMessageUi>, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedEmergency by remember { mutableStateOf<ChatMessageUi?>(null) }
    var todayOnly by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // When the Messages screen is visible, newly persisted messages are
    // immediately considered seen rather than leaving a stale unread badge
    // on the bottom navigation.
    LaunchedEffect(
        selectedTab,
        uiState.receivedMessages.size,
        uiState.receivedMessages.lastOrNull()?.timestampEpochMs
    ) {
        if (selectedTab == 0) {
            onMessagesOpened()
        }
    }

    val currentMessages = if (selectedTab == 0) {
        uiState.receivedMessages
    } else {
        uiState.sentMessages
    }

    val filteredMessages = if (todayOnly) {
        currentMessages.filter { isToday(it.timestampEpochMs) }
    } else {
        currentMessages
    }

    val selectedMessages = currentMessages.filter {
        messageStorageKey(it) in selectedKeys
    }.toSet()

    fun enterSelection(message: ChatMessageUi? = null) {
        selectionMode = true
        if (message != null) {
            selectedKeys = selectedKeys + messageStorageKey(message)
        }
    }

    fun toggleSelection(message: ChatMessageUi) {
        val key = messageStorageKey(message)
        selectedKeys = if (key in selectedKeys) {
            selectedKeys - key
        } else {
            selectedKeys + key
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectedKeys = emptySet()
        showDeleteConfirmation = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RedTacticalBackground)
            .padding(20.dp)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectionMode) {
                    "${selectedKeys.size} SELECTED"
                } else {
                    "MESSAGES"
                },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )

            if (selectionMode) {
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = selectedKeys.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete selected messages",
                        tint = if (selectedKeys.isNotEmpty()) {
                            RedTacticalPrimaryBright
                        } else {
                            RedTacticalTextSecondary
                        }
                    )
                }
                IconButton(onClick = ::exitSelection) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = Color.White
                    )
                }
            } else {
                TextButton(
                    onClick = { enterSelection() }
                ) {
                    Text(
                        "SELECT",
                        color = RedTacticalPrimaryBright,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = RedTacticalBackground,
            contentColor = RedTacticalPrimaryBright
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    selectedKeys = emptySet()
                },
                text = { Text("RECEIVED") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    selectedKeys = emptySet()
                },
                text = { Text("SENT") }
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilterChip(
                selected = todayOnly,
                onClick = { todayOnly = !todayOnly },
                label = { Text(if (todayOnly) "TODAY ONLY" else "TODAY") }
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (filteredMessages.isEmpty()) {
                item {
                    Text(
                        when {
                            todayOnly && selectedTab == 0 -> "No received messages today"
                            todayOnly -> "No sent messages today"
                            selectedTab == 0 -> "No received messages"
                            else -> "No sent messages"
                        },
                        color = RedTacticalTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            itemsIndexed(
                items = filteredMessages,
                key = { index, message ->
                    messageStorageKey(message) + "_" + index
                }
            ) { _, message ->
                MessageRow(
                    message = message,
                    isSent = selectedTab == 1,
                    isSelectionMode = selectionMode,
                    isSelected = messageStorageKey(message) in selectedKeys,
                    onClick = {
                        if (selectionMode) {
                            toggleSelection(message)
                        } else if (message.isAlert && message.emergencyData != null) {
                            selectedEmergency = message
                        }
                    },
                    onLongClick = {
                        enterSelection(message)
                    }
                )
            }
        }

        if (!selectionMode) {
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
    }

    selectedEmergency?.emergencyData?.let { alert ->
        EmergencyAlertDialog(
            alertData = alert,
            onAcknowledge = { selectedEmergency = null }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    "Delete messages?",
                    color = Color.White
                )
            },
            text = {
                Text(
                    "Delete ${selectedMessages.size} selected message${if (selectedMessages.size == 1) "" else "s"}? This cannot be undone.",
                    color = RedTacticalTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMessages(selectedMessages, selectedTab == 1)
                        exitSelection()
                    }
                ) {
                    Text(
                        "DELETE",
                        color = RedTacticalPrimaryBright,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text(
                        "CANCEL",
                        color = Color.White
                    )
                }
            },
            containerColor = RedTacticalSurface
        )
    }
}

private fun messageStorageKey(message: ChatMessageUi): String =
    message.sender + "|" +
        message.timestampEpochMs + "|" +
        message.text + "|" +
        message.isAlert + "|" +
        message.isVoice

private fun isToday(epochMs: Long): Boolean {
    if (epochMs <= 0L) return false
    val messageDate = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = java.util.Calendar.getInstance()
    return messageDate.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
        messageDate.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
}

@Composable
private fun MessageRow(
    message: ChatMessageUi,
    isSent: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> RedTacticalPrimaryBright
        message.isAlert -> RedTacticalPrimaryBright
        message.isVoice -> RedTacticalVoiceOrange
        else -> RedTacticalStatusGreen
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xFF3A1414)
            } else {
                RedTacticalSurface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected || message.isAlert) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(if (message.isAlert) 14.dp else 9.dp)
            ) {
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

                Spacer(Modifier.height(if (message.isAlert) 6.dp else 3.dp))

                Text(
                    message.text,
                    color = Color.White,
                    fontSize = if (message.isAlert) 14.sp else 12.sp,
                    maxLines = if (message.isAlert) 4 else 3
                )

                Spacer(Modifier.height(if (message.isAlert) 5.dp else 2.dp))

                Text(
                    text = when {
                        message.isAlert -> "EMERGENCY • TAP FOR DETAILS"
                        message.isVoice -> message.statusText
                        else -> message.statusText
                    },
                    color = borderColor,
                    fontSize = if (message.isAlert) 10.sp else 9.sp,
                    fontWeight = if (message.isAlert) FontWeight.Bold else FontWeight.Normal
                )
            }

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(22.dp)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) {
                                RedTacticalPrimaryBright
                            } else {
                                RedTacticalSurfaceBorder
                            },
                            shape = CircleShape
                        )
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    RedTacticalPrimaryBright,
                                    CircleShape
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Text(
                            "✓",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
