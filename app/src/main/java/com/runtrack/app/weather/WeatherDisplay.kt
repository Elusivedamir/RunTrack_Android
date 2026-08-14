package com.runtrack.app.weather

import com.runtrack.app.data.WeatherSnapshotEntity
import com.runtrack.app.domain.UnitSystem
import java.util.Locale

data class WeatherDisplay(
    val headline: String,
    val details: String,
)

object WeatherFormatter {
    fun format(
        snapshot: WeatherSnapshotEntity,
        units: UnitSystem,
        locale: Locale = Locale.getDefault(),
    ): WeatherDisplay {
        val temperature = snapshot.temperatureC?.takeIf(Double::isFinite)?.let { celsius ->
            val value = if (units == UnitSystem.METRIC) celsius else celsius * 9.0 / 5.0 + 32.0
            String.format(locale, "%.0f °%s", value, if (units == UnitSystem.METRIC) "C" else "F")
        }
        val condition = condition(snapshot.weatherCode)
        val details = buildList {
            snapshot.apparentTemperatureC?.takeIf(Double::isFinite)?.let { celsius ->
                val value = if (units == UnitSystem.METRIC) celsius else celsius * 9.0 / 5.0 + 32.0
                add(String.format(locale, "ощущается %.0f °%s", value, if (units == UnitSystem.METRIC) "C" else "F"))
            }
            snapshot.relativeHumidityPercent?.takeIf { it in 0..100 }?.let { add("влажность $it%") }
            snapshot.windSpeedMps?.takeIf { it.isFinite() && it >= 0.0 }?.let { metersPerSecond ->
                val value = if (units == UnitSystem.METRIC) metersPerSecond * 3.6 else metersPerSecond * 2.2369362920544
                add(String.format(locale, "ветер %.1f %s", value, if (units == UnitSystem.METRIC) "км/ч" else "mph"))
            }
            snapshot.precipitationMm?.takeIf { it.isFinite() && it > 0.0 }?.let { millimeters ->
                val value = if (units == UnitSystem.METRIC) millimeters else millimeters / 25.4
                add(String.format(locale, "осадки %.1f %s", value, if (units == UnitSystem.METRIC) "мм" else "in"))
            }
        }
        return WeatherDisplay(
            headline = listOfNotNull(temperature, condition).joinToString(" · ").ifBlank { "Погода сохранена" },
            details = details.joinToString(" · ").ifBlank { "Open-Meteo" },
        )
    }

    private fun condition(code: Int?): String? = when (code) {
        0 -> "ясно"
        1, 2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55, 56, 57 -> "морось"
        61, 63, 65, 66, 67, 80, 81, 82 -> "дождь"
        71, 73, 75, 77, 85, 86 -> "снег"
        95, 96, 99 -> "гроза"
        else -> null
    }
}
