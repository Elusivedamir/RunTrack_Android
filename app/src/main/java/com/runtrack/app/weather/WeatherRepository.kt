package com.runtrack.app.weather

import com.runtrack.app.data.WeatherSnapshotEntity
import com.runtrack.app.data.WorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of one weather synchronization attempt.
 *
 * Weather is deliberately optional: a network/provider failure must
 * never prevent a workout from being recorded or completed.
 */
sealed interface WeatherFetchResult {

    data class Stored(
        val snapshot: WeatherSnapshotEntity,
    ) : WeatherFetchResult

    data class AlreadyStored(
        val snapshot: WeatherSnapshotEntity,
    ) : WeatherFetchResult

    data object WorkoutNotFound : WeatherFetchResult

    data class Failed(
        val reason: String,
    ) : WeatherFetchResult
}

/**
 * Owns provider -> Room persistence.
 *
 * The workout lifecycle does not call this repository yet.
 * That integration is introduced separately so network availability
 * can never become a prerequisite for GPS tracking.
 */
class WeatherRepository(
    private val workoutDao: WorkoutDao,
    private val client: OpenMeteoWeatherClient = OpenMeteoWeatherClient(),
) {

    suspend fun fetchAndStoreCurrent(
        workoutId: String,
        latitude: Double,
        longitude: Double,
    ): WeatherFetchResult = withContext(Dispatchers.IO) {
        if (workoutId.isBlank()) {
            return@withContext WeatherFetchResult.WorkoutNotFound
        }

        if (workoutDao.getWorkout(workoutId) == null) {
            return@withContext WeatherFetchResult.WorkoutNotFound
        }

        try {
            val observation = client.fetchCurrent(
                latitude = latitude,
                longitude = longitude,
            )

            val snapshot = WeatherSnapshotEntity(
                workoutId = workoutId,
                capturedAt = observation.observedAtMillis,
                latitude = observation.latitude,
                longitude = observation.longitude,
                temperatureC = observation.temperatureC,
                apparentTemperatureC = observation.apparentTemperatureC,
                relativeHumidityPercent = observation.relativeHumidityPercent,
                windSpeedMps = observation.windSpeedMps,
                precipitationMm = observation.precipitationMm,
                weatherCode = observation.weatherCode,
                source = observation.source,
                fetchedAt = System.currentTimeMillis(),
            )

            val insertedRowId = workoutDao.insertWeatherSnapshot(snapshot)

            if (insertedRowId == INSERT_IGNORED) {
                WeatherFetchResult.AlreadyStored(snapshot)
            } else {
                WeatherFetchResult.Stored(
                    snapshot.copy(rowId = insertedRowId)
                )
            }
        } catch (error: Exception) {
            WeatherFetchResult.Failed(
                reason = error.message
                    ?.takeIf { it.isNotBlank() }
                    ?: error.javaClass.simpleName,
            )
        }
    }

    suspend fun getSnapshots(
        workoutId: String,
    ): List<WeatherSnapshotEntity> =
        workoutDao.getWeatherSnapshots(workoutId)

    suspend fun getLatestSnapshot(
        workoutId: String,
    ): WeatherSnapshotEntity? =
        workoutDao.getLatestWeatherSnapshot(workoutId)

    private companion object {
        const val INSERT_IGNORED = -1L
    }
}
