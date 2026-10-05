package com.kusal.solargridxmobile.ui.station

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.location.Location
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kusal.solargridxmobile.data.local.ReservationDbHelper
import com.kusal.solargridxmobile.data.local.SessionManager
import com.kusal.solargridxmobile.data.location.LocationHelper
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation
import com.kusal.solargridxmobile.data.repository.ReservationRepository
import com.kusal.solargridxmobile.data.repository.StationRepository
import com.kusal.solargridxmobile.ui.reservation.ReservationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.*

private const val TAG = "SolarGridX-Map"

private const val MARKER_IMAGE_ID      = "station-pin"
private const val USER_IMAGE_ID        = "user-location-dot"
private const val STATION_SOURCE       = "stations-source"
private const val USER_SOURCE          = "user-source"
private const val RADIUS_SOURCE        = "radius-source"
private const val RADIUS_FILL_LAYER    = "radius-fill-layer"
private const val RADIUS_LINE_LAYER    = "radius-line-layer"
private const val STATION_ICON_LAYER   = "stations-icon-layer"
private const val STATION_LABEL_LAYER  = "stations-label-layer"
private const val USER_LAYER           = "user-layer"
private const val OSM_STYLE            = "https://tiles.openfreemap.org/styles/liberty"
private const val NEARBY_RADIUS_KM     = 30.0
private const val RADIUS_ZOOM          = 10.2
private const val DEFAULT_FALLBACK_LAT  = 7.8731
private const val DEFAULT_FALLBACK_LNG  = 80.7718

/** Haversine distance in kilometres between two lat/lng points. */
private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Format distance nicely: "<1 km", "3.2 km", "15 km" */
private fun formatDist(km: Double): String = when {
    km < 1.0 -> "<1 km"
    km < 10.0 -> "%.1f km".format(km)
    else -> "${km.toInt()} km"
}

/** Check if coordinates are valid numbers and physically valid geographic positions. */
private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
    if (lat.isNaN() || lng.isNaN()) return false
    if (lat == 0.0 && lng == 0.0) return false
    if (lat < -90.0 || lat > 90.0) return false
    if (lng < -180.0 || lng > 180.0) return false
    return true
}

/** Creates a 64-point geodesic circular polygon representing the radius around a center coordinate. */
private fun createCirclePolygon(centerLat: Double, centerLng: Double, radiusKm: Double, pointsCount: Int = 64): Polygon {
    val coordinates = mutableListOf<Point>()
    val r = 6371.0
    val dByR = radiusKm / r
    val latRad = Math.toRadians(centerLat)
    val lngRad = Math.toRadians(centerLng)
    for (i in 0..pointsCount) {
        val bearing = 2.0 * Math.PI * i / pointsCount
        val pLat = asin(sin(latRad) * cos(dByR) + cos(latRad) * sin(dByR) * cos(bearing))
        val pLng = lngRad + atan2(sin(bearing) * sin(dByR) * cos(latRad), cos(dByR) - sin(latRad) * sin(pLat))
        coordinates.add(Point.fromLngLat(Math.toDegrees(pLng), Math.toDegrees(pLat)))
    }
    return Polygon.fromLngLats(listOf(coordinates))
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StationMapScreen(
    reservationViewModel: ReservationViewModel? = null
) {
    val context = LocalContext.current
    val repository = remember { StationRepository() }
    val sessionManager = remember { SessionManager(context) }
    val reservationRepository = remember { ReservationRepository(ReservationDbHelper(context)) }
    val coroutineScope = rememberCoroutineScope()
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val userNic = remember { sessionManager.getUserNic() ?: "" }

    // ── State ──────────────────────────────────────────────────────────────
    var stations            by remember { mutableStateOf<List<SolarStation>>(emptyList()) }
    var userLocation        by remember { mutableStateOf<Location?>(null) }
    var isLoading           by remember { mutableStateOf(true) }
    var permissionDenied    by remember { mutableStateOf(false) }
    var errorMessage        by remember { mutableStateOf<String?>(null) }
    var selectedStation     by remember { mutableStateOf<SolarStation?>(null) }
    var selectedStationSlots by remember { mutableStateOf<List<EnergySlot>>(emptyList()) }
    var isSlotsLoading      by remember { mutableStateOf(false) }
    var showPopupBox        by remember { mutableStateOf(false) }
    var showAllStations     by remember { mutableStateOf(false) }

    // State references for MapLibre callbacks
    val currentStations = rememberUpdatedState(stations)
    val currentUserLoc  = rememberUpdatedState(userLocation)
    val currentFilterAll = rememberUpdatedState(showAllStations)

    // ── Map references ────────────────────────────────────────────────────
    var mapView  by remember { mutableStateOf<MapView?>(null) }
    var mapStyle by remember { mutableStateOf<org.maplibre.android.maps.Style?>(null) }

    // ── Permission launcher ───────────────────────────────────────────────
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            permissionDenied = false
            fetchLocation(fusedClient) { loc ->
                userLocation = loc
                if (loc != null) {
                    Log.d(TAG, "Current user latitude and longitude: lat=${loc.latitude}, lng=${loc.longitude}")
                } else {
                    Log.w(TAG, "Location services returned null location.")
                }
            }
        } else {
            permissionDenied = true
            Log.w(TAG, "Location permission denied by user.")
        }
    }

    // ── Load stations from StationRepository ──────────────────────────────
    LaunchedEffect(Unit) {
        isLoading = true
        repository.getAllStations().fold(
            onSuccess = { list ->
                Log.d(TAG, "Total stations received from the API: ${list.size}")
                stations = list
                isLoading = false
            },
            onFailure = { e ->
                Log.e(TAG, "Error fetching stations from API: ${e.message}")
                errorMessage = e.message ?: "Failed to load stations"
                isLoading = false
            }
        )

        // Request location permissions (fine & coarse)
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            permissionDenied = false
            fetchLocation(fusedClient) { loc ->
                userLocation = loc
                if (loc != null) {
                    Log.d(TAG, "Current user latitude and longitude: lat=${loc.latitude}, lng=${loc.longitude}")
                }
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // ── Update markers and 30 km circle whenever stations, userLocation, filter, or style change ─
    LaunchedEffect(stations, userLocation, showAllStations, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val loc = userLocation?.takeIf { LocationHelper.isInSriLanka(it.latitude, it.longitude) }

        if (loc != null) {
            // Update 30 km search radius boundary
            updateRadiusBoundary(style, loc, NEARBY_RADIUS_KM)
            // Update user distinct location dot
            updateUserMarker(style, loc)
        }

        // Filter and update station markers (showAll if userLoc is outside Sri Lanka or not ready)
        updateStationMarkers(style, stations, loc, NEARBY_RADIUS_KM, showAllStations || loc == null)
    }

    // Auto-center on user location when first retrieved (only if within Sri Lanka)
    LaunchedEffect(userLocation) {
        val loc = userLocation?.takeIf { LocationHelper.isInSriLanka(it.latitude, it.longitude) } ?: return@LaunchedEffect
        mapView?.getMapAsync { map ->
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), RADIUS_ZOOM),
                1000
            )
        }
    }

    // ── Load slots when a station is clicked (Show ONLY valid, unexpired slots) ──
    LaunchedEffect(selectedStation) {
        val st = selectedStation ?: return@LaunchedEffect
        isSlotsLoading = true
        repository.getSlotsByStationId(st.id).fold(
            onSuccess = { slots ->
                selectedStationSlots = slots.filter { slot ->
                    slot.isActive && !isSlotExpired(slot) && !isSlotBeyond7Days(slot)
                }.sortedWith(compareBy({ it.slotDate }, { it.startTime }))
            },
            onFailure = { e ->
                Log.e(TAG, "Failed to load slots for station ${st.id}: ${e.message}")
                selectedStationSlots = emptyList()
            }
        )
        isSlotsLoading = false
    }

    // ── Stations within 30 km calculation for UI ──────────────────────────
    val nearbyStations: List<Pair<SolarStation, Double>> = remember(stations, userLocation) {
        val loc = userLocation?.takeIf { LocationHelper.isInSriLanka(it.latitude, it.longitude) } ?: return@remember emptyList()
        val valid = stations.filter { isValidCoordinate(it.latitude, it.longitude) }
        valid.mapNotNull { st ->
            val dist = distanceKm(loc.latitude, loc.longitude, st.latitude, st.longitude)
            if (dist <= NEARBY_RADIUS_KM) st to dist else null
        }.sortedBy { it.second }
    }

    // ──────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        // ── MapLibre View ─────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                val mv = MapView(ctx)
                mapView = mv
                mv.onCreate(null)
                mv.onStart()
                mv.onResume()

                mv.getMapAsync { map ->
                    map.uiSettings.apply {
                        isAttributionEnabled = true
                        isLogoEnabled = false
                        attributionGravity = Gravity.BOTTOM or Gravity.START
                    }

                    // Default center until user location is determined
                    val initialCenter = currentUserLoc.value?.let {
                        LatLng(it.latitude, it.longitude)
                    } ?: LatLng(DEFAULT_FALLBACK_LAT, DEFAULT_FALLBACK_LNG)

                    map.cameraPosition = CameraPosition.Builder()
                        .target(initialCenter)
                        .zoom(if (currentUserLoc.value != null) RADIUS_ZOOM else 7.0)
                        .build()

                    map.setStyle(org.maplibre.android.maps.Style.Builder().fromUri(OSM_STYLE)) { style ->
                        // 1. Register marker & user bitmaps
                        val pinBitmap = createStationPinBitmap(android.graphics.Color.parseColor("#15803D"))
                        style.addImage(MARKER_IMAGE_ID, pinBitmap)
                        style.addImage(USER_IMAGE_ID, createUserDotBitmap())

                        // 2. Add 30 km Radius Source & Layers (Fill + Dashed boundary line)
                        style.addSource(GeoJsonSource(RADIUS_SOURCE, FeatureCollection.fromFeatures(emptyList())))
                        style.addLayer(
                            FillLayer(RADIUS_FILL_LAYER, RADIUS_SOURCE).withProperties(
                                PropertyFactory.fillColor("#15803D"),
                                PropertyFactory.fillOpacity(0.08f)
                            )
                        )
                        style.addLayer(
                            LineLayer(RADIUS_LINE_LAYER, RADIUS_SOURCE).withProperties(
                                PropertyFactory.lineColor("#15803D"),
                                PropertyFactory.lineWidth(2.5f),
                                PropertyFactory.lineDasharray(arrayOf(3f, 2f))
                            )
                        )

                        // 3. Add Stations Source
                        style.addSource(GeoJsonSource(STATION_SOURCE, FeatureCollection.fromFeatures(emptyList())))

                        // 4. STATION ICON LAYER: Dedicated purely to drawing the marker icon.
                        // Has ZERO text/font dependencies so pins render unconditionally and immediately!
                        style.addLayer(
                            SymbolLayer(STATION_ICON_LAYER, STATION_SOURCE).withProperties(
                                PropertyFactory.iconImage(MARKER_IMAGE_ID),
                                PropertyFactory.iconSize(1.0f),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                                PropertyFactory.iconAnchor("bottom")
                            )
                        )

                        // 5. STATION LABEL LAYER: Uses "Noto Sans Regular" (the actual font stack served by OpenFreeMap)
                        style.addLayer(
                            SymbolLayer(STATION_LABEL_LAYER, STATION_SOURCE).withProperties(
                                PropertyFactory.textField("{label}"),
                                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                                PropertyFactory.textSize(11f),
                                PropertyFactory.textOffset(arrayOf(0f, 0.7f)),
                                PropertyFactory.textAnchor("top"),
                                PropertyFactory.textAllowOverlap(true),
                                PropertyFactory.textIgnorePlacement(true),
                                PropertyFactory.textColor("#0F172A"),
                                PropertyFactory.textHaloColor("#FFFFFF"),
                                PropertyFactory.textHaloWidth(2.5f)
                            )
                        )

                        // 6. Add User Location Source & SymbolLayer
                        style.addSource(GeoJsonSource(USER_SOURCE, FeatureCollection.fromFeatures(emptyList())))
                        style.addLayer(
                            SymbolLayer(USER_LAYER, USER_SOURCE).withProperties(
                                PropertyFactory.iconImage(USER_IMAGE_ID),
                                PropertyFactory.iconSize(1.0f),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true)
                            )
                        )

                        // 7. Expose style to trigger LaunchedEffect
                        mapStyle = style

                        // 8. If data is already ready, populate layers immediately
                        currentUserLoc.value?.let { loc ->
                            updateRadiusBoundary(style, loc, NEARBY_RADIUS_KM)
                            updateUserMarker(style, loc)
                        }
                        updateStationMarkers(
                            style,
                            currentStations.value,
                            currentUserLoc.value,
                            NEARBY_RADIUS_KM,
                            currentFilterAll.value
                        )

                        // 9. Station tap listener (48px hit target around pin or label)
                        map.addOnMapClickListener { latLng ->
                            val screenPt = map.projection.toScreenLocation(latLng)
                            val touchRadius = 48f
                            val touchBox = RectF(
                                screenPt.x - touchRadius,
                                screenPt.y - touchRadius,
                                screenPt.x + touchRadius,
                                screenPt.y + touchRadius
                            )
                            val hits = map.queryRenderedFeatures(touchBox, STATION_ICON_LAYER, STATION_LABEL_LAYER)
                            if (hits.isNotEmpty()) {
                                val stationId = hits[0].getStringProperty("stationId")
                                val clicked = currentStations.value.find { it.id == stationId }
                                if (clicked != null) {
                                    selectedStation = clicked
                                    showPopupBox = true
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLng(LatLng(clicked.latitude, clicked.longitude)),
                                        400
                                    )
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
            update = { /* markers updated via LaunchedEffect */ }
        )

        // ── Top Label Banner: Stations within 30 km ────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Text(
                            text = if (showAllStations) "All Solar Stations" else "Stations within 30 km",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )

                        // Toggle between "30 km Radius" and "All Stations"
                        if (stations.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (showAllStations) Color(0xFF1D4ED8) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { showAllStations = !showAllStations }
                            ) {
                                Text(
                                    text = if (showAllStations) "Viewing All" else "Show All (${stations.count { it.isActive }})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showAllStations) Color.White else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    when {
                        permissionDenied -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Location permission denied",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        isLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color(0xFF15803D))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Loading stations\u2026", fontSize = 13.sp, color = Color(0xFF0F172A))
                            }
                        }
                        userLocation == null && !showAllStations -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color(0xFF15803D))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Determining location\u2026", fontSize = 13.sp, color = Color(0xFF0F172A))
                            }
                        }
                        !showAllStations && nearbyStations.isEmpty() -> {
                            Text(
                                text = "No solar stations found within 30 km",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                        showAllStations -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1D4ED8))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${stations.count { isValidCoordinate(it.latitude, it.longitude) }} stations plotted across grid",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8)
                                )
                            }
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF15803D))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${nearbyStations.size} stations nearby",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }
            }

            // Error message banner if API failed
            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEE2E8),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(msg, fontSize = 11.sp, color = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = { errorMessage = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // ── Map Controls: Zoom In / Zoom Out / "Re-center on me" FAB ──────
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Zoom controls
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column {
                    IconButton(
                        onClick = {
                            mapView?.getMapAsync { map ->
                                map.animateCamera(CameraUpdateFactory.zoomIn(), 250)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color(0xFF0F172A))
                    }
                    HorizontalDivider(modifier = Modifier.width(40.dp), color = Color(0xFFF1F5F9))
                    IconButton(
                        onClick = {
                            mapView?.getMapAsync { map ->
                                map.animateCamera(CameraUpdateFactory.zoomOut(), 250)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color(0xFF0F172A))
                    }
                }
            }

            // Recenter on user location & 30 km radius
            userLocation?.let { loc ->
                FloatingActionButton(
                    onClick = {
                        mapView?.getMapAsync { map ->
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), RADIUS_ZOOM),
                                800
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = Color.White,
                    contentColor = Color(0xFF15803D),
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Center on 30km radius")
                }
            }
        }

        // ── Station Details POPUP BOX Dialog ──────────────────────────────
        if (showPopupBox && selectedStation != null) {
            val station = selectedStation!!
            val distToStation = userLocation?.takeIf { LocationHelper.isInSriLanka(it.latitude, it.longitude) }?.let { loc ->
                val d = distanceKm(loc.latitude, loc.longitude, station.latitude, station.longitude)
                if (d <= 500.0) d else null
            }

            Dialog(
                onDismissRequest = {
                    showPopupBox = false
                    selectedStation = null
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StationDetailPopup(
                        station = station,
                        distanceText = distToStation?.let { formatDist(it) },
                        slots = selectedStationSlots,
                        isSlotsLoading = isSlotsLoading,
                        userNic = userNic,
                        onDismiss = {
                            showPopupBox = false
                            selectedStation = null
                        },
                        onReserveSlot = { slot, requestedKwh, onResult ->
                            coroutineScope.launch {
                                val result = reservationRepository.createReservation(
                                    nic = userNic,
                                    slotId = slot.id,
                                    requestedKwh = requestedKwh
                                )
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            "Reservation confirmed for %.1f kWh!".format(requestedKwh),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        // Immediately reload slots for this station so available capacity updates
                                        repository.getSlotsByStationId(station.id).onSuccess { updatedSlots ->
                                            selectedStationSlots = updatedSlots.filter {
                                                it.isActive && !isSlotExpired(it) && !isSlotBeyond7Days(it)
                                            }.sortedWith(compareBy({ it.slotDate }, { it.startTime }))
                                        }
                                        reservationViewModel?.loadAllData()
                                        onResult(true, null)
                                    },
                                    onFailure = { err ->
                                        onResult(false, err.message ?: "Failed to book reservation")
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    // ── MapView lifecycle cleanup ────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
        }
    }
}

// ── Helper: fetch device location ────────────────────────────────────────────
@SuppressLint("MissingPermission")
private fun fetchLocation(
    client: com.google.android.gms.location.FusedLocationProviderClient,
    onResult: (Location?) -> Unit
) {
    try {
        val cts = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null && LocationHelper.isInSriLanka(loc.latitude, loc.longitude)) {
                    onResult(loc)
                } else {
                    client.lastLocation
                        .addOnSuccessListener { lastLoc ->
                            if (lastLoc != null && LocationHelper.isInSriLanka(lastLoc.latitude, lastLoc.longitude)) {
                                onResult(lastLoc)
                            } else {
                                onResult(null)
                            }
                        }
                        .addOnFailureListener { onResult(null) }
                }
            }
            .addOnFailureListener {
                client.lastLocation
                    .addOnSuccessListener { lastLoc ->
                        if (lastLoc != null && LocationHelper.isInSriLanka(lastLoc.latitude, lastLoc.longitude)) {
                            onResult(lastLoc)
                        } else {
                            onResult(null)
                        }
                    }
                    .addOnFailureListener { onResult(null) }
            }
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching location: ${e.message}")
        onResult(null)
    }
}

// ── Helper: update 30 km radius boundary on map ──────────────────────────────
private fun updateRadiusBoundary(style: org.maplibre.android.maps.Style, loc: Location, radiusKm: Double) {
    val polygon = createCirclePolygon(loc.latitude, loc.longitude, radiusKm)
    val feature = Feature.fromGeometry(polygon)
    (style.getSource(RADIUS_SOURCE) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
}

// ── Helper: update station GeoJSON source ────────────────────────────────────
private fun updateStationMarkers(
    style: org.maplibre.android.maps.Style,
    stations: List<SolarStation>,
    userLoc: Location?,
    radiusKm: Double,
    showAll: Boolean = false
) {
    // 1. Exclude stations with invalid or missing coordinates
    val validStations = stations.filter { isValidCoordinate(it.latitude, it.longitude) }
    val invalidCount = stations.size - validStations.size
    Log.d(TAG, "Number of stations excluded because of invalid coordinates: $invalidCount")

    // 2. Filter stations within radiusKm if showAll is false and user location is available
    val stationsToDisplay: List<Pair<SolarStation, Double?>> = validStations.mapNotNull { st ->
        val dist = userLoc?.takeIf { LocationHelper.isInSriLanka(it.latitude, it.longitude) }?.let {
            val d = distanceKm(it.latitude, it.longitude, st.latitude, st.longitude)
            if (d <= 500.0) d else null
        }
        if (dist != null) {
            Log.d(TAG, "Calculated distance for station '${st.stationName}' (${st.id}): %.2f km (lat=%.6f, lng=%.6f)".format(dist, st.latitude, st.longitude))
        }

        if (showAll || userLoc == null || !LocationHelper.isInSriLanka(userLoc.latitude, userLoc.longitude)) {
            st to dist
        } else if (dist != null && dist <= radiusKm) {
            st to dist
        } else {
            null
        }
    }.sortedWith(compareBy(nullsLast()) { it.second })

    val insideCount = stationsToDisplay.count { (it.second ?: Double.MAX_VALUE) <= radiusKm }
    Log.d(TAG, "Number of stations inside the ${radiusKm.toInt()} km radius: $insideCount")

    // 3. Convert stations to GeoJSON features
    val features = stationsToDisplay.map { (station, dist) ->
        val distText = if (dist != null) " (${formatDist(dist)})" else ""
        val label = "${station.stationName}$distText"
        Feature.fromGeometry(Point.fromLngLat(station.longitude, station.latitude)).also { f ->
            f.addStringProperty("stationId", station.id)
            f.addStringProperty("label", label)
        }
    }

    val source = style.getSource(STATION_SOURCE) as? GeoJsonSource
    source?.setGeoJson(FeatureCollection.fromFeatures(features))
}

// ── Helper: update user location dot ─────────────────────────────────────────
private fun updateUserMarker(style: org.maplibre.android.maps.Style, loc: Location) {
    val feature = Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude))
    (style.getSource(USER_SOURCE) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
}

// ── Helpers for Station Details, Slot Expiry, and Navigation ────────────────
private fun isSlotExpired(slot: EnergySlot): Boolean {
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

private fun isSlotBeyond7Days(slot: EnergySlot): Boolean {
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

private fun openMapsDirections(context: Context, lat: Double, lng: Double, label: String) {
    try {
        val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        context.startActivity(mapIntent)
    } catch (e: Exception) {
        try {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        } catch (err: Exception) {
            Toast.makeText(context, "Could not open map navigation", Toast.LENGTH_SHORT).show()
        }
    }
}

// ── Station Detail POPUP BOX Dialog ──────────────────────────────────────────
@Composable
private fun StationDetailPopup(
    station: SolarStation,
    distanceText: String?,
    slots: List<EnergySlot>,
    isSlotsLoading: Boolean,
    userNic: String,
    onDismiss: () -> Unit,
    onReserveSlot: (EnergySlot, Double, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var slotToReserve by remember { mutableStateOf<EnergySlot?>(null) }

    val isOpenNow = remember(station.operatingStartTime, station.operatingEndTime) {
        checkIsStationOpen(station.operatingStartTime, station.operatingEndTime)
    }
    val totalAvailableEnergy = remember(slots) { slots.sumOf { it.availableEnergyKwh } }
    val openSlots = remember(slots) { slots.filter { it.availableEnergyKwh > 0.0 } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Gradient Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF15803D), Color(0xFF16A34A), Color(0xFF0D9488))
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bolt icon badge (Static icon)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & address
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = station.stationName,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.22f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ID: ${station.id.takeLast(6).uppercase()}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = station.location.ifBlank { "Sri Lanka Solar Grid Hub" },
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Badges row: Status, Operating Hours, Distance
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Operational Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (station.isActive) Color(0xFF15803D) else Color(0xFFDC2626))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (station.isActive) "Active Node" else "Inactive",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (station.isActive) Color(0xFF15803D) else Color(0xFFDC2626)
                                )
                            }
                        }

                        // Open Now / Closed Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isOpenNow) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (isOpenNow) Color(0xFF15803D) else Color(0xFFDC2626),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isOpenNow) "Open Now" else "Closed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOpenNow) Color(0xFF15803D) else Color(0xFFDC2626)
                                )
                            }
                        }

                        // Distance Badge
                        distanceText?.let { dist ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.NearMe,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$dist away",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Scrollable Body Content ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // ── Navigation Quick Action Bar (Direction Button + Copy GPS) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Turn-by-turn Directions Button
                    Button(
                        onClick = {
                            openMapsDirections(context, station.latitude, station.longitude, station.stationName)
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                    ) {
                        Icon(
                            Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Get Directions",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Copy GPS Coordinates Button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("GPS", "${station.latitude}, ${station.longitude}")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "GPS coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy GPS",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Detailed Stats 4-Card Grid ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Bolt,
                        title = "Capacity",
                        value = "${station.totalCapacityKwh.toInt()} kWh",
                        subtitle = "Total Grid",
                        tintColor = Color(0xFF15803D),
                        bgColor = Color(0xFFDCFCE7)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.BatteryChargingFull,
                        title = "Avail. Energy",
                        value = "${totalAvailableEnergy.toInt()} kWh",
                        subtitle = "${openSlots.size} open slots",
                        tintColor = Color(0xFF1D4ED8),
                        bgColor = Color(0xFFDBEAFE)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Schedule,
                        title = "Valid Slots",
                        value = "${slots.size}",
                        subtitle = "Unexpired",
                        tintColor = Color(0xFF7C3AED),
                        bgColor = Color(0xFFF3E8FF)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Verified,
                        title = "Grid Sync",
                        value = if (station.isActive) "99.8%" else "Offline",
                        subtitle = "CEB Linked",
                        tintColor = Color(0xFFB45309),
                        bgColor = Color(0xFFFEF3C7)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Station Operational Schedule & Coordinates Info ──
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Operating Hours: ${formatOperationalTime(station.operatingStartTime)} – ${formatOperationalTime(station.operatingEndTime)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GPS: %.5f, %.5f".format(station.latitude, station.longitude),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Available Battery Slots Section (Only Valid Unexpired Slots) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Available Battery Slots",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            "Showing active, unexpired slots only",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDCFCE7))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "${slots.size} valid",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when {
                    isSlotsLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Loading valid unexpired slots\u2026",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                    slots.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No upcoming valid battery slots",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                                Text(
                                    "All past slots are expired or none are currently scheduled for this station.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            slots.forEach { slot ->
                                PopupSlotCard(
                                    slot = slot,
                                    onReserveClick = { slotToReserve = slot }
                                )
                            }
                        }
                    }
                }
            }

            // ── Bottom Action Button Row ──
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Text("Close", color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        slotToReserve = openSlots.firstOrNull() ?: slots.firstOrNull()
                    },
                    enabled = openSlots.isNotEmpty(),
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF15803D),
                        disabledContainerColor = Color(0xFFE2E8F0)
                    )
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (openSlots.isNotEmpty()) "Reserve Slot" else "No Open Slots",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ── Quick Reservation Dialog from Popup ──
    if (slotToReserve != null) {
        SlotReservationDialog(
            slot = slotToReserve!!,
            station = station,
            userNic = userNic,
            onDismiss = { slotToReserve = null },
            onConfirm = { requestedKwh, onResult ->
                onReserveSlot(slotToReserve!!, requestedKwh) { success, err ->
                    onResult(success, err)
                    if (success) {
                        slotToReserve = null
                    }
                }
            }
        )
    }
}

// ── Metric Card ───────────────────────────────────────────────────────────────
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    tintColor: Color,
    bgColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), maxLines = 1)
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569), maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, fontSize = 9.sp, color = Color(0xFF94A3B8), maxLines = 1)
            }
        }
    }
}

// ── Slot Card with Reservation Action ─────────────────────────────────────────
@Composable
private fun PopupSlotCard(
    slot: EnergySlot,
    onReserveClick: () -> Unit
) {
    val isAvailable = slot.availableEnergyKwh > 0.0
    val progress = if (slot.energyCapacityKwh > 0) {
        (slot.availableEnergyKwh / slot.energyCapacityKwh).toFloat().coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
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
                            fontSize = 13.sp,
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
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAvailable) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = if (isAvailable) "OPEN" else "FULL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAvailable) Color(0xFF15803D) else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Energy Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Capacity Available",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "%.1f / %.1f kWh".format(slot.availableEnergyKwh, slot.energyCapacityKwh),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAvailable) Color(0xFF15803D) else Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp)),
                color = if (progress > 0.2f) Color(0xFF15803D) else Color(0xFFEAB308),
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onReserveClick,
                enabled = isAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF15803D),
                    disabledContainerColor = Color(0xFFE2E8F0)
                ),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isAvailable) "Reserve Energy Slot" else "Slot Fully Booked",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Slot Reservation Dialog from Popup ────────────────────────────────────────
@Composable
private fun SlotReservationDialog(
    slot: EnergySlot,
    station: SolarStation,
    userNic: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, (Boolean, String?) -> Unit) -> Unit
) {
    var kwhText by remember { mutableStateOf(minOf(10.0, slot.availableEnergyKwh).toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Reserve Energy Slot",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Station & Slot Summary Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = station.stationName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        if (station.location.isNotBlank()) {
                            Text(
                                text = station.location,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Slot Date:", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(slot.slotDate.split("T")[0], fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Time Window:", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(
                                "${formatOperationalTime(slot.startTime)} – ${formatOperationalTime(slot.endTime)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Available Capacity:", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(
                                "%.1f kWh".format(slot.availableEnergyKwh),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                        if (userNic.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Prosumer NIC:", fontSize = 11.sp, color = Color(0xFF64748B))
                                Text(userNic, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D4ED8))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "Requested Energy (kWh)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = kwhText,
                    onValueChange = {
                        kwhText = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    placeholder = { Text("e.g. 10.0") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF15803D),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )

                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(msg, fontSize = 11.sp, color = Color(0xFFDC2626))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }

                    Button(
                        onClick = {
                            val kwh = kwhText.toDoubleOrNull()
                            if (kwh == null || kwh <= 0.0) {
                                errorMessage = "Please enter a valid amount greater than 0"
                                return@Button
                            }
                            if (kwh > slot.availableEnergyKwh) {
                                errorMessage = "Cannot exceed available capacity (%.1f kWh)".format(slot.availableEnergyKwh)
                                return@Button
                            }
                            if (userNic.isBlank()) {
                                errorMessage = "Please sign in with a valid prosumer NIC"
                                return@Button
                            }

                            isSubmitting = true
                            errorMessage = null
                            onConfirm(kwh) { success, err ->
                                isSubmitting = false
                                if (!success) {
                                    errorMessage = err ?: "Failed to book reservation"
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Confirm Booking", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── Bitmap Generators ─────────────────────────────────────────────────────────

/**
 * Creates a high-visibility, crisp location pin with:
 * 1. Base drop shadow on the map
 * 2. Solid teardrop silhouette (circle + pointer) with white outline
 * 3. SolarGridX Emerald green fill
 * 4. Center white disc badge
 * 5. Distinctive green energy bolt inside
 */
private fun createStationPinBitmap(pinColor: Int): Bitmap {
    val density = 3f
    val w = (36 * density).toInt()  // 108 px
    val h = (48 * density).toInt()  // 144 px
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val cx = w / 2f
    val r = w * 0.38f // ~41 px radius
    val cy = r + 4f
    val tipY = h - 6f

    // 1. Soft Ground Shadow at base
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(80, 15, 23, 42)
        style = Paint.Style.FILL
    }
    canvas.drawOval(RectF(cx - 24f, h - 14f, cx + 24f, h - 2f), shadowPaint)

    // 2. White outer border for contrast
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // 3. Green fill paint
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }

    // 4. Pointer path from circular head down to the needle tip
    val pointerPath = android.graphics.Path().apply {
        moveTo(cx - r * 0.72f, cy + r * 0.45f)
        lineTo(cx + r * 0.72f, cy + r * 0.45f)
        lineTo(cx, tipY)
        close()
    }

    // Draw pointer + circular head
    canvas.drawPath(pointerPath, strokePaint)
    canvas.drawPath(pointerPath, fillPaint)

    canvas.drawCircle(cx, cy, r, strokePaint)
    canvas.drawCircle(cx, cy, r, fillPaint)

    // 5. Inner White Disc Badge
    val discRadius = r * 0.55f
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, discRadius, whitePaint)

    // 6. Electric Bolt Symbol inside the disc
    val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }
    val boltPath = android.graphics.Path().apply {
        val s = discRadius * 0.8f
        moveTo(cx + 0.1f * s, cy - 1.0f * s)
        lineTo(cx - 0.7f * s, cy + 0.05f * s)
        lineTo(cx - 0.05f * s, cy + 0.05f * s)
        lineTo(cx - 0.2f * s, cy + 1.1f * s)
        lineTo(cx + 0.7f * s, cy - 0.05f * s)
        lineTo(cx + 0.05f * s, cy - 0.05f * s)
        close()
    }
    canvas.drawPath(boltPath, boltPaint)

    return bmp
}

/**
 * Creates a GPS user location dot with an outer blue pulse ring.
 */
private fun createUserDotBitmap(): Bitmap {
    val density = 3f
    val size = (32 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f

    // Outer pulse ring (semi-transparent blue)
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(60, 37, 99, 235)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, size / 2f - 2f, ringPaint)

    // White ring
    val whiteRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, (size / 2f) * 0.65f, whiteRing)

    // Deep blue center dot
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#2563EB")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, (size / 2f) * 0.45f, dotPaint)

    return bmp
}
