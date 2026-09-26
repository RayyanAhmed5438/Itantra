package com.tactical.app.ui.components

import com.tactical.app.ui.i18n.LocalUiStrings
import com.tactical.app.ui.i18n.UiTextKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tactical.app.ui.EmergencyAlertData
import com.tactical.app.ui.theme.*

@Composable
fun EmergencyAlertDialog(
    alertData: EmergencyAlertData,
    onAcknowledge: () -> Unit
) {
    Dialog(onDismissRequest = onAcknowledge) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = RedTacticalSurface,
            tonalElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                RedTacticalPrimaryBright
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency",
                            tint = RedTacticalPrimaryBright,
                            modifier = Modifier.size(25.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = LocalUiStrings.current.text(UiTextKey.EMERGENCY_ALERT),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(onClick = onAcknowledge) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = LocalUiStrings.current.text(UiTextKey.CLOSE),
                            tint = RedTacticalTextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = LocalUiStrings.current.text(UiTextKey.FROM) + alertData.sender,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = alertData.timestampText,
                        color = RedTacticalTextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoPill(LocalUiStrings.current.text(UiTextKey.SEVERITY) + alertData.severity)
                    InfoPill(LocalUiStrings.current.text(UiTextKey.LANG) + alertData.languageCode.uppercase())
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = LocalUiStrings.current.text(UiTextKey.MESSAGE),
                    color = RedTacticalTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Spacer(Modifier.height(5.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RedTacticalBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = alertData.message,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 25.sp,
                        modifier = Modifier.padding(15.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = LocalUiStrings.current.text(UiTextKey.LOCATION),
                    color = RedTacticalTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Spacer(Modifier.height(5.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RedTacticalBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (alertData.hasLocation) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = RedTacticalPrimaryBright
                            )
                            Column {
                                Text(
                                    text = LocalUiStrings.current.text(UiTextKey.LOCATION_ATTACHED),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "%.6f, %.6f",
                                        requireNotNull(alertData.locationLatitude),
                                        requireNotNull(alertData.locationLongitude)
                                    ),
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                alertData.locationAccuracyMeters?.let { accuracy ->
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = LocalUiStrings.current.text(UiTextKey.ACCURACY) + accuracy.toInt() + " m",
                                        color = RedTacticalTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = LocalUiStrings.current.text(UiTextKey.NO_LOCATION),
                            color = RedTacticalTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedTacticalPrimaryBright,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = LocalUiStrings.current.text(UiTextKey.CLOSE),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(
        color = RedTacticalBackground,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            RedTacticalDarkBorder
        )
    ) {
        Text(
            text = text,
            color = RedTacticalTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
