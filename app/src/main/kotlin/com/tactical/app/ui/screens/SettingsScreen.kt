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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.tactical.app.ui.theme.RedTacticalTextSecondary
import com.tactical.app.ui.theme.SquadBlueBackground
import com.tactical.app.ui.theme.SquadBlueBorder
import com.tactical.app.ui.theme.SquadBlueGlow
import com.tactical.app.ui.theme.SquadBluePrimary
import com.tactical.app.ui.theme.SquadBlueSurface

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
    languageLoadingCode: String?,
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
            .background(SquadBlueBackground)
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
                        SquadBluePrimary
                    } else {
                        RedTacticalTextSecondary
                    }
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SquadBlueSurface,
                unfocusedContainerColor = SquadBlueSurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = SquadBluePrimary,
                unfocusedLabelColor = RedTacticalTextSecondary,
                focusedBorderColor = SquadBluePrimary,
                unfocusedBorderColor = SquadBlueBorder
            )
        )

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = {
                usernameError = onUsernameSave(usernameInput)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = SquadBluePrimary
            )
        ) {
            Text("SAVE USERNAME", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "UI LANGUAGE",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(6.dp))

        Spacer(Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OUTGOING_LANGUAGES.forEach { language ->
                val selected = selectedLanguageCode == language.code
                val loading = languageLoadingCode == language.code
                val switching = languageLoadingCode != null

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = SquadBlueSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (selected) {
                                SquadBluePrimary
                            } else {
                                SquadBlueBorder
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !switching && !selected) {
                            onLanguageSelected(language.code)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = SquadBlueGlow,
                                strokeWidth = 2.dp
                            )
                        } else {
                            RadioButton(
                                selected = selected,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SquadBluePrimary,
                                    unselectedColor = RedTacticalTextSecondary
                                ),
                                onClick = {
                                    if (!switching && !selected) {
                                        onLanguageSelected(language.code)
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                language.nativeName,
                                color = if (switching && !loading) {
                                    RedTacticalTextSecondary
                                } else {
                                    Color.White
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (loading) "Loading voice models…" else language.name,
                                color = if (loading) {
                                    SquadBlueGlow
                                } else {
                                    RedTacticalTextSecondary
                                },
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

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
            "Voices from the same device are always played one by one. This setting controls how voices from different devices are handled when they arrive close together.",
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
                    "Play all received voice messages one by one, regardless of which device sent them."
                ),
                Triple(
                    com.tactical.platform.speech.mms.MmsTtsPlaybackMode.OVERLAPPING,
                    "OVERLAPPING VOICES",
                    "Voices from different devices may play at the same time. Messages from the same device never overlap."
                )
            )

            playbackOptions.forEach { (mode, title, description) ->
                val selected = ttsPlaybackMode == mode

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = SquadBlueSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (selected) {
                                SquadBluePrimary
                            } else {
                                SquadBlueBorder
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
                            colors = RadioButtonDefaults.colors(
                                selectedColor = SquadBluePrimary,
                                unselectedColor = RedTacticalTextSecondary
                            ),
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
            "OVERLAPPING allows voices from different devices to play together; each device's own messages remain sequential.",
            color = RedTacticalTextSecondary,
            fontSize = 11.sp
        )
    }
}
