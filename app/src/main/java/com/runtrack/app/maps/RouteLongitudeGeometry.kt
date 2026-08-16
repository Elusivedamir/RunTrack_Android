package com.runtrack.app.maps

import com.runtrack.app.domain.LocationSample
import kotlin.math.cos
import kotlin.math.min

internal data class LongitudeArc(
    val westDegrees: Double,
    val eastDegrees: Double,
) {
    init {
        require(westDegrees.isFinite())
        require(eastDegrees.isFinite())
        require(eastDegrees >= westDegrees)
    }

    val spanDegrees: Double
        get() = eastDegrees - westDegrees

    /**
     * Move a canonical longitude into the same world copy as this minimal arc.
     * The returned value may be outside [-180, 180], which is intentional for map bounds.
     */
    fun unwrap(longitude: Double): Double {
        var value = RouteLongitudeGeometry.normalizeLongitude(longitude)
        if (value < westDegrees) value += 360.0
        return value
    }
}

internal data class NormalizedRoutePoint(
    val x: Float,
    val y: Float,
)

internal object RouteLongitudeGeometry {
    /**
     * Returns the smallest circular longitude arc containing every point.
     *
     * The complement of the largest gap on the longitude circle is the unique minimal covering
     * arc (apart from exact ties). This avoids interpreting 179.9E -> 179.9W as a 359.8 degree
     * route.
     */
    fun minimalArc(longitudes: List<Double>): LongitudeArc {
        require(longitudes.isNotEmpty())
        require(longitudes.all(Double::isFinite))

        val sorted = longitudes.map(::normalizeLongitude).sorted()
        if (sorted.size == 1) {
            return LongitudeArc(sorted.first(), sorted.first())
        }

        var largestGap = Double.NEGATIVE_INFINITY
        var largestGapStartIndex = 0

        for (index in sorted.indices) {
            val current = sorted[index]
            val next = if (index == sorted.lastIndex) sorted.first() + 360.0 else sorted[index + 1]
            val gap = next - current
            if (gap > largestGap) {
                largestGap = gap
                largestGapStartIndex = index
            }
        }

        val west = if (largestGapStartIndex == sorted.lastIndex) {
            sorted.first()
        } else {
            sorted[largestGapStartIndex + 1]
        }
        val span = (360.0 - largestGap).coerceIn(0.0, 360.0)
        return LongitudeArc(westDegrees = west, eastDegrees = west + span)
    }

    /**
     * Canvas fallback uses the same antimeridian-aware world copy as MapLibre.
     * Route segments remain separate, so pause gaps are never joined.
     */
    fun normalizeRoutes(
        routes: List<List<LocationSample>>,
        width: Float,
        height: Float,
        padding: Float,
    ): List<List<NormalizedRoutePoint>> {
        if (width <= 0f || height <= 0f) return routes.map { emptyList() }
        val all = routes.flatten()
        if (all.isEmpty()) return routes.map { emptyList() }
        if (all.size == 1) {
            val only = NormalizedRoutePoint(width / 2f, height / 2f)
            return routes.map { route -> route.map { only } }
        }

        val longitudeArc = minimalArc(all.map { it.longitude })
        val meanLat = all.map { it.latitude }.average()
        val cosLat = cos(Math.toRadians(meanLat)).coerceAtLeast(0.01)

        fun projected(point: LocationSample): Pair<Double, Double> =
            (longitudeArc.unwrap(point.longitude) * cosLat) to point.latitude

        val projectedAll = all.map(::projected)
        val minX = projectedAll.minOf { it.first }
        val maxX = projectedAll.maxOf { it.first }
        val minY = projectedAll.minOf { it.second }
        val maxY = projectedAll.maxOf { it.second }
        val spanX = (maxX - minX).coerceAtLeast(1e-12)
        val spanY = (maxY - minY).coerceAtLeast(1e-12)
        val availableW = (width - padding * 2f).coerceAtLeast(1f)
        val availableH = (height - padding * 2f).coerceAtLeast(1f)
        val scale = min(availableW / spanX, availableH / spanY)
        val usedW = spanX * scale
        val usedH = spanY * scale
        val offsetX = (width - usedW) / 2.0
        val offsetY = (height - usedH) / 2.0

        return routes.map { route ->
            route.map { point ->
                val (x, y) = projected(point)
                NormalizedRoutePoint(
                    x = (offsetX + (x - minX) * scale).toFloat(),
                    y = (height - (offsetY + (y - minY) * scale)).toFloat(),
                )
            }
        }
    }

    internal fun normalizeLongitude(longitude: Double): Double {
        require(longitude.isFinite())
        return ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    }
}
