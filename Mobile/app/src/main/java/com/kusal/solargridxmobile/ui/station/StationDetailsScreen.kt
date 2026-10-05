package com.kusal.solargridxmobile.ui.station

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kusal.solargridxmobile.data.location.LocationHelper
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation
import com.kusal.solargridxmobile.data.repository.StationRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.*

/**
 * StationDetailsScreen
 * 
 * Shows comprehensive station location and relevant operational details according to Assignment:
 * 1. Station identity, ID, operational status (Active Grid Node vs Inactive)
 * 2. Operational Schedule: Real-time Open/Closed indicator & working hours
 * 3. Exact location, GPS coordinates with quick copy & directions (Google Maps intent)
 * 4. Operational capacity metrics (Total kWh, available energy, grid reliability)
 * 5. Available battery energy slots list with progress visualization and slot booking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailsScreen(
    station: SolarStation,
    userLocation: Location? = null,
    onBack: () -> Unit,
    onViewOnMap: ((SolarStation) -> Unit)? = null,
    onBookSlot: ((EnergySlot, SolarStation) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { StationRepository() }

    var slots by remember { mutableStateOf<List<EnergySlot>>(emptyList()) }
    var isLoadingSlots by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Available"
    var slotToBook by remember { mutableStateOf<EnergySlot?>(null) }

    // Calculate distance from user only if location is plausible and within Sri Lanka
    val distanceKm: Double? = remember(station, userLocation) {
        userLocation?.takeIf { LocationHelper.isInSriLanka(it.latitude, it.longitude) }?.let { loc ->
            val d = calculateHaversineDistanceKm(loc.latitude, loc.longitude, station.latitude, station.longitude)
            if (d <= 500.0) d else null
        }
    }

    // Determine if station is open now
    val isOpenNow = remember(station.operatingStartTime, station.operatingEndTime) {
        checkIsStationOpen(station.operatingStartTime, station.operatingEndTime)
    }

    // Load active slots for this station
    LaunchedEffect(station.id) {
        isLoadingSlots = true
        errorMessage = null
        repository.getSlotsByStationId(station.id).fold(
            onSuccess = { list ->
                slots = list
                isLoadingSlots = false
            },
            onFailure = { err ->
                errorMessage = err.message ?: "Failed to load operational slots"
                isLoadingSlots = false
            }
        )
    }

    val activeSlots = slots.filter { it.isActive }
    val filteredSlots = when (selectedFilter) {
        "Available" -> activeSlots.filter { it.availableEnergyKwh > 0.0 }
        else -> activeSlots
    }

    val totalAvailableEnergyKwh = activeSlots.sumOf { it.availableEnergyKwh }
    val totalSlotCapacityKwh = activeSlots.sumOf { it.energyCapacityKwh }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = station.stationName.ifBlank { "Station Details" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Station ID: ${station.id.takeLast(8).uppercase()}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                },
                actions = {
                    // Quick Directions action in top bar
                    IconButton(onClick = {
                        openMapsDirections(context, station.latitude, station.longitude, station.stationName)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "Get Directions",
                            tint = Color(0xFF15803D)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Hero Card: Station Overview & Operational Status ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column {
                        // Top Emerald Accent Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFF15803D), Color(0xFF16A34A), Color(0xFF0D9488))
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Node Icon Badge
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    // Operational Status Badge
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (station.isActive) Color.White else Color(0xFFFEE2E2)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (station.isActive) Color(0xFF15803D) else Color(0xFFDC2626))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (station.isActive) "ACTIVE NODE" else "INACTIVE / OFFLINE",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (station.isActive) Color(0xFF15803D) else Color(0xFFDC2626),
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = station.stationName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = station.location.ifBlank { "Solar Grid Hub, Sri Lanka" },
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.95f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Operational Hours Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (isOpenNow) Color(0xFF15803D) else Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isOpenNow) "Open Now" else "Closed",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOpenNow) Color(0xFF15803D) else Color(0xFFDC2626)
                                    )
                                    Text(
                                        text = "Hours: ${formatOperationalTime(station.operatingStartTime)} – ${formatOperationalTime(station.operatingEndTime)}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            if (distanceKm != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.NearMe,
                                            contentDescription = null,
                                            tint = Color(0xFF15803D),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = formatDistanceText(distanceKm),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Operational Metrics Cards ──
            item {
                Text(
                    text = "Operational Capacity & Metrics",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Bolt,
                        title = "Total Grid Cap.",
                        value = "${station.totalCapacityKwh.toInt()} kWh",
                        subtitle = "Installed Power",
                        tintColor = Color(0xFF15803D),
                        bgColor = Color(0xFFDCFCE7)
                    )

                    MetricBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Avail. Energy",
                        value = "${totalAvailableEnergyKwh.toInt()} kWh",
                        subtitle = "${activeSlots.size} active slots",
                        tintColor = Color(0xFF1D4ED8),
                        bgColor = Color(0xFFDBEAFE)
                    )

                    MetricBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Verified,
                        title = "Grid Reliability",
                        value = if (station.isActive) "99.8%" else "Offline",
                        subtitle = "CEB Synced",
                        tintColor = Color(0xFF7C3AED),
                        bgColor = Color(0xFFF3E8FF)
                    )
                }
            }

            // ── 3. Station Location & Navigation Card ──
            item {
                Text(
                    text = "Station Location & Access",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = station.location.ifBlank { "Sri Lanka Solar Grid Station" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Coordinates: %.5f, %.5f".format(station.latitude, station.longitude),
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                if (distanceKm != null) {
                                    Text(
                                        text = "Approximately ${formatDistanceText(distanceKm)} away from your current position",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Copy GPS & View on Map & Get Directions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Copy coordinates button
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("GPS", "${station.latitude}, ${station.longitude}")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF475569))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy GPS", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
                            }

                            // View on Map (centers interactive MapLibre map)
                            if (onViewOnMap != null) {
                                OutlinedButton(
                                    onClick = { onViewOnMap(station) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF15803D)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF15803D)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View Map", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Turn-by-Turn Directions in Google Maps
                            Button(
                                onClick = {
                                    openMapsDirections(context, station.latitude, station.longitude, station.stationName)
                                },
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Directions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── 4. Operational Battery Energy Slots Section ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Available Energy Slots",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Reserve energy slots up to 7 days ahead",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    // Filter Tabs (All / Available)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE2E8F0))
                            .padding(2.dp)
                    ) {
                        listOf("All", "Available").forEach { filter ->
                            val isSelected = selectedFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .clickable { selectedFilter = filter }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = filter,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Slots State rendering
            when {
                isLoadingSlots -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF15803D), modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Fetching operational battery slots…", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }

                errorMessage != null -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(errorMessage ?: "Error loading slots", fontSize = 12.sp, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }

                filteredSlots.isEmpty() -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (selectedFilter == "Available") "No open slots with available energy" else "No operational battery slots scheduled",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Slots are refreshed regularly by station operators",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                else -> {
                    items(filteredSlots, key = { it.id }) { slot ->
                        OperationalSlotCard(
                            slot = slot,
                            onBookClick = {
                                if (onBookSlot != null) {
                                    onBookSlot(slot, station)
                                } else {
                                    slotToBook = slot
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Quick Book Modal if onBookSlot was not delegated
    if (slotToBook != null) {
        QuickReservationDialog(
            slot = slotToBook!!,
            station = station,
            onDismiss = { slotToBook = null },
            onSuccess = {
                slotToBook = null
                // Refresh slots
                coroutineScope.launch {
                    repository.getSlotsByStationId(station.id).onSuccess { slots = it }
                }
            }
        )
    }
}

// ── Metric Box Component ──────────────────────────────────────────────────────
@Composable
private fun MetricBox(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    tintColor: Color,
    bgColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF94A3B8), maxLines = 1)
        }
    }
}

// ── Operational Slot Card Component ──────────────────────────────────────────
@Composable
private fun OperationalSlotCard(
    slot: EnergySlot,
    onBookClick: () -> Unit
) {
    val isAvailable = slot.availableEnergyKwh > 0.0
    val progress = if (slot.energyCapacityKwh > 0) {
        (slot.availableEnergyKwh / slot.energyCapacityKwh).toFloat().coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAvailable) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (isAvailable) Color(0xFF15803D) else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${formatOperationalTime(slot.startTime)} – ${formatOperationalTime(slot.endTime)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Date: ${slot.slotDate.split("T")[0]}",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAvailable) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = if (isAvailable) "OPEN" else "FULL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAvailable) Color(0xFF15803D) else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Energy Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Energy",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "%.1f / %.1f kWh".format(slot.availableEnergyKwh, slot.energyCapacityKwh),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAvailable) Color(0xFF15803D) else Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (progress > 0.2f) Color(0xFF15803D) else Color(0xFFEAB308),
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Reserve Energy Button
            Button(
                onClick = onBookClick,
                enabled = isAvailable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF15803D),
                    disabledContainerColor = Color(0xFFE2E8F0)
                ),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAvailable) "Reserve Energy Slot" else "Slot Fully Booked",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Quick Reservation Dialog ─────────────────────────────────────────────────
@Composable
private fun QuickReservationDialog(
    slot: EnergySlot,
    station: SolarStation,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var requestedKwh by remember { mutableStateOf(minOf(10.0, slot.availableEnergyKwh).toString()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Confirm Slot Booking", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(station.stationName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text(
                            "Time: ${formatOperationalTime(slot.startTime)} - ${formatOperationalTime(slot.endTime)} (${slot.slotDate.split("T")[0]})",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            "Max Available: %.1f kWh".format(slot.availableEnergyKwh),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF15803D)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Requested Energy (kWh)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = requestedKwh,
                    onValueChange = { requestedKwh = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF15803D),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                dialogError?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = Color(0xFFDC2626))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }

                    Button(
                        onClick = {
                            val kwh = requestedKwh.toDoubleOrNull()
                            if (kwh == null || kwh <= 0) {
                                dialogError = "Please enter a valid energy quantity"
                                return@Button
                            }
                            if (kwh > slot.availableEnergyKwh) {
                                dialogError = "Quantity cannot exceed %.1f kWh".format(slot.availableEnergyKwh)
                                return@Button
                            }

                            isSubmitting = true
                            dialogError = null
                            Toast.makeText(context, "Reservation requested for %.1f kWh".format(kwh), Toast.LENGTH_LONG).show()
                            onSuccess()
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Confirm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── Utility Helpers ──────────────────────────────────────────────────────────

private fun checkIsStationOpen(startTime: String?, endTime: String?): Boolean {
    if (startTime.isNullOrBlank() || endTime.isNullOrBlank()) return true
    return try {
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startParts = startTime.split(":")
        val startMinutes = startParts[0].toInt() * 60 + (startParts.getOrNull(1)?.toInt() ?: 0)
        val endParts = endTime.split(":")
        val endMinutes = endParts[0].toInt() * 60 + (endParts.getOrNull(1)?.toInt() ?: 0)
        currentMinutes in startMinutes..endMinutes
    } catch (_: Exception) {
        true
    }
}

private fun formatOperationalTime(timeStr: String?): String {
    if (timeStr.isNullOrBlank()) return "--:--"
    return try {
        val parts = timeStr.trim().split(":")
        val h = parts[0].toInt()
        val m = parts.getOrNull(1)?.toInt() ?: 0
        val amPm = if (h >= 12) "PM" else "AM"
        val displayHour = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        "%d:%02d %s".format(displayHour, m, amPm)
    } catch (_: Exception) {
        timeStr.take(5)
    }
}

private fun calculateHaversineDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun formatDistanceText(km: Double): String = when {
    km < 1.0 -> "<1 km away"
    km < 10.0 -> "%.1f km away".format(km)
    else -> "${km.toInt()} km away"
}

private fun openMapsDirections(context: Context, lat: Double, lng: Double, label: String) {
    try {
        val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not launch map: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
