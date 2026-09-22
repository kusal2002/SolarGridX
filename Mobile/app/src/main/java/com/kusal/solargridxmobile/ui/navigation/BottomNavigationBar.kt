package com.kusal.solargridxmobile.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class BottomNavItem(
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun SolarBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {

    val navItems = listOf(

        BottomNavItem(
            "Home",
            Icons.Filled.Home,
            Icons.Outlined.Home
        ),

        BottomNavItem(
            "Trade",
            Icons.Filled.SwapHoriz,
            Icons.Outlined.SwapHoriz
        ),

        BottomNavItem(
            "Monitor",
            Icons.Filled.ShowChart,
            Icons.Outlined.ShowChart
        ),

        BottomNavItem(
            "Profile",
            Icons.Filled.Person,
            Icons.Outlined.Person
        )
    )

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = androidx.compose.ui.unit.Dp.Unspecified
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
                    Text(item.title)
                },

                alwaysShowLabel = true,

                colors = NavigationBarItemDefaults.colors(

                    selectedIconColor = Color(0xFF15803D),

                    selectedTextColor = Color(0xFF15803D),

                    indicatorColor = Color(0xFFDCFCE7),

                    unselectedIconColor = Color(0xFF64748B),

                    unselectedTextColor = Color(0xFF64748B)
                )
            )
        }
    }
}