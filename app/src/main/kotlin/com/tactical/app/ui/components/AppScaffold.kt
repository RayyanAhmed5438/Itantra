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
fun AppHeader(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RedTacticalBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "SENTINEL",
                    tint = RedTacticalPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SENTINEL",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Connection Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(RedTacticalSurface)
                .border(1.dp, RedTacticalSurfaceBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(RedTacticalStatusGreen)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "CONNECTED",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "→ Wi-Fi Direct • 4 Devices",
                color = RedTacticalTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun AppBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = RedTacticalSurface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("HOME") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RedTacticalPrimaryBright,
                selectedTextColor = RedTacticalPrimaryBright,
                unselectedIconColor = RedTacticalTextSecondary,
                unselectedTextColor = RedTacticalTextSecondary,
                indicatorColor = RedTacticalDarkBorder
            )
        )

        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.People, contentDescription = "Squad") },
            label = { Text("SQUAD") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RedTacticalPrimaryBright,
                selectedTextColor = RedTacticalPrimaryBright,
                unselectedIconColor = RedTacticalTextSecondary,
                unselectedTextColor = RedTacticalTextSecondary,
                indicatorColor = RedTacticalDarkBorder
            )
        )

        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Email, contentDescription = "Messages") },
            label = { Text("MESSAGES") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RedTacticalPrimaryBright,
                selectedTextColor = RedTacticalPrimaryBright,
                unselectedIconColor = RedTacticalTextSecondary,
                unselectedTextColor = RedTacticalTextSecondary,
                indicatorColor = RedTacticalDarkBorder
            )
        )
    }
}
