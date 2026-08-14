package com.runtrack.app.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.runtrack.app.domain.LocationSample
import com.runtrack.app.domain.MapLayer
import com.runtrack.app.domain.RouteGeometry
import kotlinx.coroutines.launch

private val MapBackground = Color(0xFF0C1821)
private val TerrainBackground = Color(0xFF0E1B16)
private val RouteGreen = Color(0xFF62D72F)
private val RouteColors = listOf(RouteGreen, Color(0xFF42A5F5), Color(0xFFF4B400), Color(0xFFAB7CFF))
private val MapControlBackground = Color(0xE6111E27)

/**
 * Shows the real Maps SDK surface only when an API key is configured. Otherwise the local
 * Canvas renderer remains fully functional, so route recording and viewing never depend on Maps.
 */
@Composable
fun RunTrackRouteMap(
    routes: List<List<LocationSample>>,
    layer: MapLayer,
    onLayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapsAvailable = remember(context) {
        runCatching {
            MapsRuntimeConfig.isConfigured(context) &&
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }
    val cleanRoutes = remember(routes) {
        routes.map { RouteGeometry.downsampleForRender(it) }.filter { it.isNotEmpty() }
    }

    if (mapsAvailable && cleanRoutes.isNotEmpty()) {
        GoogleRouteMap(cleanRoutes, layer, onLayer, modifier)
    } else {
        CanvasRouteMap(cleanRoutes, layer, onLayer, modifier)
    }
}

@Composable
private fun GoogleRouteMap(
    routes: List<List<LocationSample>>,
    layer: MapLayer,
    onLayer: () -> Unit,
    modifier: Modifier,
) {
    val latLngRoutes = remember(routes) {
        routes.map { route -> route.map { LatLng(it.latitude, it.longitude) } }
    }
    val allPoints = remember(latLngRoutes) { latLngRoutes.flatten() }
    val bounds = remember(allPoints) {
        LatLngBounds.builder().apply { allPoints.forEach { include(it) } }.build()
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(allPoints.first(), 15f)
    }
    val scope = rememberCoroutineScope()
    var mapLoaded by remember { mutableStateOf(false) }

    fun centerRoute(animated: Boolean) {
        if (!mapLoaded) return
        val update = if (allPoints.size == 1) {
            CameraUpdateFactory.newLatLngZoom(allPoints.first(), 16f)
        } else {
            CameraUpdateFactory.newLatLngBounds(bounds, 96)
        }
        if (animated) {
            scope.launch {
                runCatching { cameraPositionState.animate(update, 450) }
            }
        } else {
            runCatching { cameraPositionState.move(update) }
        }
    }

    LaunchedEffect(mapLoaded, bounds) {
        if (mapLoaded) centerRoute(animated = false)
    }

    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentDescription = "Карта маршрута",
            properties = MapProperties(
                mapType = if (layer == MapLayer.TERRAIN) MapType.TERRAIN else MapType.NORMAL,
            ),
            uiSettings = MapUiSettings(
                compassEnabled = true,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                rotationGesturesEnabled = false,
                tiltGesturesEnabled = false,
                zoomControlsEnabled = false,
            ),
            onMapLoaded = { mapLoaded = true },
        ) {
            latLngRoutes.forEachIndexed { index, points ->
                if (points.size >= 2) {
                    Polyline(
                        points = points,
                        color = RouteColors[index % RouteColors.size],
                        width = 9f,
                        geodesic = true,
                        zIndex = 2f,
                    )
                }
            }
            latLngRoutes.firstOrNull()?.firstOrNull()?.let { start ->
                Circle(
                    center = start,
                    radius = 4.0,
                    fillColor = RouteGreen,
                    strokeColor = Color.White,
                    strokeWidth = 2f,
                    zIndex = 3f,
                )
            }
            latLngRoutes.lastOrNull()?.lastOrNull()?.let { finish ->
                Circle(
                    center = finish,
                    radius = 5.0,
                    fillColor = RouteGreen,
                    strokeColor = Color.White,
                    strokeWidth = 3f,
                    zIndex = 3f,
                )
            }
        }
        RouteMapControls(
            onCenter = { centerRoute(animated = true) },
            onLayer = onLayer,
            modifier = Modifier.align(Alignment.TopEnd).padding(9.dp),
        )
    }
}

@Composable
private fun CanvasRouteMap(
    routes: List<List<LocationSample>>,
    layer: MapLayer,
    onLayer: () -> Unit,
    modifier: Modifier,
) {
    var pan by remember(routes) { mutableStateOf(Offset.Zero) }
    Box(
        modifier.background(
            if (layer == MapLayer.STANDARD) MapBackground else TerrainBackground,
            RoundedCornerShape(16.dp),
        )
    ) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(routes) {
                detectDragGestures { _, dragAmount -> pan += dragAmount }
            }
        ) {
            val road = if (layer == MapLayer.STANDARD) Color(0xFF20313C) else Color(0xFF244032)
            val minor = if (layer == MapLayer.STANDARD) Color(0xFF172630) else Color(0xFF172D23)
            for (x in 0..size.width.toInt() step 60) drawLine(minor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 1f)
            for (y in 0..size.height.toInt() step 60) drawLine(minor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 1f)
            drawLine(road, Offset(0f, size.height * .32f), Offset(size.width, size.height * .62f), 6f)
            drawLine(road, Offset(size.width * .18f, 0f), Offset(size.width * .72f, size.height), 5f)

            val normalizedRoutes = RouteGeometry.normalizeRoutes(routes, size.width, size.height, 24.dp.toPx())
            normalizedRoutes.forEachIndexed { index, normalized ->
                val color = RouteColors[index % RouteColors.size]
                if (normalized.size >= 2) {
                    val path = Path().apply {
                        moveTo(normalized.first().x + pan.x, normalized.first().y + pan.y)
                        normalized.drop(1).forEach { lineTo(it.x + pan.x, it.y + pan.y) }
                    }
                    drawPath(path, color.copy(alpha = .22f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                } else if (normalized.size == 1) {
                    drawCircle(color, 6.dp.toPx(), Offset(normalized.first().x + pan.x, normalized.first().y + pan.y))
                }
            }
            normalizedRoutes.firstOrNull()?.firstOrNull()?.let {
                drawCircle(RouteGreen, 5.dp.toPx(), Offset(it.x + pan.x, it.y + pan.y))
            }
            normalizedRoutes.lastOrNull()?.lastOrNull()?.let {
                drawCircle(
                    RouteGreen,
                    7.dp.toPx(),
                    Offset(it.x + pan.x, it.y + pan.y),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
        if (routes.isEmpty()) {
            Text(
                "Маршрут недоступен",
                color = Color(0xFF8E9AA3),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        RouteMapControls(
            onCenter = { pan = Offset.Zero },
            onLayer = onLayer,
            modifier = Modifier.align(Alignment.TopEnd).padding(9.dp),
        )
    }
}

@Composable
private fun RouteMapControls(
    onCenter: () -> Unit,
    onLayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        RouteMapControl(Icons.Outlined.MyLocation, "Центрировать маршрут", onCenter)
        RouteMapControl(Icons.Outlined.Layers, "Слой карты", onLayer)
    }
}

@Composable
private fun RouteMapControl(icon: ImageVector, description: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.padding(2.dp).size(38.dp).background(MapControlBackground, CircleShape),
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
