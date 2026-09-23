package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
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
    var selectedEmergency by remember { mutableStateOf<ChatMessageUi?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // The Messages destination is only composed while it is visible, so
    // entering this screen (and receiving a new message while it stays open)
    // marks the current conversation as seen.
    LaunchedEffect(
        uiState.receivedMessages.size,
        uiState.receivedMessages.lastOrNull()?.timestampEpochMs
    ) {
        onMessagesOpened()
    }

    // Keep normal text and speech-derived traffic in separate tabs
    // so Call Mode sentence-by-sentence transmissions do not flood the
    // ordinary conversation.
    val allMessages = remember(
        uiState.sentMessages,
        uiState.receivedMessages
    ) {
        (uiState.sentMessages + uiState.receivedMessages)
            .distinctBy(::messageStorageKey)
            .sortedBy {
                it.conversationOrderEpochMs.takeIf { time -> time > 0L }
                    ?: it.timestampEpochMs
            }
    }

    val conversationMessages = remember(allMessages, selectedTab) {
        allMessages.filter { message ->
            if (selectedTab == 1) message.isVoice else !message.isVoice
        }
    }

    val listState = rememberLazyListState()

    // Keep the newest message visible as the conversation grows.
    LaunchedEffect(conversationMessages.size) {
        if (conversationMessages.isNotEmpty()) {
            listState.animateScrollToItem(conversationMessages.lastIndex)
        }
    }

    val selectedMessages = conversationMessages.filter {
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
                TextButton(onClick = { enterSelection() }) {
                    Text(
                        "SELECT",
                        color = RedTacticalPrimaryBright,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = RedTacticalBackground,
            contentColor = Color.White
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "CHAT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "VOICE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (conversationMessages.isEmpty()) {
                item {
                    Text(
                        "No messages",
                        color = RedTacticalTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            itemsIndexed(
                items = conversationMessages,
                key = { index, message ->
                    messageStorageKey(message) + "_" + index
                }
            ) { _, message ->
                MessageRow(
                    message = message,
                    isSent = message.sender == "YOU",
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

        if (!selectionMode && selectedTab == 0) {
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
        val selectedSent = selectedMessages
            .filter { it in uiState.sentMessages }
            .toSet()
        val selectedReceived = selectedMessages
            .filterNot { it in uiState.sentMessages }
            .toSet()

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
                        if (selectedSent.isNotEmpty()) {
                            onDeleteMessages(selectedSent, true)
                        }
                        if (selectedReceived.isNotEmpty()) {
                            onDeleteMessages(selectedReceived, false)
                        }
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

    if (message.isAlert) {
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
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🚨 " + message.sender,
                        color = RedTacticalPrimaryBright,
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
                    maxLines = 6
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    "EMERGENCY • TAP FOR DETAILS",
                    color = RedTacticalPrimaryBright,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isSelectionMode) {
                    Spacer(Modifier.height(8.dp))
                    SelectionIndicator(
                        isSelected = isSelected,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    Color(0xFF3A1414)
                } else {
                    RedTacticalSurface
                }
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isSent) 4.dp else 16.dp,
                bottomEnd = if (isSent) 16.dp else 4.dp
            ),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .fillMaxWidth(fraction = 0.82f)
                .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isSent) 4.dp else 16.dp,
                        bottomEnd = if (isSent) 16.dp else 4.dp
                    )
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 9.dp
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!isSent) {
                        Text(
                            message.sender,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    Text(
                        message.timestampText,
                        color = RedTacticalTextSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    message.text,
                    color = Color.White,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message.statusText,
                        color = borderColor,
                        fontSize = 9.sp
                    )

                    if (isSelectionMode) {
                        Spacer(Modifier.width(8.dp))
                        SelectionIndicator(isSelected = isSelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .border(
                width = 2.dp,
                color = if (isSelected) {
                    RedTacticalPrimaryBright
                } else {
                    RedTacticalSurfaceBorder
                },
                shape = RoundedCornerShape(50)
            )
            .then(
                if (isSelected) {
                    Modifier.background(
                        RedTacticalPrimaryBright,
                        RoundedCornerShape(50)
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
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
