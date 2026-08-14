package com.runtrack.app.maps

import com.runtrack.app.domain.MapLayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmMapConfigTest {
    @Test
    fun standardStyleUsesPublicOsmAndAttributionWithoutApiKey() {
        val style = OsmMapConfig.styleJson(MapLayer.STANDARD)
        assertTrue(style.contains("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertTrue(style.contains("© OpenStreetMap contributors"))
        assertFalse(style.contains("MAPS_" + "API_KEY"))
        assertFalse(style.contains("google.android.geo"))
    }

    @Test
    fun terrainCompatibilityModeUsesSameOsmSource() {
        val style = OsmMapConfig.styleJson(MapLayer.TERRAIN)
        assertTrue(style.contains("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertTrue(style.contains("\"raster-contrast\": 0.18"))
    }
}
