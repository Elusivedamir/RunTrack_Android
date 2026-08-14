package com.runtrack.app.weather

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Normalized current weather returned by a remote provider.
 *
 * Internal units are deliberately provider-independent:
 * - temperature: Celsius
 * - humidity: percent
 * - wind: metres / second
 * - precipitation: millimetres
 */
data class WeatherObservation(
    val observedAtMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val temperatureC: Double?,
    val apparentTemperatureC: Double?,
    val relativeHumidityPercent: Int?,
    val windSpeedMps: Double?,
    val precipitationMm: Double?,
    val weatherCode: Int?,
    val source: String,
)

/**
 * Minimal Open-Meteo client.
 *
 * No Android UI or workout lifecycle is coupled to this class.
 * Network errors are propagated to the repository where they are
 * converted into a non-fatal WeatherFetchResult.
 */
class OpenMeteoWeatherClient(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val connectTimeoutMillis: Int = 8_000,
    private val readTimeoutMillis: Int = 12_000,
) {

    fun fetchCurrent(
        latitude: Double,
        longitude: Double,
    ): WeatherObservation {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Invalid latitude"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Invalid longitude"
        }

        val lat = String.format(Locale.US, "%.6f", latitude)
        val lon = String.format(Locale.US, "%.6f", longitude)

        /*
         * Keep provider defaults for wind speed (km/h) and normalize
         * it to m/s after parsing. This keeps our Room model independent
         * from provider-specific unit configuration.
         */
        val requestUrl =
            "$endpoint" +
                "?latitude=$lat" +
                "&longitude=$lon" +
                "&current=" +
                "temperature_2m," +
                "relative_humidity_2m," +
                "apparent_temperature," +
                "precipitation," +
                "weather_code," +
                "wind_speed_10m" +
                "&timezone=UTC"

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode

            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                throw IOException(
                    "Open-Meteo HTTP $status" +
                        body.takeIf { it.isNotBlank() }
                            ?.let { ": ${it.take(MAX_ERROR_BODY_LENGTH)}" }
                            .orEmpty()
                )
            }

            return parseCurrent(
                body = body,
                requestedLatitude = latitude,
                requestedLongitude = longitude,
            )
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseCurrent(
        body: String,
        requestedLatitude: Double,
        requestedLongitude: Double,
    ): WeatherObservation {
        val root = try {
            JSONObject(body)
        } catch (error: Exception) {
            throw IOException("Invalid Open-Meteo JSON", error)
        }

        val current = root.optJSONObject("current")
            ?: throw IOException("Open-Meteo response has no current object")

        val observedAt = current
            .optString("time")
            .takeIf { it.isNotBlank() }
            ?: throw IOException("Open-Meteo response has no current.time")

        val observedAtMillis = try {
            LocalDateTime
                .parse(observedAt)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        } catch (error: Exception) {
            throw IOException(
                "Invalid Open-Meteo current.time: $observedAt",
                error,
            )
        }

        // Open-Meteo default wind speed unit is km/h.
        val windSpeedKmh = current.nullableDouble("wind_speed_10m")

        return WeatherObservation(
            observedAtMillis = observedAtMillis,
            latitude = requestedLatitude,
            longitude = requestedLongitude,
            temperatureC = current.nullableDouble("temperature_2m"),
            apparentTemperatureC = current.nullableDouble("apparent_temperature"),
            relativeHumidityPercent = current
                .nullableInt("relative_humidity_2m")
                ?.coerceIn(0, 100),
            windSpeedMps = windSpeedKmh?.div(KMH_PER_MPS),
            precipitationMm = current
                .nullableDouble("precipitation")
                ?.coerceAtLeast(0.0),
            weatherCode = current.nullableInt("weather_code"),
            source = SOURCE_OPEN_METEO,
        )
    }

    private fun JSONObject.nullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null

        return try {
            getDouble(name).takeIf { it.isFinite() }
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.nullableInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null

        return try {
            getInt(name)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val SOURCE_OPEN_METEO = "OPEN_METEO"

        private const val DEFAULT_ENDPOINT =
            "https://api.open-meteo.com/v1/forecast"

        private const val KMH_PER_MPS = 3.6
        private const val MAX_ERROR_BODY_LENGTH = 500
    }
}
