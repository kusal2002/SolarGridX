package com.kusal.solargridxmobile.ui.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusal.solargridxmobile.data.model.EnergyReservation
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// High-contrast, theme-safe colors
val GreenPrimary = Color(0xFF15803D)
val GreenLight = Color(0xFFDCFCE7)
val GreenDark = Color(0xFF14532D)

val AmberPending = Color(0xFFB45309)
val AmberLight = Color(0xFFFEF3C7)

val BlueApproved = Color(0xFF1D4ED8)
val BlueLight = Color(0xFFDBEAFE)

val TextPrimary = Color(0xFF0F172A)     // Deep slate black
val TextSecondary = Color(0xFF334155)   // Slate gray
val TextMuted = Color(0xFF64748B)       // Subtitle gray
val CardBg = Color(0xFFFFFFFF)          // Pure white card
val ScreenBg = Color(0xFFF8FAFC)        // Soft light background
val BorderLight = Color(0xFFE2E8F0)     // Divider color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationScreen(
    viewModel: ReservationViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog states
    var showBookDialog by remember { mutableStateOf(false) }
    var selectedSlotForBooking by remember { mutableStateOf<EnergySlot?>(null) }
    var reservationToCancel by remember { mutableStateOf<EnergyReservation?>(null) }
    var reservationToModify by remember { mutableStateOf<EnergyReservation?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Top Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Energy Reservations",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Member 3 • Lithira | Advance slots & 12h policy",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    IconButton(onClick = { viewModel.loadAllData() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = GreenPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // KPI Mini Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiMiniChip(
                        label = "Total",
                        value = "${state.totalCount}",
                        bg = Color(0xFFF1F5F9),
                        fg = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniChip(
                        label = "Pending",
                        value = "${state.pendingCount}",
                        bg = AmberLight,
                        fg = AmberPending,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniChip(
                        label = "Approved",
                        value = "${state.approvedCount}",
                        bg = BlueLight,
                        fg = BlueApproved,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniChip(
                        label = "Energy",
                        value = "${state.totalKwh.toInt()} kWh",
                        bg = GreenLight,
                        fg = GreenPrimary,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CardBg,
                    contentColor = GreenPrimary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 0) GreenPrimary else TextMuted
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Available Slots",
                                    color = if (selectedTab == 0) GreenPrimary else TextMuted,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 1) GreenPrimary else TextMuted
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "My Bookings",
                                    color = if (selectedTab == 1) GreenPrimary else TextMuted,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                if (state.myReservations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selectedTab == 1) GreenPrimary else Color(0xFFE2E8F0))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = state.myReservations.size.toString(),
                                            fontSize = 11.sp,
                                            color = if (selectedTab == 1) Color.White else TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        // Error message banner
        state.errorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { viewModel.clearMessages() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Success message banner
        state.successMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = GreenLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        color = GreenDark,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { viewModel.clearMessages() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = GreenDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (state.isLoading && state.slots.isEmpty() && state.myReservations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
        } else {
            when (selectedTab) {
                0 -> AvailableSlotsView(
                    stations = state.stations,
                    slots = state.slots,
                    onBookSlotClick = { slot ->
                        selectedSlotForBooking = slot
                        showBookDialog = true
                    }
                )
                1 -> MyReservationsView(
                    reservations = state.myReservations,
                    stations = state.stations,
                    viewModel = viewModel,
                    onCancelClick = { reservationToCancel = it },
                    onModifyClick = { reservationToModify = it }
                )
            }
        }
    }

    // Book Slot Dialog
    if (showBookDialog && selectedSlotForBooking != null) {
        val slot = selectedSlotForBooking!!
        val station = state.stations.find { it.id == slot.stationId }
        BookSlotDialog(
            slot = slot,
            stationName = station?.stationName ?: "Solar Station",
            stationLocation = station?.location ?: "",
            onDismiss = {
                showBookDialog = false
                selectedSlotForBooking = null
            },
            onConfirm = { requestedKwh ->
                viewModel.createReservation(slot.id, requestedKwh) {
                    showBookDialog = false
                    selectedSlotForBooking = null
                }
            }
        )
    }

    // Cancel Dialog
    reservationToCancel?.let { res ->
        val (eligible, hoursLeft) = viewModel.isEligibleFor12HourRule(res)
        val station = state.stations.find { it.id == res.stationId }
        CancelDialog(
            reservation = res,
            stationName = station?.stationName ?: res.stationName ?: "Solar Station",
            isEligible = eligible,
            hoursRemaining = hoursLeft,
            onDismiss = { reservationToCancel = null },
            onConfirm = { reason ->
                viewModel.cancelReservation(res.id, reason) {
                    reservationToCancel = null
                }
            }
        )
    }

    // Modify Dialog
    reservationToModify?.let { res ->
        val (eligible, hoursLeft) = viewModel.isEligibleFor12HourRule(res)
        val station = state.stations.find { it.id == res.stationId }
        ModifyDialog(
            reservation = res,
            stationName = station?.stationName ?: res.stationName ?: "Solar Station",
            isEligible = eligible,
            hoursRemaining = hoursLeft,
            onDismiss = { reservationToModify = null },
            onConfirm = { newKwh ->
                viewModel.modifyReservation(res.id, null, newKwh) {
                    reservationToModify = null
                }
            }
        )
    }
}

@Composable
fun KpiMiniChip(
    label: String,
    value: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = fg
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = fg.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==========================================
// 1. AVAILABLE SLOTS VIEW (Member 3)
// ==========================================

// Helper: checks if slot has passed current time (expired)
fun isSlotExpired(slot: EnergySlot): Boolean {
    return try {
        val datePart = slot.slotDate.split("T")[0].trim()
        val timeSource = if (slot.endTime.isNotBlank()) slot.endTime.trim() else slot.startTime.trim()
        val timeParts = timeSource.split(":")
        val hour = timeParts.getOrNull(0)?.padStart(2, '0') ?: "00"
        val minute = timeParts.getOrNull(1)?.padStart(2, '0') ?: "00"
        val second = timeParts.getOrNull(2)?.padStart(2, '0') ?: "00"

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val slotDateTime = sdf.parse("$datePart $hour:$minute:$second")
        val now = System.currentTimeMillis()
        (slotDateTime?.time ?: Long.MAX_VALUE) <= now
    } catch (e: Exception) {
        false
    }
}

// Helper: checks if slot is beyond 7-day advance booking window
fun isSlotBeyond7Days(slot: EnergySlot): Boolean {
    return try {
        val datePart = slot.slotDate.split("T")[0].trim()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val slotDate = sdf.parse(datePart) ?: return false
        val now = System.currentTimeMillis()
        val diffMs = slotDate.time - now
        val diffDays = diffMs / (1000.0 * 60 * 60 * 24)
        diffDays > 7.0
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableSlotsView(
    stations: List<SolarStation>,
    slots: List<EnergySlot>,
    onBookSlotClick: (EnergySlot) -> Unit
) {
    var selectedStationId by remember { mutableStateOf("") } // "" means All Stations
    var isStationDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSlots = slots.filter { slot ->
        val station = stations.find { it.id == slot.stationId }
        val matchesStation = selectedStationId.isEmpty() || slot.stationId == selectedStationId
        val matchesSearch = searchQuery.isBlank() ||
                (station?.stationName?.contains(searchQuery, ignoreCase = true) == true) ||
                (station?.location?.contains(searchQuery, ignoreCase = true) == true) ||
                slot.slotDate.contains(searchQuery, ignoreCase = true) ||
                slot.startTime.contains(searchQuery, ignoreCase = true)

        slot.isActive && !isSlotExpired(slot) && !isSlotBeyond7Days(slot) && slot.availableEnergyKwh > 0.0 && matchesStation && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 7-Day Window Policy Banner (Member 3 requirement)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = BlueApproved,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "7-Day Advance Window: Slots open up to 7 days ahead. Bookings require admin review before approval.",
                    fontSize = 11.sp,
                    color = Color(0xFF1E3A8A),
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Station Dropdown Selection (Member 3)
        ExposedDropdownMenuBox(
            expanded = isStationDropdownExpanded,
            onExpandedChange = { isStationDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            val selectedStation = stations.find { it.id == selectedStationId }
            val stationDisplayText = if (selectedStationId.isEmpty()) {
                val totalActive = slots.count { it.isActive && !isSlotExpired(it) && !isSlotBeyond7Days(it) && it.availableEnergyKwh > 0.0 }
                "All Stations ($totalActive available)"
            } else {
                val stationActive = slots.count { it.stationId == selectedStationId && it.isActive && !isSlotExpired(it) && !isSlotBeyond7Days(it) && it.availableEnergyKwh > 0.0 }
                "${selectedStation?.stationName ?: "Selected Station"} ($stationActive available)"
            }

            OutlinedTextField(
                value = stationDisplayText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Filter by Station", fontSize = 12.sp, color = TextSecondary) },
                leadingIcon = {
                    Icon(
                        Icons.Default.EvStation,
                        contentDescription = "Solar Station",
                        tint = GreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isStationDropdownExpanded)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = BorderLight,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = GreenPrimary,
                    unfocusedLabelColor = TextSecondary
                ),
                singleLine = true
            )

            ExposedDropdownMenu(
                expanded = isStationDropdownExpanded,
                onDismissRequest = { isStationDropdownExpanded = false },
                modifier = Modifier.background(CardBg)
            ) {
                // Option 1: All Stations
                val isAllSelected = selectedStationId.isEmpty()
                val totalActiveSlots = slots.count { it.isActive && !isSlotExpired(it) && !isSlotBeyond7Days(it) && it.availableEnergyKwh > 0.0 }

                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "All Solar Stations",
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllSelected) GreenPrimary else TextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "View slots across all solar stations",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isAllSelected) GreenLight else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$totalActiveSlots slots",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAllSelected) GreenDark else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = null,
                            tint = if (isAllSelected) GreenPrimary else TextMuted
                        )
                    },
                    trailingIcon = {
                        if (isAllSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = GreenPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        selectedStationId = ""
                        isStationDropdownExpanded = false
                    }
                )

                HorizontalDivider(color = BorderLight.copy(alpha = 0.6f))

                if (stations.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No stations available",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        },
                        onClick = { isStationDropdownExpanded = false }
                    )
                } else {
                    stations.forEach { station ->
                        val isSelected = selectedStationId == station.id
                        val count = slots.count { it.stationId == station.id && it.isActive && !isSlotExpired(it) && !isSlotBeyond7Days(it) && it.availableEnergyKwh > 0.0 }

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = station.stationName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) GreenPrimary else TextPrimary,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (station.location.isNotBlank()) {
                                            Text(
                                                text = station.location,
                                                fontSize = 11.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = if (isSelected) GreenLight else Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "$count slots",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) GreenDark else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.EvStation,
                                    contentDescription = null,
                                    tint = if (isSelected) GreenPrimary else TextMuted
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                selectedStationId = station.id
                                isStationDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search location, date, or time...", fontSize = 13.sp, color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedBorderColor = GreenPrimary,
                unfocusedBorderColor = BorderLight,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Slot List
        if (filteredSlots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.EventBusy,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No matching energy slots found",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Try adjusting your station filter or search query",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredSlots) { slot ->
                    val station = stations.find { it.id == slot.stationId }
                    SlotCard(
                        slot = slot,
                        station = station,
                        onBookClick = { onBookSlotClick(slot) }
                    )
                }
            }
        }
    }
}

@Composable
fun SlotCard(
    slot: EnergySlot,
    station: SolarStation?,
    onBookClick: () -> Unit
) {
    val freeCapacity = slot.availableEnergyKwh
    val totalCapacity = if (slot.energyCapacityKwh > 0) slot.energyCapacityKwh else freeCapacity
    val fillPercent = if (totalCapacity > 0) ((totalCapacity - freeCapacity) / totalCapacity).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Station and Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station?.stationName ?: "Solar Station",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!station?.location.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = station?.location ?: "",
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Date Pill
                val cleanDate = slot.slotDate.split("T")[0]
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cleanDate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time & Energy Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${slot.startTime.take(5)} - ${slot.endTime.take(5)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${slot.availableEnergyKwh} kWh free",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Capacity Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { fillPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = GreenPrimary,
                    trackColor = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Booked: ${(totalCapacity - freeCapacity).toInt()} kWh",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "Cap: ${totalCapacity.toInt()} kWh",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onBookClick,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Book Slot",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. MY RESERVATIONS VIEW (Member 3)
// ==========================================
@Composable
fun MyReservationsView(
    reservations: List<EnergyReservation>,
    stations: List<SolarStation>,
    viewModel: ReservationViewModel,
    onCancelClick: (EnergyReservation) -> Unit,
    onModifyClick: (EnergyReservation) -> Unit
) {
    var statusFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = reservations.filter { res ->
        val station = stations.find { it.id == res.stationId }
        val matchesStatus = statusFilter == "All" || res.status.equals(statusFilter, ignoreCase = true)
        val term = searchQuery.trim().lowercase()
        val matchesSearch = term.isEmpty() ||
                res.id.lowercase().contains(term) ||
                res.reservationDate.lowercase().contains(term) ||
                (station?.stationName?.lowercase()?.contains(term) == true) ||
                (res.stationName?.lowercase()?.contains(term) == true)

        matchesStatus && matchesSearch
    }.sortedByDescending { it.createdAt }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by Booking ID, station, date...", fontSize = 13.sp, color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedBorderColor = GreenPrimary,
                unfocusedBorderColor = BorderLight,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Status Filter Chips
        val statusList = listOf("All", "Pending", "Approved", "Completed", "Cancelled")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(statusList) { status ->
                val count = if (status == "All") reservations.size else reservations.count { it.status.equals(status, ignoreCase = true) }
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { statusFilter = status },
                    label = {
                        Text(
                            text = "$status ($count)",
                            fontSize = 12.sp,
                            fontWeight = if (statusFilter == status) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (status) {
                            "Pending" -> AmberPending
                            "Approved" -> BlueApproved
                            "Completed" -> GreenPrimary
                            "Cancelled" -> Color(0xFF64748B)
                            else -> GreenPrimary
                        },
                        selectedLabelColor = Color.White,
                        containerColor = CardBg,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.EventBusy,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (reservations.isEmpty()) "No reservations booked yet" else "No matching reservations",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        text = if (reservations.isEmpty()) "Browse 'Available Slots' to book solar energy" else "Adjust filter or search to view bookings",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList) { res ->
                    val station = stations.find { it.id == res.stationId }
                    val (isEligible, hoursRemaining) = viewModel.isEligibleFor12HourRule(res)

                    ReservationCard(
                        reservation = res,
                        stationName = station?.stationName ?: res.stationName ?: "Solar Station",
                        stationLocation = station?.location ?: "",
                        isEligible12h = isEligible,
                        hoursRemaining = hoursRemaining,
                        onCancelClick = { onCancelClick(res) },
                        onModifyClick = { onModifyClick(res) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReservationCard(
    reservation: EnergyReservation,
    stationName: String,
    stationLocation: String,
    isEligible12h: Boolean,
    hoursRemaining: Double,
    onCancelClick: () -> Unit,
    onModifyClick: () -> Unit
) {
    val isActionable = reservation.status.equals("Pending", ignoreCase = true) ||
            reservation.status.equals("Approved", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: ID + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Booking #${reservation.id.takeLast(6).uppercase()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                StatusBadge(status = reservation.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Station Name
            Text(
                text = stationName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = TextPrimary
            )
            if (stationLocation.isNotBlank()) {
                Text(
                    text = stationLocation,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Date & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Date: ${reservation.reservationDate.split("T")[0]}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${reservation.startTime.take(5)} - ${reservation.endTime.take(5)}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Energy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reserved Energy",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Text(
                    text = "${reservation.requestedEnergyKwh} kWh",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GreenPrimary
                )
            }

            // Cancellation Reason (if cancelled)
            if (reservation.status.equals("Cancelled", ignoreCase = true) && !reservation.cancellationReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Reason: ${reservation.cancellationReason}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // 12-Hour Rule compliance badge (Member 3 requirement)
            if (isActionable) {
                Spacer(modifier = Modifier.height(10.dp))
                val hoursText = if (hoursRemaining > 0) "${hoursRemaining.toInt()}h left" else "Passed"
                val ruleColor = if (isEligible12h) Color(0xFF15803D) else Color(0xFFDC2626)
                val ruleBg = if (isEligible12h) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                val ruleBorder = if (isEligible12h) Color(0xFFBBF7D0) else Color(0xFFFECACA)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ruleBg)
                        .border(1.dp, ruleBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isEligible12h) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = ruleColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEligible12h) "12h Rule: Modifiable" else "12h Rule: Locked",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ruleColor
                            )
                        }
                        Text(
                            text = hoursText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ruleColor
                        )
                    }
                }

                // Action buttons
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onModifyClick,
                        enabled = isEligible12h,
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextPrimary,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Modify",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onCancelClick,
                        enabled = isEligible12h,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Cancel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// STATUS BADGE
// ==========================================
@Composable
fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        "Pending" -> Pair(AmberLight, AmberPending)
        "Approved" -> Pair(BlueLight, BlueApproved)
        "Completed" -> Pair(GreenLight, GreenPrimary)
        "Cancelled" -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
        else -> Pair(Color(0xFFE2E8F0), Color(0xFF475569))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = status,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// 3. DIALOGS (Member 3)
// ==========================================
@Composable
fun BookSlotDialog(
    slot: EnergySlot,
    stationName: String,
    stationLocation: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var kwhText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Book Energy Slot",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = stationName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (stationLocation.isNotBlank()) {
                    Text(
                        text = stationLocation,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Date", fontSize = 12.sp, color = TextMuted)
                            Text(slot.slotDate.split("T")[0], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Time Window", fontSize = 12.sp, color = TextMuted)
                            Text("${slot.startTime.take(5)} - ${slot.endTime.take(5)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Available Capacity", fontSize = 12.sp, color = TextMuted)
                            Text("${slot.availableEnergyKwh} kWh", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = kwhText,
                    onValueChange = {
                        kwhText = it
                        error = null
                    },
                    label = { Text("Requested Energy (kWh)", color = TextSecondary) },
                    singleLine = true,
                    placeholder = { Text("e.g. 50", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = GreenPrimary,
                        unfocusedLabelColor = TextMuted,
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = BorderLight
                    )
                )

                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 7-Day Window: Advance booking policy applies.\n• Max energy is limited to slot capacity (${slot.availableEnergyKwh} kWh).",
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val kwh = kwhText.toDoubleOrNull()
                    if (kwh == null || kwh <= 0.0) {
                        error = "Please enter a valid amount greater than 0."
                    } else if (kwh > slot.availableEnergyKwh) {
                        error = "Cannot exceed available capacity (${slot.availableEnergyKwh} kWh)."
                    } else {
                        onConfirm(kwh)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Booking", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun CancelDialog(
    reservation: EnergyReservation,
    stationName: String,
    isEligible: Boolean,
    hoursRemaining: Double,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Cancel Reservation", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                if (!isEligible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "12-Hour Cancellation Rule Enforced",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Slot begins in ${hoursRemaining.toInt()} hours. Policy prohibits cancellations within 12 hours of the reserved window.",
                                fontSize = 11.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                } else {
                    Text(
                        "Are you sure you want to cancel booking #${reservation.id.takeLast(6).uppercase()} at $stationName?",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "• Hours remaining before slot: ${hoursRemaining.toInt()}h (12h rule satisfied).",
                        fontSize = 11.sp,
                        color = GreenPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason for cancellation (optional)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = GreenPrimary,
                            unfocusedLabelColor = TextMuted,
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = BorderLight
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.ifBlank { null }) },
                enabled = isEligible,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Cancel", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun ModifyDialog(
    reservation: EnergyReservation,
    stationName: String,
    isEligible: Boolean,
    hoursRemaining: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var newKwhText by remember { mutableStateOf(reservation.requestedEnergyKwh.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text("Modify Energy Amount", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                if (!isEligible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "12-Hour Modification Rule Enforced",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Slot begins in ${hoursRemaining.toInt()} hours. Modifications within 12 hours of the slot start are locked by system policy.",
                                fontSize = 11.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                } else {
                    Text(
                        "Updating energy for booking #${reservation.id.takeLast(6).uppercase()} at $stationName.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "• Hours remaining before slot: ${hoursRemaining.toInt()}h (12h rule satisfied).",
                        fontSize = 11.sp,
                        color = GreenPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newKwhText,
                        onValueChange = {
                            newKwhText = it
                            error = null
                        },
                        label = { Text("Updated Energy (kWh)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = GreenPrimary,
                            unfocusedLabelColor = TextMuted,
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = BorderLight
                        )
                    )
                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val kwh = newKwhText.toDoubleOrNull()
                    if (kwh == null || kwh <= 0.0) {
                        error = "Enter a valid energy amount greater than 0."
                    } else {
                        onConfirm(kwh)
                    }
                },
                enabled = isEligible,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenPrimary,
                    disabledContainerColor = GreenPrimary.copy(alpha = 0.4f),
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
