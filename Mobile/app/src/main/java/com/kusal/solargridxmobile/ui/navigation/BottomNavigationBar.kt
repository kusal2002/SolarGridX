package com.kusal.solargridxmobile.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class BottomNavItem(
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun SolarBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    operator: Boolean = false
) {

    val navItems = listOf(

        BottomNavItem(
            "Home",
            Icons.Filled.Dashboard,
            Icons.Outlined.Dashboard
        ),

        BottomNavItem(
            "Stations",
            Icons.Filled.EvStation,
            Icons.Outlined.EvStation
        ),

        BottomNavItem(
            if (operator) "Bookings" else "Reserve",
            Icons.Filled.CalendarMonth,
            Icons.Outlined.CalendarMonth
        ),

        BottomNavItem(
            "Transfers",
            Icons.Filled.SwapHoriz,
            Icons.Outlined.SwapHoriz
        ),

        BottomNavItem(
            "Profile",
            Icons.Filled.AccountCircle,
            Icons.Outlined.AccountCircle
        )
    )

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp
    ) {

        navItems.forEachIndexed { index, item ->

            val selected = selectedTab == index

            NavigationBarItem(

                selected = selected,

                onClick = {
                    onTabSelected(index)
                },

                icon = {

                    Icon(
                        imageVector = if (selected)
                            item.selectedIcon
                        else
                            item.unselectedIcon,

                        contentDescription = item.title
                    )
                },

                label = {
                    Text(item.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                },

                alwaysShowLabel = true,

                colors = NavigationBarItemDefaults.colors(

                    selectedIconColor = Color(0xFF065F46),

                    selectedTextColor = Color(0xFF065F46),

                    indicatorColor = Color(0xFFD1FAE5),

                    unselectedIconColor = Color(0xFF64748B),

                    unselectedTextColor = Color(0xFF64748B)
                )
            )
        }
    }
}
