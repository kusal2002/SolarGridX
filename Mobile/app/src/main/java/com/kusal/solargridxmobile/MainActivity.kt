package com.kusal.solargridxmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kusal.solargridxmobile.ui.theme.SolarGridXMobileTheme
import com.kusal.solargridxmobile.ui.navigation.SolarBottomNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SolarGridXMobileTheme {
                SolarGridXApp()
            }
        }
    }
}

@Composable
fun SolarGridXApp() {

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    val tabs = listOf(
        "Home",
        "Trade",
        "Monitor",
        "Profile"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        bottomBar = {

            SolarBottomNavigation(
                selectedTab = selectedTab,

                onTabSelected = { index ->
                    selectedTab = index
                }
            )
        }

    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),

            contentAlignment = Alignment.Center
        ) {

            when (selectedTab) {

                0 -> HomeScreen()

                1 -> TradeScreen()

                2 -> MonitorScreen()

                3 -> ProfileScreen()
            }
        }
    }
}

// HOME SCREEN
@Composable
fun HomeScreen() {
    Text("SolarGridX Dashboard")
}

// TRADE SCREEN
@Composable
fun TradeScreen() {
    Text("Energy Trading")
}

// MONITOR SCREEN
@Composable
fun MonitorScreen() {
    Text("Energy Transfer & Monitoring")
}

// PROFILE SCREEN
@Composable
fun ProfileScreen() {
    Text("User Profile")
}

@Preview(showBackground = true)
@Composable
fun SolarGridXPreview() {

    SolarGridXMobileTheme {
        SolarGridXApp()
    }
}