package com.kusal.solargridxmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusal.solargridxmobile.data.api.ApiClient
import com.kusal.solargridxmobile.data.local.ReservationDbHelper
import com.kusal.solargridxmobile.data.local.SessionManager
import com.kusal.solargridxmobile.data.repository.AuthRepository
import com.kusal.solargridxmobile.data.repository.ReservationRepository
import com.kusal.solargridxmobile.ui.auth.AccountInactiveScreen
import com.kusal.solargridxmobile.ui.auth.LoginScreen
import com.kusal.solargridxmobile.ui.auth.PendingApprovalScreen
import com.kusal.solargridxmobile.ui.auth.RegisterScreen
import com.kusal.solargridxmobile.ui.auth.SolarGreen
import com.kusal.solargridxmobile.ui.navigation.SolarBottomNavigation
import com.kusal.solargridxmobile.ui.reservation.ReservationScreen
import com.kusal.solargridxmobile.ui.reservation.ReservationViewModel
import com.kusal.solargridxmobile.ui.theme.SolarGridXMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize API Client
        ApiClient.initialize(applicationContext)

        setContent {
            SolarGridXMobileTheme {
                MainAppEntry()
            }
        }
    }
}

@Composable
fun MainAppEntry() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val authRepository = remember { AuthRepository(sessionManager) }
    val dbHelper = remember { ReservationDbHelper(context) }
    val reservationRepository = remember { ReservationRepository(dbHelper) }

    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var currentAuthScreen by remember { mutableStateOf("login") }
    var pendingEmail by remember { mutableStateOf("") }
    var pendingPassword by remember { mutableStateOf("") }
    var pendingNic by remember { mutableStateOf("") }
    var inactiveReason by remember { mutableStateOf("") }

    if (!isLoggedIn) {
        when (currentAuthScreen) {
            "login" -> {
                LoginScreen(
                    authRepository = authRepository,
                    onLoginSuccess = { isLoggedIn = true },
                    onNavigateToRegister = { currentAuthScreen = "register" },
                    onAccountPending = { email, password ->
                        pendingEmail = email
                        pendingPassword = password
                        currentAuthScreen = "pending_approval"
                    },
                    onAccountInactive = { email, reason ->
                        pendingEmail = email
                        inactiveReason = reason
                        currentAuthScreen = "inactive"
                    }
                )
            }
            "register" -> {
                RegisterScreen(
                    authRepository = authRepository,
                    onRegisterSuccess = { email, password, nic, _ ->
                        pendingEmail = email
                        pendingPassword = password
                        pendingNic = nic
                        currentAuthScreen = "pending_approval"
                    },
                    onNavigateToLogin = { currentAuthScreen = "login" }
                )
            }
            "pending_approval" -> {
                PendingApprovalScreen(
                    email = pendingEmail,
                    password = pendingPassword,
                    nic = pendingNic,
                    authRepository = authRepository,
                    onLoginSuccess = { isLoggedIn = true },
                    onNavigateToLogin = { currentAuthScreen = "login" }
                )
            }
            "inactive" -> {
                AccountInactiveScreen(
                    email = pendingEmail,
                    statusMessage = inactiveReason,
                    onNavigateToLogin = { currentAuthScreen = "login" }
                )
            }
        }
    } else {
        val userNic = sessionManager.getUserNic() ?: ""
        val reservationViewModel = remember(userNic) {
            ReservationViewModel(reservationRepository, userNic)
        }

        SolarGridXApp(
            sessionManager = sessionManager,
            reservationViewModel = reservationViewModel,
            onLogout = {
                authRepository.logout()
                isLoggedIn = false
                currentAuthScreen = "login"
            }
        )
    }
}

@Composable
fun SolarGridXApp(
    sessionManager: SessionManager,
    reservationViewModel: ReservationViewModel,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Trade / Reservations tab

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
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> ReservationScreen(viewModel = reservationViewModel)
                2 -> MonitorScreen()
                3 -> ProfileScreen(sessionManager = sessionManager, onLogout = onLogout)
            }
        }
    }
}

// HOME SCREEN
@Composable
fun HomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SolarGridX Dashboard",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SolarGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Navigate to 'Trade' tab to book energy slots",
                color = Color(0xFF475569),
                fontSize = 14.sp
            )
        }
    }
}

// MONITOR SCREEN
@Composable
fun MonitorScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Energy Monitoring & Transfer",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SolarGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Member 4 QR Scanning & Transfer",
                color = Color(0xFF475569),
                fontSize = 14.sp
            )
        }
    }
}

// PROFILE SCREEN
@Composable
fun ProfileScreen(sessionManager: SessionManager, onLogout: () -> Unit) {
    val profile = sessionManager.getUserProfile()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = SolarGreen,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = profile?.name ?: "Prosumer",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Text(
            text = profile?.email ?: "",
            fontSize = 14.sp,
            color = Color(0xFF475569)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "NIC: ${profile?.nic ?: "N/A"}",
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Role: ${profile?.role ?: "Prosumer"}",
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Server: http://192.168.0.122:5000",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Log Out", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}