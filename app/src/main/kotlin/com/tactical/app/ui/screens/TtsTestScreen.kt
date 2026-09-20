package com.tactical.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.theme.RedTacticalBackground
import com.tactical.app.ui.theme.RedTacticalPrimary
import com.tactical.app.ui.theme.RedTacticalPrimaryBright
import com.tactical.app.ui.theme.RedTacticalStatusGreen
import com.tactical.app.ui.theme.RedTacticalSurface
import com.tactical.app.ui.theme.RedTacticalTextSecondary
import com.tactical.platform.speech.mms.MmsTtsLanguage
import com.tactical.platform.speech.mms.MmsTtsTestResult

@Composable
fun TtsTestScreen(
    installedLanguages: List<MmsTtsLanguage>,
    isLoadingModels: Boolean,
    modelMessage: String?,
    onSpeak: suspend (MmsTtsLanguage, String) -> MmsTtsTestResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLanguage by remember(installedLanguages) {
        mutableStateOf(installedLanguages.firstOrNull() ?: MmsTtsLanguage.ALL.first())
    }
    var text by remember(selectedLanguage) {
        mutableStateOf(defaultPhrase(selectedLanguage.modelCode))
    }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MmsTtsTestResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    val selectedInstalled = installedLanguages.any { it.modelCode == selectedLanguage.modelCode }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RedTacticalBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "TTS MODEL LAB",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    "MMS VITS • int8 ONNX",
                    color = RedTacticalTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = RedTacticalPrimaryBright
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "BUNDLED MODEL PACK",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            installedLanguages.size.toString() + "/" +
                                MmsTtsLanguage.ALL.size + " languages ready",
                            color = RedTacticalTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                modelMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = when {
                            isLoadingModels -> RedTacticalTextSecondary
                            it.startsWith("TTS models ready") -> RedTacticalStatusGreen
                            else -> RedTacticalPrimaryBright
                        },
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "LANGUAGE",
            color = RedTacticalTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    selectedLanguage.displayName + " (" + selectedLanguage.isoCode + ")",
                    modifier = Modifier.weight(1f),
                    color = Color.White
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                MmsTtsLanguage.ALL.forEach { language ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                language.displayName +
                                    if (installedLanguages.any { it.modelCode == language.modelCode }) {
                                        "  ✓"
                                    } else {
                                        "  —"
                                    }
                            )
                        },
                        onClick = {
                            selectedLanguage = language
                            menuExpanded = false
                            result = null
                            error = null
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                result = null
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("Text to synthesize") },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RedTacticalSurface,
                unfocusedContainerColor = RedTacticalSurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = RedTacticalPrimaryBright,
                unfocusedLabelColor = RedTacticalTextSecondary,
                focusedBorderColor = RedTacticalPrimary,
                unfocusedBorderColor = RedTacticalSurface
            )
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                running = true
                error = null
                result = null
            },
            enabled = selectedInstalled && text.isNotBlank() && !running,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = RedTacticalPrimary
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (running) "SYNTHESIZING…" else "SPEAK")
        }

        if (running) {
            androidx.compose.runtime.LaunchedEffect(selectedLanguage, text) {
                try {
                    result = onSpeak(selectedLanguage, text)
                } catch (t: Throwable) {
                    error = t.message ?: t.javaClass.simpleName
                } finally {
                    running = false
                }
            }
        }

        if (!selectedInstalled && !isLoadingModels) {
            Spacer(Modifier.height(8.dp))
            Text(
                "No bundled model is available for this language.",
                color = RedTacticalTextSecondary,
                fontSize = 11.sp
            )
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "ERROR: " + it,
                    color = RedTacticalPrimaryBright,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        result?.let { test ->
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = RedTacticalSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = RedTacticalStatusGreen
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SYNTHESIS COMPLETE",
                            color = RedTacticalStatusGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MetricRow("Normalized", test.normalizedText)
                    MetricRow("Tokens", test.tokenCount.toString())
                    MetricRow("Inference", test.inferenceMs.toString() + " ms")
                    MetricRow("Audio", test.audioDurationMs.toString() + " ms")
                    MetricRow("Sample rate", test.sampleRate.toString() + " Hz")
                    MetricRow(
                        "Real-time factor",
                        "%.3f".format(test.realTimeFactor)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = RedTacticalTextSecondary, fontSize = 11.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun defaultPhrase(modelCode: String): String = when (modelCode) {
    "eng" -> "Hello, this is an offline voice test."
    "ben" -> "নমস্কার, এটি একটি অফলাইন কণ্ঠ পরীক্ষা।"
    "guj" -> "નમસ્તે, આ એક ઑફલાઇન અવાજ પરીક્ષણ છે."
    "hin" -> "नमस्ते, यह एक ऑफलाइन आवाज़ परीक्षण है।"
    "kan" -> "ನಮಸ್ಕಾರ, ಇದು ಆಫ್‌ಲೈನ್ ಧ್ವನಿ ಪರೀಕ್ಷೆಯಾಗಿದೆ."
    "mal" -> "നമസ്കാരം, ഇത് ഒരു ഓഫ്‌ലൈൻ ശബ്ദ പരിശോധനയാണ്."
    "mar" -> "नमस्कार, ही एक ऑफलाइन आवाज चाचणी आहे."
    "ory" -> "ନମସ୍କାର, ଏହା ଏକ ଅଫଲାଇନ୍ ସ୍ୱର ପରୀକ୍ଷା."
    "tam" -> "வணக்கம், இது ஒரு ஆஃப்லைன் குரல் சோதனை."
    "tel" -> "నమస్కారం, ఇది ఒక ఆఫ్‌లైన్ వాయిస్ పరీక్ష."
    else -> "This is an offline voice test."
}
