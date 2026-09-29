package com.kusal.solargridxmobile.ui.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusal.solargridxmobile.data.model.EnergyReservation
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation

// High-contrast, theme-safe colors (works in both Light & Dark system modes)
val GreenPrimary = Color(0xFF15803D)
val GreenLight = Color(0xFFDCFCE7)
val AmberPending = Color(0xFFB45309)
val AmberLight = Color(0xFFFEF3C7)
val BlueApproved = Color(0xFF1D4ED8)
val BlueLight = Color(0xFFDBEAFE)

val TextPrimary = Color(0xFF0F172A)     // Deep slate black
val TextSecondary = Color(0xFF334155)   // Slate gray
val TextMuted = Color(0xFF64748B)       // Subtitle gray
val CardBg = Color(0xFFFFFFFF)          // Pure white card
val ScreenBg = Color(0xFFF8FAFC)        // Soft light background

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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Energy Reservations",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )
                        Text(
                            text = "Book solar energy & manage reservations",
                            fontSize = 12.sp,
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
                            Text(
                                "Available Slots",
                                color = if (selectedTab == 0) GreenPrimary else TextMuted,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "My Bookings",
                                    color = if (selectedTab == 1) GreenPrimary else TextMuted,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                                if (state.myReservations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(GreenLight)
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = state.myReservations.size.toString(),
                                            fontSize = 11.sp,
                                            color = GreenPrimary,
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

        // Messages
        state.errorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        state.successMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = GreenLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = msg,
                    color = GreenPrimary,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (state.isLoading) {
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
                    onCancelClick = { reservationToCancel = it },
                    onModifyClick = { reservationToModify = it }
                )
            }
        }
    }

    // Book Slot Dialog
    if (showBookDialog && selectedSlotForBooking != null) {
        BookSlotDialog(
            slot = selectedSlotForBooking!!,
            onDismiss = {
                showBookDialog = false
                selectedSlotForBooking = null
            },
            onConfirm = { requestedKwh ->
                viewModel.createReservation(selectedSlotForBooking!!.id, requestedKwh) {
                    showBookDialog = false
                    selectedSlotForBooking = null
                }
            }
        )
    }

    // Cancel Dialog
    reservationToCancel?.let { res ->
        val (eligible, hoursLeft) = viewModel.isEligibleFor12HourRule(res)
        CancelDialog(
            reservation = res,
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
        ModifyDialog(
            reservation = res,
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
fun AvailableSlotsView(
    stations: List<SolarStation>,
    slots: List<EnergySlot>,
    onBookSlotClick: (EnergySlot) -> Unit
) {
    var selectedStationId by remember {
        mutableStateOf(stations.firstOrNull()?.id ?: "")
    }

    LaunchedEffect(stations) {
        if (selectedStationId.isEmpty() && stations.isNotEmpty()) {
            selectedStationId = stations.first().id
        }
    }

    val activeSlots = slots.filter {
        it.isActive && (selectedStationId.isEmpty() || it.stationId == selectedStationId)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Solar Station", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))

        if (stations.isEmpty()) {
            Text("No active stations available.", fontSize = 14.sp, color = TextSecondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(activeSlots) { slot ->
                    val station = stations.find { it.id == slot.stationId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = station?.stationName ?: "Solar Station",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Date: ${slot.slotDate.split("T")[0]}",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Time: ${slot.startTime} - ${slot.endTime}",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(6.dp))
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
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(Cap: ${slot.energyCapacityKwh} kWh)",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Button(
                                onClick = { onBookSlotClick(slot) },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Book", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                if (activeSlots.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No available energy slots at this time.", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyReservationsView(
    reservations: List<EnergyReservation>,
    onCancelClick: (EnergyReservation) -> Unit,
    onModifyClick: (EnergyReservation) -> Unit
) {
    if (reservations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.EventBusy,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("No reservations found.", color = TextSecondary, fontSize = 14.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(reservations) { res ->
                val isModifiable = res.status == "Pending" || res.status == "Approved"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Booking #${res.id.takeLast(6)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            StatusBadge(status = res.status)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Date: ${res.reservationDate.split("T")[0]}",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Time: ${res.startTime} - ${res.endTime}",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reserved Energy: ${res.requestedEnergyKwh} kWh",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )

                        if (isModifiable) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { onModifyClick(res) },
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Modify", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onCancelClick(res) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancel", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        "Pending" -> Pair(AmberLight, AmberPending)
        "Approved" -> Pair(BlueLight, BlueApproved)
        "Completed" -> Pair(GreenLight, GreenPrimary)
        else -> Pair(Color(0xFFE2E8F0), Color(0xFF475569))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = status, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BookSlotDialog(
    slot: EnergySlot,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var kwhText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("Book Energy Slot", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Slot Date: ${slot.slotDate.split("T")[0]}", fontSize = 13.sp, color = TextSecondary)
                Text("Time: ${slot.startTime} - ${slot.endTime}", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Available Capacity: ${slot.availableEnergyKwh} kWh",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GreenPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = kwhText,
                    onValueChange = { kwhText = it; error = null },
                    label = { Text("Energy in kWh", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = GreenPrimary,
                        unfocusedLabelColor = TextMuted
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val kwh = kwhText.toDoubleOrNull()
                    if (kwh == null || kwh <= 0.0) {
                        error = "Please enter a valid amount."
                    } else if (kwh > slot.availableEnergyKwh) {
                        error = "Cannot exceed available capacity (${slot.availableEnergyKwh} kWh)."
                    } else {
                        onConfirm(kwh)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
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
    isEligible: Boolean,
    hoursRemaining: Double,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("Cancel Reservation", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (!isEligible) {
                    Text(
                        text = "12-Hour Rule Enforced: This slot starts in ${hoursRemaining.toInt()} hours. Cancellations within 12 hours are locked.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        "Are you sure you want to cancel booking #${reservation.id.takeLast(6)}?",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason (Optional)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = GreenPrimary,
                            unfocusedLabelColor = TextMuted
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
                )
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
        title = {
            Text("Modify Energy Amount", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (!isEligible) {
                    Text(
                        text = "12-Hour Rule Enforced: Slot starts in ${hoursRemaining.toInt()} hours. Modifications within 12 hours are locked.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    OutlinedTextField(
                        value = newKwhText,
                        onValueChange = { newKwhText = it; error = null },
                        label = { Text("Updated Energy (kWh)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = GreenPrimary,
                            unfocusedLabelColor = TextMuted
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
                        error = "Enter a valid energy amount."
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
                )
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
