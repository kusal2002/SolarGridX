package com.kusal.solargridxmobile.ui.station

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation
import com.kusal.solargridxmobile.data.repository.StationRepository
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val MARKER_IMAGE_ID = "station-pin"
private const val SOURCE_ID = "stations-source"
private const val LAYER_ID = "stations-layer"
private const val OSM_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DEFAULT_LAT = 7.8731
private const val DEFAULT_LNG = 80.7718
private const val DEFAULT_ZOOM = 7.0

@Composable
fun StationMapScreen() {
    val context = LocalContext.current
    val repository = remember { StationRepository() }

    var stations by remember { mutableStateOf<List<SolarStation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedStation by remember { mutableStateOf<SolarStation?>(null) }
    var selectedStationSlots by remember { mutableStateOf<List<EnergySlot>>(emptyList()) }
    var isSlotsLoading by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    var mapView by remember { mutableStateOf<MapView?>(null) }

    // Load all stations once
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        repository.getAllStations().fold(
            onSuccess = { list ->
                stations = list
                isLoading = false
            },
            onFailure = { e ->
                errorMessage = e.message ?: "Failed to load stations"
                isLoading = false
            }
        )
    }

    // Load slots when station is selected
    LaunchedEffect(selectedStation) {
        val st = selectedStation ?: return@LaunchedEffect
        isSlotsLoading = true
        repository.getSlotsByStationId(st.id).fold(
            onSuccess = { slots -> selectedStationSlots = slots.filter { it.isActive } },
            onFailure = { selectedStationSlots = emptyList() }
        )
        isSlotsLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // MapLibre MapView via AndroidView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                val mv = MapView(ctx)
                mapView = mv
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    map.uiSettings.apply {
                        isAttributionEnabled = true
                        isLogoEnabled = false
                        attributionGravity = Gravity.BOTTOM or Gravity.END
                    }
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(DEFAULT_LAT, DEFAULT_LNG))
                        .zoom(DEFAULT_ZOOM)
                        .build()

                    map.setStyle(org.maplibre.android.maps.Style.Builder().fromUri(OSM_STYLE)) { style ->
                        // Add pin image
                        style.addImage(MARKER_IMAGE_ID, createPinBitmap())

                        // Build GeoJSON features for all active stations with GPS
                        val features = stations
                            .filter { it.isActive && it.latitude != 0.0 && it.longitude != 0.0 }
                            .map { station ->
                                Feature.fromGeometry(
                                    Point.fromLngLat(station.longitude, station.latitude)
                                ).also { feature ->
                                    feature.addStringProperty("stationId", station.id)
                                    feature.addStringProperty("stationName", station.stationName)
                                }
                            }

                        val geoJsonSource = GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(features))
                        style.addSource(geoJsonSource)

                        val symbolLayer = SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                            PropertyFactory.iconImage(MARKER_IMAGE_ID),
                            PropertyFactory.iconSize(1.2f),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconAnchor("bottom"),
                            PropertyFactory.textField("{stationName}"),
                            PropertyFactory.textSize(11f),
                            PropertyFactory.textOffset(arrayOf(0f, 0.5f)),
                            PropertyFactory.textAllowOverlap(false),
                            PropertyFactory.textOptional(true)
                        )
                        style.addLayer(symbolLayer)

                        // Tap on a marker
                        map.addOnMapClickListener { latLng ->
                            val screenPoint = map.projection.toScreenLocation(latLng)
                            val features2 = map.queryRenderedFeatures(screenPoint, LAYER_ID)
                            if (features2.isNotEmpty()) {
                                val stationId = features2[0].getStringProperty("stationId")
                                val clicked = stations.find { it.id == stationId }
                                if (clicked != null) {
                                    selectedStation = clicked
                                    showBottomSheet = true
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
                mv
            },
            update = {}
        )

        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth().padding(16.dp).align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.95f)).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF15803D), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Loading stations\u2026", fontSize = 13.sp, color = Color(0xFF0F172A))
                }
            }
        }

        // Error banner
        errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth().padding(16.dp).align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEE2E2).copy(alpha = 0.95f)).padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(msg, fontSize = 12.sp, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                    IconButton(onClick = { errorMessage = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFDC2626))
                    }
                }
            }
        }

        // Active station count chip
        if (!isLoading && errorMessage == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter).padding(top = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF15803D))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "${stations.count { it.isActive }} Active Stations",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        // Station details bottom sheet
        AnimatedVisibility(
            visible = showBottomSheet,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedStation?.let { station ->
                StationBottomSheet(
                    station = station,
                    slots = selectedStationSlots,
                    isSlotsLoading = isSlotsLoading,
                    onDismiss = { showBottomSheet = false }
                )
            }
        }
    }

    // MapView lifecycle
    DisposableEffect(Unit) {
        mapView?.onStart()
        mapView?.onResume()
        onDispose {
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
        }
    }
}

// ---- Station Bottom Sheet ---------------------------------------------------

@Composable
private fun StationBottomSheet(
    station: SolarStation,
    slots: List<EnergySlot>,
    isSlotsLoading: Boolean,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Color.White,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Drag handle
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE2E8F0)))
            }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(station.stationName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    if (station.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(station.location, fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (station.isActive) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (station.isActive) "Active" else "Inactive",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (station.isActive) Color(0xFF15803D) else Color(0xFF64748B)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StationStatChip("Capacity", "${station.totalCapacityKwh.toInt()} kWh", Icons.Default.Bolt, Color(0xFF15803D))
                StationStatChip(
                    "Operating",
                    "${station.operatingStartTime?.take(5) ?: "--"} \u2013 ${station.operatingEndTime?.take(5) ?: "--"}",
                    Icons.Default.Schedule, Color(0xFF1D4ED8)
                )
                StationStatChip("Slots", "${slots.size} avail.", Icons.Default.Battery4Bar, Color(0xFF7C3AED))
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Text("Available Battery Slots", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

            when {
                isSlotsLoading -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF15803D), modifier = Modifier.size(28.dp))
                }
                slots.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No available slots at this station", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(slots) { slot -> SlotCard(slot = slot) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ---- Slot Card --------------------------------------------------------------

@Composable
private fun SlotCard(slot: EnergySlot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDCFCE7)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Battery4Bar, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${slot.startTime.take(5)} \u2013 ${slot.endTime.take(5)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Text(slot.slotDate.split("T")[0], fontSize = 11.sp, color = Color(0xFF64748B))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${slot.availableEnergyKwh.toInt()} kWh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                Text("of ${slot.energyCapacityKwh.toInt()} kWh", fontSize = 10.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

// ---- Stat Chip --------------------------------------------------------------

@Composable
private fun StationStatChip(label: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text(label, fontSize = 10.sp, color = Color(0xFF64748B))
    }
}

// ---- Marker Bitmap ----------------------------------------------------------

private fun createPinBitmap(): Bitmap {
    val w = 72; val h = 90
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#15803D"); style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }
    val cx = w / 2f; val cy = w / 2f; val r = (w / 2f) - 4f
    canvas.drawCircle(cx, cy, r, fill)
    canvas.drawCircle(cx, cy, r, stroke)
    val path = android.graphics.Path().apply {
        moveTo(cx - 10f, cy + r - 4f); lineTo(cx + 10f, cy + r - 4f); lineTo(cx, h - 4f); close()
    }
    canvas.drawPath(path, fill)
    val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
    canvas.drawCircle(cx, cy, r * 0.38f, inner)
    return bmp
}
