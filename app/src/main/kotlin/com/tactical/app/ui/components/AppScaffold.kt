package com.tactical.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tactical.app.ui.theme.*

@Composable
fun AppHeader(deviceCount: Int, modifier: Modifier = Modifier, onSettingsClick: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().background(RedTacticalBackground).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, "SENTINEL", tint = RedTacticalPrimary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("SENTINEL", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(RedTacticalSurface).border(1.dp, RedTacticalSurfaceBorder, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(RedTacticalStatusGreen))
            Spacer(Modifier.width(6.dp))
            Text("NETWORK READY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("• $deviceCount ${if (deviceCount == 1) "Device" else "Devices"}", color = RedTacticalTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun AppBottomNavigation(
    selectedTab: Int,
    unreadMessageCount: Int = 0,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    squadTheme: Boolean = false
) {
    NavigationBar(
        modifier = modifier,
        containerColor = if (squadTheme) SquadBlueSurface else RedTacticalSurface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(selected = selectedTab == 0, onClick = { onTabSelected(0) }, icon = { Icon(Icons.Default.Devices, "Devices") }, label = { Text("DEVICES") }, colors = navigationColors(squadTheme))
        NavigationBarItem(selected = selectedTab == 1, onClick = { onTabSelected(1) }, icon = { Icon(Icons.Default.People, "Squad") }, label = { Text("SQUAD") }, colors = navigationColors(squadTheme))
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadMessageCount > 0) {
                            Badge {
                                Text(unreadMessageCount.coerceAtMost(99).toString())
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Email, "Messages")
                }
            },
            label = { Text("MESSAGES") },
            colors = navigationColors(squadTheme)
        )
    }
}

@Composable
private fun navigationColors(squadTheme: Boolean) = NavigationBarItemDefaults.colors(
    selectedIconColor = if (squadTheme) SquadBlueGlow else RedTacticalPrimaryBright,
    selectedTextColor = if (squadTheme) SquadBlueGlow else RedTacticalPrimaryBright,
    unselectedIconColor = if (squadTheme) Color(0xFF8EA8C0) else RedTacticalTextSecondary,
    unselectedTextColor = if (squadTheme) Color(0xFF8EA8C0) else RedTacticalTextSecondary,
    indicatorColor = if (squadTheme) Color(0xFF123E63) else RedTacticalDarkBorder
)