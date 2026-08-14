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
import com.runtrack.app.domain.LocationSample
import com.runtrack.app.domain.MapLayer
import com.runtrack.app.domain.RouteGeometry
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.MultiLineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.toJson
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

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
    val cleanRoutes = remember(routes) {
        routes.map { RouteGeometry.downsampleForRender(it) }.filter { it.isNotEmpty() }
    }
    var mapFailed by remember(layer) { mutableStateOf(false) }

    if (cleanRoutes.isNotEmpty() && !mapFailed) {
        MapLibreRouteMap(
            routes = cleanRoutes,
            layer = layer,
            onLayer = onLayer,
            onMapFailed = { mapFailed = true },
            modifier = modifier,
        )
    } else {
        CanvasRouteMap(cleanRoutes, layer, onLayer, modifier)
    }
}

@Composable
private fun MapLibreRouteMap(
    routes: List<List<LocationSample>>,
    layer: MapLayer,
    onLayer: () -> Unit,
    onMapFailed: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val allPoints = remember(routes) { routes.flatten() }
    val bounds = remember(allPoints) { paddedBounds(allPoints) }
    val cameraState = rememberCameraState()
    val scope = rememberCoroutineScope()
    var mapLoaded by remember(layer) { mutableStateOf(false) }

    val lineSegments = remember(routes) { routes.filter { it.size >= 2 } }
    val lineGeoJson = remember(lineSegments) {
        MultiLineString(
            lineSegments.map { segment ->
                segment.map { point ->
                    Position(longitude = point.longitude, latitude = point.latitude)
                }
            }
        ).toJson()
    }
    val startGeoJson = remember(routes) {
        val point = routes.first().first()
        Point(longitude = point.longitude, latitude = point.latitude).toJson()
    }
    val finishGeoJson = remember(routes) {
        val point = routes.last().last()
        Point(longitude = point.longitude, latitude = point.latitude).toJson()
    }

    val cameraPadding = PaddingValues(horizontal = 34.dp, vertical = 44.dp)

    LaunchedEffect(mapLoaded, bounds) {
        if (mapLoaded) {
            try {
                cameraState.jumpTo(bounds, padding = cameraPadding)
            } catch (_: Throwable) {
            }
        }
    }

    fun centerRoute() {
        if (!mapLoaded) return
        scope.launch {
            try {
                cameraState.animateTo(
                    boundingBox = bounds,
                    padding = cameraPadding,
                    duration = 450.milliseconds,
                )
            } catch (_: Throwable) {
            }
        }
    }

    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Json(OsmMapConfig.styleJson(layer)),
            cameraState = cameraState,
            options = MapOptions(
                gestureOptions = GestureOptions(
                    isRotateEnabled = false,
                    isScrollEnabled = true,
                    isTiltEnabled = false,
                    isZoomEnabled = true,
                    isDoubleTapEnabled = true,
                    isQuickZoomEnabled = true,
                ),
                ornamentOptions = OrnamentOptions.OnlyLogo,
            ),
            onMapLoadFailed = { onMapFailed() },
            onMapLoadFinished = { mapLoaded = true },
        ) {
            if (lineSegments.isNotEmpty()) {
                val routeSource = rememberGeoJsonSource(
                    data = GeoJsonData.JsonString(lineGeoJson),
                    options = GeoJsonOptions(synchronousUpdate = true),
                )
                LineLayer(
                    id = "runtrack-route-casing",
                    source = routeSource,
                    color = const(Color.Black.copy(alpha = 0.42f)),
                    width = const(7.dp),
                )
                LineLayer(
                    id = "runtrack-route",
                    source = routeSource,
                    color = const(RouteGreen),
                    width = const(4.dp),
                )
            }

            val startSource = rememberGeoJsonSource(
                data = GeoJsonData.JsonString(startGeoJson),
                options = GeoJsonOptions(synchronousUpdate = true),
            )
            CircleLayer(
                id = "runtrack-start",
                source = startSource,
                color = const(RouteGreen),
                radius = const(5.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(2.dp),
            )

            val finishSource = rememberGeoJsonSource(
                data = GeoJsonData.JsonString(finishGeoJson),
                options = GeoJsonOptions(synchronousUpdate = true),
            )
            CircleLayer(
                id = "runtrack-finish",
                source = finishSource,
                color = const(RouteGreen),
                radius = const(6.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(2.dp),
            )
        }

        RouteMapControls(
            onCenter = ::centerRoute,
            onLayer = onLayer,
            modifier = Modifier.align(Alignment.TopEnd).padding(9.dp),
        )

        Text(
            text = OsmMapConfig.ATTRIBUTION,
            color = Color.White,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .background(Color(0xC7111E27), RoundedCornerShape(4.dp))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(OsmMapConfig.COPYRIGHT_URL))
                        )
                    }
                }
                .padding(horizontal = 5.dp, vertical = 3.dp),
        )
    }
}

private fun paddedBounds(points: List<LocationSample>): BoundingBox {
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    val lonPad = max((east - west) * 0.08, 0.00045)
    val latPad = max((north - south) * 0.08, 0.00045)

    return BoundingBox(
        west = (west - lonPad).coerceAtLeast(-180.0),
        south = (south - latPad).coerceAtLeast(-90.0),
        east = (east + lonPad).coerceAtMost(180.0),
        north = (north + latPad).coerceAtMost(90.0),
    )
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
