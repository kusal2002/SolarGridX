package com.kusal.solargridxmobile.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kusal.solargridxmobile.data.api.*
import com.kusal.solargridxmobile.data.local.SessionManager
import com.kusal.solargridxmobile.data.model.SolarStation
import com.kusal.solargridxmobile.ui.reservation.GreenPrimary
import com.kusal.solargridxmobile.ui.reservation.TextPrimary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun OperatorHomeScreen(session: SessionManager, onTransfers: () -> Unit) {
    var stations by remember { mutableStateOf<List<SolarStation>>(emptyList()) }
    var stationId by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf<DashboardSummary?>(null) }
    var completed by remember { mutableStateOf<List<EnergyTransfer>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            busy = true; error = ""
            try {
                stations = ApiClient.transferService.dashboardStations()
                stats = ApiClient.transferService.summary(stationId)
                completed = ApiClient.transferService.transfers(status = "Completed", stationId = stationId, pageSize = 5)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load station statistics"; stats = null; completed = emptyList() }
            finally { busy = false }
        }
    }
    LaunchedEffect(stationId) { refresh() }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = GreenPrimary, onPrimary = Color.White, surface = Color.White, onSurface = TextPrimary)) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Welcome, ${session.getUserProfile()?.name ?: "Operator"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(if (session.getUserRole() == "Backoffice") "System operations" else "Your assigned station operations", color = TextPrimary)
        Box {
            OutlinedButton(enabled = !busy, onClick = { expanded = true }) { Text(stations.find { it.id == stationId }?.stationName ?: "All assigned stations") }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("All accessible stations") }, onClick = { stationId = null; expanded = false })
                stations.forEach { station -> DropdownMenuItem(text = { Text(station.stationName) }, onClick = { stationId = station.id; expanded = false }) }
            }
        }
        TextButton(enabled = !busy, onClick = { refresh() }) { Text("Refresh statistics") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (!busy && error.isBlank() && stations.isEmpty()) Text("Ask Backoffice to assign you to a station in Station Management.", color = TextPrimary)
        stats?.let { counts ->
            val cards = listOf("Stations" to counts.stationCount.toString(), "Upcoming active slots" to counts.activeSlots.toString(),
                "Pending bookings" to counts.pending.toString(), "Current bookings" to counts.current.toString(),
                "Transfers in progress" to counts.activeTransfers.toString(), "Completed transfers" to counts.completedTransfers.toString(),
                "Available energy" to "${counts.availableEnergyKwh} kWh", "Completed energy" to "${counts.deliveredEnergyKwh} kWh")
            cards.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value) ->
                        Card(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = onTransfers, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) { Text("Open transfers & history") }
        Text("Recently completed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        if (!busy && completed.isEmpty()) Text("No completed transfers yet.", color = TextPrimary)
        completed.forEach { transfer ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text(stations.find { it.id == transfer.stationId }?.stationName ?: "Station unavailable", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("${transfer.prosumerNIC} · ${transfer.transferredEnergyKWh} kWh · Completed", color = TextPrimary)
                }
            }
        }
    }
    }
}
