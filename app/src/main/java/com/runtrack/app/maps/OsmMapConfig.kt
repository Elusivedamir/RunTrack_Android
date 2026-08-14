package com.runtrack.app.maps

import com.runtrack.app.domain.MapLayer

/**
 * Public OSM Standard tiles for personal/testing use.
 * No bulk download, prefetch or offline packs.
 *
 * MapLayer.TERRAIN is kept for saved-settings compatibility; on this free endpoint it is
 * only a higher-contrast raster treatment, not a separate terrain dataset.
 */
object OsmMapConfig {
    const val TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    const val ATTRIBUTION = "© OpenStreetMap contributors"
    const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

    fun styleJson(layer: MapLayer): String {
        val paint = when (layer) {
            MapLayer.STANDARD -> """"raster-opacity": 1.0"""
            MapLayer.TERRAIN -> """
                "raster-opacity": 1.0,
                "raster-saturation": -0.30,
                "raster-contrast": 0.18,
                "raster-brightness-max": 0.92
            """.trimIndent()
        }

        return """
            {
              "version": 8,
              "name": "RunTrack OpenStreetMap",
              "sources": {
                "osm": {
                  "type": "raster",
                  "tiles": ["$TILE_URL"],
                  "tileSize": 256,
                  "minzoom": 0,
                  "maxzoom": 19,
                  "attribution": "$ATTRIBUTION"
                }
              },
              "layers": [{
                "id": "osm-raster",
                "type": "raster",
                "source": "osm",
                "paint": { $paint }
              }]
            }
        """.trimIndent()
    }
}
