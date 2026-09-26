package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.tactical.app.ui.theme.RedTacticalBackground
import com.tactical.app.ui.theme.RedTacticalPrimary
import com.tactical.app.ui.theme.RedTacticalSurface
import com.tactical.app.ui.theme.RedTacticalSurfaceBorder
import com.tactical.app.ui.theme.RedTacticalTextSecondary

private data class LanguageOption(
    val code: String,
    val name: String,
    val nativeName: String
)

private val OUTGOING_LANGUAGES = listOf(
    LanguageOption("hi", "Hindi", "हिन्दी"),
    LanguageOption("en", "English", "English")
)

@Composable
fun SettingsScreen(
    selectedLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    ttsPlaybackMode: com.tactical.platform.speech.mms.MmsTtsPlaybackMode,
    onTtsPlaybackModeSelected: (com.tactical.platform.speech.mms.MmsTtsPlaybackMode) -> Unit,
    username: String,
    onUsernameSave: (String) -> String?,
    modifier: Modifier = Modifier
) {
    var usernameInput by remember(username) {
        mutableStateOf(username)
    }
    var usernameError by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(RedTacticalBackground)
            .padding(20.dp)
    ) {
        Text(
            "USERNAME / CALLSIGN",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "This name is shown to other squad members.",
            color = RedTacticalTextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = usernameInput,
            onValueChange = {
                usernameInput = it
                usernameError = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Username") },
            supportingText = {
                Text(
                    usernameError ?: "Maximum 5 UTF-8 bytes for the existing BLE callsign field.",
                    color = if (usernameError != null) {
                        RedTacticalPrimary
                    } else {
                        RedTacticalTextSecondary
                    }
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RedTacticalSurface,
                unfocusedContainerColor = RedTacticalSurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = RedTacticalPrimary,
                unfocusedLabelColor = RedTacticalTextSecondary,
                focusedBorderColor = RedTacticalPrimary,
                unfocusedBorderColor = RedTacticalSurfaceBorder
            )
        )

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = {
                usernameError = onUsernameSave(usernameInput)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = RedTacticalPrimary
            )
        ) {
            Text("SAVE USERNAME", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "VOICE LANGUAGE",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Language used for your push-to-talk transmissions.",
            color = RedTacticalTextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OUTGOING_LANGUAGES.forEach { language ->
                val selected = selectedLanguageCode == language.code

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RedTacticalSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (selected) {
                                RedTacticalPrimary
                            } else {
                                RedTacticalSurfaceBorder
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onLanguageSelected(language.code) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onLanguageSelected(language.code) }
                        )

                        Column {
                            Text(
                                language.nativeName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                language.name,
                                color = RedTacticalTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Incoming voice messages are handled independently. The sender's language flag is carried in the packet, so Hindi and English can be received regardless of your selected outgoing language.",
            color = RedTacticalTextSecondary,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "INCOMING VOICE PLAYBACK",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Choose how received voice messages are played when multiple messages arrive close together.",
            color = RedTacticalTextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val playbackOptions = listOf(
                Triple(
                    com.tactical.platform.speech.mms.MmsTtsPlaybackMode.ONE_BY_ONE,
                    "ONE BY ONE",
                    "Finish one voice message before starting the next."
                ),
                Triple(
                    com.tactical.platform.speech.mms.MmsTtsPlaybackMode.OVERLAPPING,
                    "OVERLAPPING VOICES",
                    "Play multiple voice messages at the same time when they overlap."
                )
            )

            playbackOptions.forEach { (mode, title, description) ->
                val selected = ttsPlaybackMode == mode

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RedTacticalSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (selected) {
                                RedTacticalPrimary
                            } else {
                                RedTacticalSurfaceBorder
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onTtsPlaybackModeSelected(mode) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onTtsPlaybackModeSelected(mode) }
                        )

                        Column {
                            Text(
                                title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                description,
                                color = RedTacticalTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            "Overlapping voices may be slower or use more CPU when multiple messages are being processed.",
            color = RedTacticalTextSecondary,
            fontSize = 11.sp
        )
    }
}
