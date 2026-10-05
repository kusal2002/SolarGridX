package com.kusal.solargridxmobile.ui.transfer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kusal.solargridxmobile.data.api.*
import com.kusal.solargridxmobile.data.local.SessionManager
import com.kusal.solargridxmobile.data.model.SolarStation
import com.kusal.solargridxmobile.ui.theme.MobileMetric
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun OperatorHomeScreen(session: SessionManager, onTransfers: () -> Unit) {
    var stations by remember { mutableStateOf<List<SolarStation>>(emptyList()) }
    var stationId by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf<DashboardSummary?>(null) }
    var completed by remember { mutableStateOf<List<EnergyTransfer>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var requestId by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        val request = ++requestId
        val filter = stationId
        scope.launch {
            busy = true; error = ""
            try {
                val stationList = ApiClient.transferService.dashboardStations()
                val counts = ApiClient.transferService.summary(filter)
                val history = ApiClient.transferService.transfers(status = "Completed", stationId = filter, pageSize = 5)
                if (request == requestId) { stations = stationList; stats = counts; completed = history }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (request == requestId) { error = "Unable to load station overview. Check your connection and retry."; stats = null; completed = emptyList() } }
            finally { if (request == requestId) busy = false }
        }
    }
    LaunchedEffect(stationId) { refresh() }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (session.getUserRole() == "Backoffice") "System overview" else "Station overview", style = MaterialTheme.typography.titleLarge)
                            Text(session.getUserProfile()?.name ?: "Grid Operator", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        IconButton(enabled = !busy, onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh overview") }
                    }
                    Text("Bookings, energy and delivery in one place", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            Box {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { expanded = true }) {
                    Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(8.dp))
                    Text(stations.find { it.id == stationId }?.stationName ?: "All accessible stations", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ExpandMore, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
                    DropdownMenuItem(text = { Text("All accessible stations", color = MaterialTheme.colorScheme.onSurface) }, onClick = { stationId = null; expanded = false })
                    stations.forEach { station -> DropdownMenuItem(text = { Text(station.stationName, color = MaterialTheme.colorScheme.onSurface) }, onClick = { stationId = station.id; expanded = false }) }
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error.isNotBlank()) item { OutlinedCard { Column(Modifier.padding(16.dp)) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refresh() }) { Text("Retry") } } } }
        if (!busy && error.isBlank() && stations.isEmpty()) item { Text("Ask Backoffice to assign your account to a station.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = onTransfers) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan QR & manage transfers") } }
        stats?.let { counts ->
            val metrics = listOf(Triple("Assigned stations", counts.stationCount.toString(), Icons.Default.LocationOn), Triple("Upcoming slots", counts.activeSlots.toString(), Icons.Default.CalendarMonth),
                Triple("Pending bookings", counts.pending.toString(), Icons.Default.Schedule), Triple("Current bookings", counts.current.toString(), Icons.Default.EventAvailable),
                Triple("In progress", counts.activeTransfers.toString(), Icons.Default.Sync), Triple("Completed", counts.completedTransfers.toString(), Icons.Default.CheckCircle),
                Triple("Available energy", String.format(Locale.US, "%.1f kWh", counts.availableEnergyKwh), Icons.Default.Bolt), Triple("Delivered energy", String.format(Locale.US, "%.1f kWh", counts.deliveredEnergyKwh), Icons.Default.BatteryChargingFull))
            items(metrics.chunked(2)) { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { (label, value, icon) -> MobileMetric(label, value, icon, Modifier.weight(1f)) } } }
        }
        item { Text("Recent deliveries", style = MaterialTheme.typography.titleMedium) }
        if (!busy && completed.isEmpty()) item { Text("Completed transfers will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(completed, key = { it.id }) { transfer ->
            OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = onTransfers, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stations.find { it.id == transfer.stationId }?.stationName ?: "Station unavailable", fontWeight = FontWeight.SemiBold)
                        Text(transfer.prosumerNIC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(String.format(Locale.US, "%.1f kWh", transfer.transferredEnergyKWh), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
