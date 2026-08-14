package com.runtrack.app.weather

import com.runtrack.app.data.WeatherSnapshotEntity
import com.runtrack.app.domain.UnitSystem
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDisplayTest {
    @Test fun weatherDisplayRespectsSelectedUnits() {
        val snapshot = WeatherSnapshotEntity(
            workoutId = "workout",
            capturedAt = 1,
            latitude = 55.75,
            longitude = 37.62,
            temperatureC = 20.0,
            apparentTemperatureC = 18.0,
            relativeHumidityPercent = 60,
            windSpeedMps = 5.0,
            precipitationMm = 2.54,
            weatherCode = 61,
            source = "OPEN_METEO",
            fetchedAt = 2,
        )

        val metric = WeatherFormatter.format(snapshot, UnitSystem.METRIC, Locale.US)
        val imperial = WeatherFormatter.format(snapshot, UnitSystem.IMPERIAL, Locale.US)

        assertEquals("20 °C · дождь", metric.headline)
        assertTrue(metric.details.contains("18.0 км/ч"))
        assertEquals("68 °F · дождь", imperial.headline)
        assertTrue(imperial.details.contains("0.1 in"))
    }
}
