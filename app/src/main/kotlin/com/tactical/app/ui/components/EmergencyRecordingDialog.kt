package com.tactical.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tactical.app.ui.theme.RedTacticalBackground
import com.tactical.app.ui.theme.RedTacticalPrimary
import com.tactical.app.ui.theme.RedTacticalPrimaryBright
import com.tactical.app.ui.theme.RedTacticalStatusGreen
import com.tactical.app.ui.theme.RedTacticalSurface
import com.tactical.app.ui.theme.RedTacticalSurfaceBorder
import com.tactical.app.ui.theme.RedTacticalTextSecondary

@Composable
fun EmergencyRecordingDialog(
    transcription: String,
    isRecording: Boolean,
    isSending: Boolean,
    error: String?,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = {
        if (!isSending) onCancel()
    }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = RedTacticalSurface,
            tonalElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .border(
                    width = 2.dp,
                    color = RedTacticalPrimaryBright,
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "EMERGENCY ALERT",
                    color = RedTacticalPrimaryBright,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    text = "Recording started automatically.",
                    color = RedTacticalTextSecondary,
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            RedTacticalBackground,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.Stop,
                        contentDescription = null,
                        tint = if (isRecording) RedTacticalPrimaryBright else RedTacticalTextSecondary,
                        modifier = Modifier.size(28.dp)
                    )

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isRecording) "LISTENING…" else "READY TO SEND",
                            color = if (isRecording) RedTacticalPrimaryBright else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (transcription.isBlank()) {
                                "Speak your emergency message."
                            } else {
                                transcription
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                error?.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = message,
                        color = RedTacticalPrimaryBright,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "The alert will be broadcast to nearby connected devices.",
                    color = RedTacticalTextSecondary,
                    fontSize = 10.sp
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        enabled = !isSending,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RedTacticalBackground,
                            contentColor = Color.White
                        )
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onSend,
                        enabled = transcription.isNotBlank() && !isSending,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RedTacticalPrimaryBright,
                            contentColor = Color.White,
                            disabledContainerColor = RedTacticalSurfaceBorder
                        )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (isSending) "SENDING…" else "SEND",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (!isRecording && error == null && transcription.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Transcript ready",
                        color = RedTacticalStatusGreen,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
