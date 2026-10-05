package com.kusal.solargridxmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import com.kusal.solargridxmobile.ui.theme.MobileMetric
import com.kusal.solargridxmobile.ui.theme.SolarGridXMobileTheme
import androidx.compose.foundation.lazy.LazyColumn
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
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.solargridx_mark), "SolarGridX logo", Modifier.size(32.dp))
                    Text("SolarGridX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        },
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
                1 -> StationMapScreen(reservationViewModel = reservationViewModel)
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
fun HomeScreen(sessionManager: SessionManager, viewModel: ReservationViewModel, onNavigateToTab: (Int) -> Unit) {
    val profile = sessionManager.getUserProfile()
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadAllData() }
    val next = state.nextUpcomingReservation
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your energy workspace", style = MaterialTheme.typography.titleLarge)
                Text(profile?.name ?: "Prosumer", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Reserve a slot, show your QR and track delivery.", style = MaterialTheme.typography.bodySmall)
            }
        } }
        if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { viewModel.loadAllData() }) { Text("Retry") } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MobileMetric("Pending", state.pendingCount.toString(), Icons.Default.Schedule, Modifier.weight(1f)); MobileMetric("Approved", state.approvedCount.toString(), Icons.Default.CheckCircle, Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MobileMetric("Completed", state.completedCount.toString(), Icons.Default.DoneAll, Modifier.weight(1f)); MobileMetric("Reserved energy", "${state.totalKwh.toInt()} kWh", Icons.Default.Bolt, Modifier.weight(1f)) } }
        item { Text("Next booking", style = MaterialTheme.typography.titleMedium) }
        item { OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (next == null) { Text("Ready for your next visit?", style = MaterialTheme.typography.titleMedium); Text("Find a station and reserve an available slot.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else {
                    Text(state.stations.find { it.id == next.stationId }?.stationName ?: "Your station", style = MaterialTheme.typography.titleMedium)
                    Text("${next.reservationDate.take(10)} · ${next.startTime.take(5)}–${next.endTime.take(5)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${next.requestedEnergyKwh} kWh · ${next.status}", color = MaterialTheme.colorScheme.primary)
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = { onNavigateToTab(if (next?.status == "Approved") 3 else 2) }) { Text(if (next?.status == "Approved") "Open booking QR" else if (next == null) "Reserve a slot" else "Manage booking") }
            }
        } }
        item { Text("Quick actions", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigateToTab(1) }) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("Stations") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigateToTab(3) }) { Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text("History & QR") }
        } }
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
fun ProfileScreen(sessionManager: SessionManager, viewModel: ReservationViewModel, onLogout: () -> Unit) {
    val profile = sessionManager.getUserProfile()
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("My profile", style = MaterialTheme.typography.titleLarge)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Person, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Column { Text(profile?.name ?: "Account", style = MaterialTheme.typography.titleMedium); Text(profile?.role ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Account information", style = MaterialTheme.typography.titleMedium)
                Text("Email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(profile?.email ?: "—")
                HorizontalDivider()
                Text("NIC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(profile?.nic ?: "—")
            }
        }
        if (profile?.role == "Prosumer") Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MobileMetric("Bookings", state.totalCount.toString(), Icons.Default.CalendarMonth, Modifier.weight(1f)); MobileMetric("Reserved energy", "${state.totalKwh.toInt()} kWh", Icons.Default.Bolt, Modifier.weight(1f)) }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("Log out") }
    }
}
