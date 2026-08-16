package com.runtrack.app.maps

import com.runtrack.app.domain.LocationSample
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLongitudeGeometryTest {
    @Test
    fun minimalArcAcrossAntimeridianStaysNarrow() {
        val arc = RouteLongitudeGeometry.minimalArc(listOf(179.9, -179.9))

        assertEquals(0.2, arc.spanDegrees, 1e-9)
        assertEquals(0.2, abs(arc.unwrap(-179.9) - arc.unwrap(179.9)), 1e-9)
        assertTrue(arc.eastDegrees > 180.0)
    }

    @Test
    fun canvasNormalizationDoesNotJumpAcrossWholeWorldAtDateline() {
        val route = listOf(
            LocationSample(0L, 0.0, 179.90, 5f),
            LocationSample(1_000L, 0.0, 179.95, 5f),
            LocationSample(2_000L, 0.0, -179.95, 5f),
            LocationSample(3_000L, 0.0, -179.90, 5f),
        )

        val normalized = RouteLongitudeGeometry.normalizeRoutes(
            routes = listOf(route),
            width = 400f,
            height = 200f,
            padding = 20f,
        ).single()

        assertEquals(route.size, normalized.size)
        assertTrue(normalized.zipWithNext().all { (a, b) -> b.x >= a.x })
    }
}
