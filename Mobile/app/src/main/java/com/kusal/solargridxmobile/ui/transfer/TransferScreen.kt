package com.kusal.solargridxmobile.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kusal.solargridxmobile.data.api.*
import com.kusal.solargridxmobile.data.local.SessionManager
import com.kusal.solargridxmobile.data.model.EnergyReservation
import com.kusal.solargridxmobile.ui.reservation.GreenPrimary
import com.kusal.solargridxmobile.ui.reservation.ScreenBg
import com.kusal.solargridxmobile.ui.reservation.TextPrimary
import com.kusal.solargridxmobile.ui.reservation.TextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(session: SessionManager) {
    val api = ApiClient.transferService
    val scope = rememberCoroutineScope()
    val operator = session.getUserRole() in listOf("Grid Operator", "Backoffice")
    var summary by remember { mutableStateOf<DashboardSummary?>(null) }
    var bookings by remember { mutableStateOf<List<EnergyReservation>>(emptyList()) }
    var view by remember { mutableStateOf("current") }
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<EnergyReservation?>(null) }
    var ticket by remember { mutableStateOf<QrTicket?>(null) }
    var transfer by remember { mutableStateOf<EnergyTransfer?>(null) }
    var seller by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    suspend fun refresh() {
        summary = api.summary()
        val result = api.bookings(view, search.trim(), page)
        bookings = result.items; total = result.total
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; message = ""
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                message = if (e is HttpException) runCatching {
                    JSONObject(e.response()?.errorBody()?.string() ?: "{}").optString("message", "Request failed (${e.code()})")
                }.getOrDefault("Request failed (${e.code()})") else e.message ?: "Request failed"
            }
            finally { busy = false }
        }
    }
    fun change(action: String) {
        val current = transfer ?: return
        run {
            val result = when (action) {
                "start" -> api.start(current.id)
                "cancel" -> api.cancel(current.id, EndTransfer(reason.trim()))
                "fail" -> api.fail(current.id, EndTransfer(reason.trim()))
                else -> {
                    val amount = reading.toDoubleOrNull()
                    require(amount != null && amount.isFinite() && amount >= 0) { "Enter a valid cumulative kWh reading." }
                    if (action == "complete") api.complete(current.id, MeterReading(amount))
                    else api.progress(current.id, MeterReading(amount))
                }
            }
            transfer = result; refresh()
            message = if (result.status == "Completed") "Transfer completed successfully." else "Transfer ${result.status}."
        }
    }

    LaunchedEffect(Unit) { run { refresh() } }
    LaunchedEffect(ticket) {
        val current = ticket ?: return@LaunchedEffect
        delay((Instant.parse(current.expiresAt).toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0))
        ticket = null; message = "QR expired. Tap Show QR to refresh."
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { payload = it; transfer = null; message = "QR scanned. Tap Verify QR." }
    }
    val bitmap = remember(ticket) { ticket?.let { BarcodeEncoder().encodeBitmap(it.payload, BarcodeFormat.QR_CODE, 800, 800) } }

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = GreenPrimary, onPrimary = Color.White,
        surface = Color.White, onSurface = TextPrimary,
        onSurfaceVariant = TextMuted
    )) {
    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = GreenPrimary)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Energy Transfers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (operator) "Verify QR and monitor energy delivery" else "Your bookings and transaction QR", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    IconButton(enabled = !busy, onClick = { run { refresh() } }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = GreenPrimary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransferCount("Pending", summary?.pending, Color(0xFFFEF3C7), Color(0xFFB45309), Modifier.weight(1f))
                    TransferCount("Current", summary?.current, Color(0xFFDBEAFE), Color(0xFF1D4ED8), Modifier.weight(1f))
                    TransferCount("Approved", summary?.approvedFuture, Color(0xFFDCFCE7), GreenPrimary, Modifier.weight(1f))
                    TransferCount("Completed", summary?.completed, Color(0xFFF1F5F9), TextPrimary, Modifier.weight(1f))
                }
                val views = listOf("current", "pending", "history", "search")
                SecondaryTabRow(selectedTabIndex = views.indexOf(view), containerColor = Color.White, contentColor = GreenPrimary) {
                    views.forEach { value ->
                        Tab(selected = view == value, enabled = !busy,
                            onClick = { view = value; page = 1; ticket = null; run { refresh() } },
                            text = { Text(if (value == "search") "All" else value.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium) })
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (message.isNotBlank()) {
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedTextField(search, { search = it }, label = { Text("Search ID, NIC or station") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy, onClick = { page = 1; ticket = null; run { refresh() } }) { Text("Search / refresh") }
        if (bookings.isEmpty() && !busy) TransferPanel {
            Text("No bookings found", fontWeight = FontWeight.SemiBold)
            Text("Try another tab or search.", color = TextMuted)
        }
        bookings.forEach { booking ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(8.dp)) {
                            Text(booking.status, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = GreenPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                        Text("${booking.requestedEnergyKwh} kWh", fontWeight = FontWeight.Bold)
                    }
                    Text("${booking.reservationDate.take(10)} · ${booking.startTime}–${booking.endTime}")
                    Text(booking.id, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    TextButton(enabled = !busy, onClick = {
                        selected = booking; ticket = null; transfer = null
                        if (operator) run {
                            transfer = api.transfers(reservationId = booking.id).firstOrNull()
                            reading = transfer?.transferredEnergyKWh?.toString() ?: "0"
                        }
                    }) { Text("Details") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(enabled = !busy && page > 1, onClick = { page--; run { refresh() } }) { Text("Previous") }
            Text("Page $page")
            TextButton(enabled = !busy && page * 50 < total, onClick = { page++; run { refresh() } }) { Text("Next") }
        }
        selected?.let { booking ->
            TransferPanel {
            Text("Booking details", style = MaterialTheme.typography.titleMedium)
            Text("Prosumer: ${booking.prosumerNIC}\nStation: ${booking.stationId}\nSlot: ${booking.slotId}")
            if (!operator && booking.status == "Approved") {
                Button(enabled = !busy, onClick = { run { ticket = api.qr(booking.id) } }) { Text("Show QR / refresh") }
                bitmap?.let { Image(it.asImageBitmap(), "Transaction QR", Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) }
                ticket?.let { Text("Expires: ${it.expiresAt}. Show this QR to the operator.") }
            }
            }
        }
        if (operator) {
            TransferPanel {
            Text("Verify transaction", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(seller, { seller = it }, label = { Text("Seller NIC") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy, onClick = {
                scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the prosumer QR").setBeepEnabled(false).setOrientationLocked(false))
            }) { Text("Scan QR") }
            OutlinedTextField(payload, { payload = it; transfer = null }, label = { Text("QR payload") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && seller.isNotBlank() && payload.isNotBlank(), onClick = {
                run {
                    transfer = null
                    transfer = api.verify(VerifyQr(payload.trim(), seller.trim()))
                    reading = transfer!!.transferredEnergyKWh.toString()
                    payload = ""; refresh(); message = "QR verified. Ready to start."
                }
            }) { Text("Verify QR") }
            }
            transfer?.let { current ->
                TransferPanel {
                Text("${current.status}: ${current.transferredEnergyKWh}/${current.expectedEnergyKWh} kWh")
                Text("Reservation: ${current.reservationId}")
                Text("Seller: ${current.sellerId} · Buyer: ${current.buyerId}")
                if (current.status == "Pending") Button(enabled = !busy && current.verifiedAt != null, onClick = { change("start") }) { Text("Start transfer") }
                if (current.status == "InProgress") {
                    OutlinedTextField(reading, { reading = it }, label = { Text("Cumulative delivered kWh") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && reading.isNotBlank(), onClick = { change("progress") }) { Text("Save reading") }
                        Button(enabled = !busy && reading.isNotBlank(), onClick = { change("complete") }) { Text("Complete") }
                    }
                }
                if (current.status in listOf("Pending", "InProgress")) {
                    OutlinedTextField(reason, { reason = it }, label = { Text("Cancellation / failure reason") })
                    TextButton(enabled = !busy && reason.isNotBlank(), onClick = { change(if (current.status == "Pending") "cancel" else "fail") }) { Text(if (current.status == "Pending") "Cancel transfer" else "Fail transfer") }
                }
                Text("Transfer history", style = MaterialTheme.typography.titleMedium)
                current.history.forEach { Text("${it.action} · ${it.status} · ${it.actorNIC}") }
                }
            }
        }
    }
    }
    }
}

@Composable
private fun TransferCount(label: String, value: Int?, background: Color, foreground: Color, modifier: Modifier) {
    Surface(modifier, color = background, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.toString() ?: "—", fontWeight = FontWeight.Bold, color = foreground)
            Text(label, style = MaterialTheme.typography.labelSmall, color = foreground)
        }
    }
}

@Composable
private fun TransferPanel(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
