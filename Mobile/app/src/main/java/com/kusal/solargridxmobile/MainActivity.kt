package com.kusal.solargridxmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.kusal.solargridxmobile.ui.station.StationMapScreen
import com.kusal.solargridxmobile.ui.reservation.ReservationViewModel
import com.kusal.solargridxmobile.ui.theme.SolarGridXMobileTheme
import com.kusal.solargridxmobile.ui.transfer.TransferScreen
import com.kusal.solargridxmobile.ui.transfer.OperatorHomeScreen

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
    val operator = sessionManager.getUserRole() in listOf("Grid Operator", "Backoffice")
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            SolarBottomNavigation(
                selectedTab = selectedTab,
                operator = operator,
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
                0 -> if (operator) OperatorHomeScreen(sessionManager) { selectedTab = 3 } else HomeScreen(
                    sessionManager = sessionManager,
                    viewModel = reservationViewModel,
                    onNavigateToTab = { selectedTab = it }
                )
                1 -> StationMapScreen()
                2 -> if (operator) TransferScreen(sessionManager, initialView = "pending") else ReservationScreen(viewModel = reservationViewModel)
                3 -> TransferScreen(sessionManager)
                4 -> ProfileScreen(
                    sessionManager = sessionManager,
                    viewModel = reservationViewModel,
                    onLogout = onLogout
                )
            }
        }
    }
}

// ==========================================
// 1. HOME SCREEN (Member 3 Dashboard)
// ==========================================
@Composable
fun HomeScreen(
    sessionManager: SessionManager,
    viewModel: ReservationViewModel,
    onNavigateToTab: (Int) -> Unit
) {
    val profile = sessionManager.getUserProfile()
    val state by viewModel.uiState.collectAsState()
    val nextRes = state.nextUpcomingReservation
    LaunchedEffect(Unit) { viewModel.loadAllData() }
    val nextStation = state.stations.find { it.id == nextRes?.stationId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Welcome Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome back,",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = profile?.name ?: "Prosumer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFDCFCE7))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "NIC: ${profile?.nic ?: "N/A"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFDBEAFE))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = profile?.role ?: "Prosumer",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.loadAllData() },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF15803D)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4 KPI Summary Cards (2x2 Grid)
        Text(
            text = "Energy Reservation Overview",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeKpiCard(
                title = "Total Bookings",
                value = "${state.totalCount}",
                subtitle = "All slots",
                accentColor = Color(0xFF475569),
                bgColor = Color(0xFFF8FAFC),
                modifier = Modifier.weight(1f)
            )
            HomeKpiCard(
                title = "Pending Review",
                value = "${state.pendingCount}",
                subtitle = "Awaiting approval",
                accentColor = Color(0xFFB45309),
                bgColor = Color(0xFFFEF3C7),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeKpiCard(
                title = "Approved Slots",
                value = "${state.approvedCount}",
                subtitle = "Ready for transfer",
                accentColor = Color(0xFF1D4ED8),
                bgColor = Color(0xFFDBEAFE),
                modifier = Modifier.weight(1f)
            )
            HomeKpiCard(
                title = "Total Energy",
                value = "${state.totalKwh.toInt()} kWh",
                subtitle = "Reserved capacity",
                accentColor = Color(0xFF15803D),
                bgColor = Color(0xFFDCFCE7),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Next Upcoming Booking Spotlight
        Text(
            text = "Upcoming Energy Booking",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (nextRes != null) {
                    val (isEligible, hoursLeft) = viewModel.isEligibleFor12HourRule(nextRes)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Booking #${nextRes.id.takeLast(6).uppercase()}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (nextRes.status == "Approved") Color(0xFFDBEAFE) else Color(0xFFFEF3C7))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = nextRes.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (nextRes.status == "Approved") Color(0xFF1D4ED8) else Color(0xFFB45309)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = nextStation?.stationName ?: nextRes.stationName ?: "Solar Station",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Date: ${nextRes.reservationDate.split("T")[0]}",
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "Time: ${nextRes.startTime.take(5)} - ${nextRes.endTime.take(5)}",
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reserved: ${nextRes.requestedEnergyKwh} kWh",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = if (isEligible) "${hoursLeft.toInt()}h left to modify" else "12h lock active",
                            fontSize = 11.sp,
                            color = if (isEligible) Color(0xFF15803D) else Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { onNavigateToTab(2) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Manage in My Bookings", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No upcoming reservations scheduled",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Book energy slots up to 7 days in advance",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onNavigateToTab(2) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Book Energy Slot", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions
        Text(
            text = "Quick Actions",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToTab(2) },
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Browse Slots",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${state.slots.filter { it.isActive }.size} active slots",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigateToTab(2) },
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDBEAFE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "My Bookings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${state.myReservations.size} reservations",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Policies & Rules Card (Member 3 documentation)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF15803D),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Energy Reservation Policies",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0F172A)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                PolicyItem(
                    title = "7-Day Advance Booking Window",
                    description = "Slots are open for booking up to 7 days ahead. Reservations enter Pending status until verified."
                )
                Spacer(modifier = Modifier.height(8.dp))
                PolicyItem(
                    title = "12-Hour Modification/Cancel Rule",
                    description = "Bookings can be adjusted or cancelled freely until 12 hours before slot start time. Within 12h, slots are locked."
                )
                Spacer(modifier = Modifier.height(8.dp))
                PolicyItem(
                    title = "Single Active Slot per Prosumer",
                    description = "A prosumer can hold only one active reservation per time slot to ensure fair grid access."
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun HomeKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun PolicyItem(title: String, description: String) {
    Column {
        Text(
            text = "• $title",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = description,
            fontSize = 11.sp,
            color = Color(0xFF475569),
            modifier = Modifier.padding(start = 10.dp, top = 2.dp)
        )
    }
}

// ==========================================
// 2. MONITOR SCREEN (Member 3 Allocation & Grid)
// ==========================================
@Composable
fun MonitorScreen(
    viewModel: ReservationViewModel,
    onNavigateToTab: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val approvedKwh = state.myReservations.filter { it.status.equals("Approved", ignoreCase = true) }.sumOf { it.requestedEnergyKwh }
    val pendingKwh = state.myReservations.filter { it.status.equals("Pending", ignoreCase = true) }.sumOf { it.requestedEnergyKwh }
    val completedKwh = state.myReservations.filter { it.status.equals("Completed", ignoreCase = true) }.sumOf { it.requestedEnergyKwh }
    val totalEnergy = approvedKwh + pendingKwh + completedKwh

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Energy Monitoring & Allocation",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Member 3 • Lithira | Real-time reservation breakdown & grid status",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Total Energy Capacity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Total Prosumer Energy Reserved",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${totalEnergy.toInt()} kWh",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Energy breakdown metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AllocationMetric(label = "Approved", value = "${approvedKwh.toInt()} kWh", color = Color(0xFF1D4ED8))
                    AllocationMetric(label = "Pending", value = "${pendingKwh.toInt()} kWh", color = Color(0xFFB45309))
                    AllocationMetric(label = "Completed", value = "${completedKwh.toInt()} kWh", color = Color(0xFF15803D))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress ratio
                val approvedRatio = if (totalEnergy > 0) (approvedKwh / totalEnergy).toFloat() else 0f
                LinearProgressIndicator(
                    progress = { approvedRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF1D4ED8),
                    trackColor = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(approvedRatio * 100).toInt()}% of reserved energy is approved for grid transfer",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Solar Stations Capacity
        Text(
            text = "Active Solar Stations",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (state.stations.isEmpty()) {
            Text("No active stations found", fontSize = 13.sp, color = Color(0xFF64748B))
        } else {
            state.stations.forEach { station ->
                val stationSlots = state.slots.filter { it.stationId == station.id && it.isActive }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = station.stationName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF0F172A)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (station.isActive) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (station.isActive) "Active" else "Inactive",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (station.isActive) Color(0xFF15803D) else Color(0xFF64748B)
                                )
                            }
                        }

                        if (station.location.isNotBlank()) {
                            Text(
                                text = station.location,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Capacity: ${station.totalCapacityKwh.toInt()} kWh",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "${stationSlots.size} slots available",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Rule Compliance Audit
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Rule Compliance",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))
                RuleAuditItem(rule = "7-Day Advance Booking Window", status = "Active & Enforced")
                RuleAuditItem(rule = "12-Hour Modification/Cancel Lock", status = "Active & Enforced")
                RuleAuditItem(rule = "Single Active Slot per Prosumer", status = "Active & Enforced")
                RuleAuditItem(rule = "Offline SQLite Caching", status = "Syncing Locally")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AllocationMetric(label: String, value: String, color: Color) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun RuleAuditItem(rule: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF15803D),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = rule,
                fontSize = 12.sp,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = status,
            fontSize = 11.sp,
            color = Color(0xFF15803D),
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// 3. PROFILE SCREEN
// ==========================================
@Composable
fun ProfileScreen(
    sessionManager: SessionManager,
    viewModel: ReservationViewModel,
    onLogout: () -> Unit
) {
    val profile = sessionManager.getUserProfile()
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0xFFDCFCE7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFF15803D),
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = profile?.name ?: "Prosumer",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Text(
            text = profile?.email ?: "",
            fontSize = 13.sp,
            color = Color(0xFF64748B)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Reservation Summary
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Account details",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "NIC: ${profile?.nic ?: "N/A"}",
                    fontSize = 13.sp,
                    color = Color(0xFF334155),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Role: ${profile?.role ?: "Prosumer"}",
                    fontSize = 13.sp,
                    color = Color(0xFF334155),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Server: http://192.168.0.122:5000",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Booking summary",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Bookings:", fontSize = 12.sp, color = Color(0xFF64748B))
                    Text("${state.totalCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Energy Reserved:", fontSize = 12.sp, color = Color(0xFF64748B))
                    Text("${state.totalKwh.toInt()} kWh", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Log Out", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
