package com.garagedoor.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garagedoor.app.data.AppSettings
import com.garagedoor.app.data.LatLngPoint
import com.garagedoor.app.geofence.PolygonUtil
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private fun createMyLocationDotBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (24 * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val radius = center - density

    canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x664285F4
        style = Paint.Style.FILL
    })
    canvas.drawCircle(center, center, radius * 0.72f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4285F4.toInt()
        style = Paint.Style.FILL
    })
    canvas.drawCircle(center, center, radius * 0.72f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    })
    return bitmap
}

private fun isNearExistingCorner(
    tap: GeoPoint,
    corners: List<GeoPoint>,
    mapView: MapView,
    radiusPx: Float,
): Boolean {
    val projection = mapView.projection ?: return false
    val tapPixel = projection.toPixels(tap, null) ?: return false
    for (corner in corners) {
        val cornerPixel = projection.toPixels(corner, null) ?: continue
        val dx = tapPixel.x - cornerPixel.x
        val dy = tapPixel.y - cornerPixel.y
        if (dx * dx + dy * dy <= radiusPx * radiusPx) return true
    }
    return false
}

@Composable
fun GarageMapScreen(viewModel: GarageViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var boundaryEditing by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    var baseUrl by rememberSaveable {
        mutableStateOf(settings.apiBaseUrl.ifBlank { AppSettings.DEFAULT_API_URL })
    }
    var apiToken by rememberSaveable { mutableStateOf("") }

    val points = remember {
        mutableStateListOf<GeoPoint>().apply {
            settings.polygon.forEach { add(GeoPoint(it.lat, it.lng)) }
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshStatus() }

    val start = points.firstOrNull()
        ?: settings.geofenceCenterLat.takeIf { it != 0.0 }?.let { lat ->
            GeoPoint(lat, settings.geofenceCenterLng)
        }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            start?.let { controller.setCenter(it) }
        }
    }
    var centeredOnUser by rememberSaveable { mutableStateOf(false) }
    val myLocationOverlay = remember(mapView) {
        val blueDot = createMyLocationDotBitmap(context)
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            setPersonIcon(blueDot)
            setPersonAnchor(0.5f, 0.5f)
            setDirectionIcon(blueDot)
            setDirectionAnchor(0.5f, 0.5f)
            disableFollowLocation()
            runOnFirstFix {
                if (!centeredOnUser) {
                    myLocation?.let { mapView.controller.animateTo(it) }
                    centeredOnUser = true
                }
            }
        }
    }
    val polygonOverlay = remember { mutableStateOf<Polygon?>(null) }
    val cornerTouchRadiusPx = remember { DraggableCornerMarker.touchRadiusPx(context) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun centerMapOnUser(animate: Boolean = true) {
        if (!hasLocationPermission()) return
        val here = myLocationOverlay.myLocation
        if (here != null) {
            if (animate) mapView.controller.animateTo(here) else mapView.controller.setCenter(here)
            centeredOnUser = true
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.lastLocation.addOnSuccessListener { last ->
            if (last != null && !centeredOnUser) {
                val point = GeoPoint(last.latitude, last.longitude)
                mapView.controller.setCenter(point)
                centeredOnUser = true
            }
        }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val point = GeoPoint(location.latitude, location.longitude)
                    if (animate) mapView.controller.animateTo(point) else mapView.controller.setCenter(point)
                    centeredOnUser = true
                }
            }
    }

    LaunchedEffect(Unit) {
        centerMapOnUser(animate = false)
    }

    fun syncMyLocationOverlay() {
        if (!mapView.overlays.contains(myLocationOverlay)) {
            mapView.overlays.add(0, myLocationOverlay)
        }
        if (hasLocationPermission()) {
            myLocationOverlay.enableMyLocation()
        } else {
            myLocationOverlay.disableMyLocation()
        }
    }

    fun updatePolygonShape() {
        if (points.size >= 3) {
            val polygon = polygonOverlay.value ?: Polygon().apply {
                fillPaint.color = 0x331565C0
                outlinePaint.color = 0xFF1565C0.toInt()
                outlinePaint.strokeWidth = 4f
                infoWindow = null
            }.also { created ->
                mapView.overlays.add(created)
                polygonOverlay.value = created
            }
            polygon.points = points.toList()
        } else {
            polygonOverlay.value?.let { mapView.overlays.remove(it) }
            polygonOverlay.value = null
        }
        mapView.invalidate()
    }

    fun reloadPointsFromSettings() {
        points.clear()
        settings.polygon.forEach { points.add(GeoPoint(it.lat, it.lng)) }
    }

    fun exitBoundaryEditMode() {
        reloadPointsFromSettings()
        boundaryEditing = false
    }

    fun refreshOverlays() {
        mapView.overlays.removeIf { it is MapEventsOverlay || it is Polygon || it is DraggableCornerMarker }
        polygonOverlay.value = null
        syncMyLocationOverlay()
        if (boundaryEditing) {
            mapView.overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p == null) return false
                        if (isNearExistingCorner(p, points, mapView, cornerTouchRadiusPx)) return true
                        points.add(GeoPoint(p.latitude, p.longitude))
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }),
            )
        }
        updatePolygonShape()
        if (boundaryEditing) {
            points.forEachIndexed { index, point ->
                mapView.overlays.add(
                    DraggableCornerMarker(mapView, context).apply {
                        position = point
                        relatedObject = index
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) = syncPointFromMarker(marker)
                            override fun onMarkerDragEnd(marker: Marker) = syncPointFromMarker(marker)
                            override fun onMarkerDragStart(marker: Marker) = Unit

                            private fun syncPointFromMarker(marker: Marker) {
                                val index = marker.relatedObject as? Int ?: return
                                if (index !in points.indices) return
                                val pos = marker.position
                                points[index] = GeoPoint(pos.latitude, pos.longitude)
                                updatePolygonShape()
                            }
                        })
                    },
                )
            }
        }
        mapView.invalidate()
    }

    val savedPolygonKey = settings.polygon.joinToString { "${it.lat},${it.lng}" }

    LaunchedEffect(points.size, boundaryEditing, savedPolygonKey) {
        if (!boundaryEditing && settings.polygon.isNotEmpty()) {
            reloadPointsFromSettings()
        }
        refreshOverlays()
    }

    DisposableEffect(mapView, lifecycleOwner) {
        refreshOverlays()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    syncMyLocationOverlay()
                    if (!centeredOnUser) centerMapOnUser(animate = false)
                    mapView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    myLocationOverlay.disableMyLocation()
                    mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            myLocationOverlay.disableMyLocation()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    fun centerOnMe() {
        if (!hasLocationPermission()) {
            error = "Location permission required"
            return
        }
        centerMapOnUser(animate = true)
        error = null
    }

    fun saveBoundary() {
        error = null
        actionMessage = null
        val payload = points.map { LatLngPoint(it.latitude, it.longitude) }
        val myLocation = myLocationOverlay.myLocation
        val currentlyInside = myLocation?.let { location ->
            PolygonUtil.contains(location.latitude, location.longitude, payload)
        }
        viewModel.savePolygon(payload, currentlyInside) { message ->
            if (message == null) {
                viewModel.finishSetup { setupError ->
                    if (setupError == null) {
                        actionMessage = "Boundary saved"
                        boundaryEditing = false
                    } else {
                        error = setupError
                    }
                }
            } else {
                error = message
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Garage Door", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (boundaryEditing) {
                                    "Editing: tap to add corners · drag to adjust (${points.size})"
                                } else {
                                    "Boundary locked · ${points.size} corners"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { menuExpanded = !menuExpanded }) {
                            Text("Menu")
                            Icon(
                                imageVector = if (menuExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (menuExpanded) "Collapse menu" else "Expand menu",
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = menuExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Door", style = MaterialTheme.typography.labelLarge)
                            if (uiState.isLoading && uiState.status == null) {
                                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            }
                            uiState.status?.let {
                                Text("Status: ${it.door ?: "unknown"} · busy=${it.busy} · cooldown=${it.cooldown}")
                                it.version?.let { v -> Text("Firmware: $v", style = MaterialTheme.typography.bodySmall) }
                            }
                            uiState.statusMessage?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            actionMessage?.let {
                                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                            error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        actionMessage = null
                                        error = null
                                        viewModel.triggerManual { msg -> if (msg != null) error = msg else actionMessage = "Triggered" }
                                    },
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Open / Close") }
                                Button(
                                    onClick = { viewModel.refreshStatus() },
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Refresh") }
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-open on arrival", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "Geofence ~${settings.geofenceRadiusMeters.toInt()} m",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Switch(
                                    checked = settings.autoOpenEnabled,
                                    onCheckedChange = { viewModel.setAutoOpen(it) },
                                )
                            }

                            HorizontalDivider()

                            Text("Boundary", style = MaterialTheme.typography.labelLarge)
                            Button(
                                onClick = {
                                    if (boundaryEditing) {
                                        exitBoundaryEditMode()
                                    } else {
                                        boundaryEditing = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (boundaryEditing) "Done editing" else "Edit boundary")
                            }
                            if (boundaryEditing) {
                                Text(
                                    "Drag points or tap the map to add corners.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            if (points.isNotEmpty()) {
                                                points.removeAt(points.lastIndex)
                                                refreshOverlays()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Undo") }
                                    Button(
                                        onClick = {
                                            points.clear()
                                            refreshOverlays()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Clear") }
                                }
                                Button(
                                    onClick = { saveBoundary() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Save boundary") }
                            }
                            Button(onClick = { centerOnMe() }, modifier = Modifier.fillMaxWidth()) {
                                Text("My location")
                            }

                            HorizontalDivider()

                            Text("API connection", style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                label = { Text("API URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = apiToken,
                                onValueChange = { apiToken = it },
                                label = { Text("API key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Button(
                                onClick = {
                                    error = null
                                    actionMessage = null
                                    viewModel.saveCredentials(baseUrl, apiToken) { message ->
                                        if (message == null) actionMessage = "Credentials saved" else error = message
                                    }
                                },
                                enabled = !uiState.isLoading && apiToken.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Test & save credentials") }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            if (!menuExpanded) {
                Text(
                    "Menu ▼ for controls",
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
