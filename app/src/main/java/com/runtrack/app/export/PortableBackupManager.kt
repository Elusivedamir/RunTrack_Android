package com.runtrack.app.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.runtrack.app.data.RoutePointEntity
import com.runtrack.app.data.RunTrackDatabase
import com.runtrack.app.data.WorkoutEntity
import com.runtrack.app.data.WeatherSnapshotEntity
import com.runtrack.app.domain.MapLayer
import com.runtrack.app.domain.PortableBackupCrypto
import com.runtrack.app.domain.GoalKind
import com.runtrack.app.domain.UnitSystem
import com.runtrack.app.domain.WorkoutStatus
import com.runtrack.app.settings.RunTrackSettings
import com.runtrack.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Portable encrypted backup. The encryption key is derived from a user passphrase instead of the
 * device-only Android Keystore so the package can be restored on a different phone.
 */
class PortableBackupManager(
    private val context: Context,
    private val db: RunTrackDatabase,
    private val settingsRepository: SettingsRepository,
) {
    private val dao = db.workoutDao()

    suspend fun buildEncrypted(passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        validatePassphrase(passphrase)
        try {
            val relations = dao.getAllCompletedWithRoutes()
            val settings = settingsRepository.settings.first()
            val root = JSONObject().apply {
                put("format", BACKUP_FORMAT)
                put("version", BACKUP_VERSION)
                put("createdAt", System.currentTimeMillis())
                put("settings", settings.toJson())
                put("workouts", JSONArray().apply {
                    relations.forEach { relation ->
                        put(JSONObject().apply {
                            put("workout", relation.workout.toJson())
                            put("route", JSONArray().apply {
                                relation.route.sortedBy { it.elapsedRealtimeMillis }.forEach { put(it.toJson()) }
                            })
                            put("heartRate", JSONArray().apply {
                                relation.heartRateSamples
                                    .sortedBy { it.elapsedRealtimeMillis }
                                    .forEach { put(it.toJson()) }
                            })
                            put("weather", JSONArray().apply {
                                relation.weatherSnapshots
                                    .sortedBy { it.capturedAt }
                                    .forEach { put(it.toJson()) }
                            })
                        })
                    }
                })
            }
            PortableBackupCrypto.encrypt(root.toString().toByteArray(StandardCharsets.UTF_8), passphrase)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    suspend fun writeToUri(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: error("Не удалось открыть файл резервной копии")
    }

    suspend fun readFromUri(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BACKUP_BYTES) { "Резервная копия слишком большая" }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: error("Не удалось открыть резервную копию")
    }

    /** Merge restore. Duplicate workout ids are rejected before any row is written. */
    suspend fun restoreEncrypted(bytes: ByteArray, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        validatePassphrase(passphrase)
        val plain = try {
            PortableBackupCrypto.decrypt(bytes, passphrase)
        } finally {
            passphrase.fill('\u0000')
        }
        val root = runCatching { JSONObject(String(plain, StandardCharsets.UTF_8)) }
            .getOrElse { throw IllegalArgumentException("Повреждённая резервная копия", it) }
        check(root.optString("format") == BACKUP_FORMAT) { "Неизвестный формат резервной копии" }
        val version = root.optInt("version", -1)
        check(version in 1..BACKUP_VERSION) { "Версия резервной копии не поддерживается: $version" }

        data class RestoredWorkout(
            val workout: WorkoutEntity,
            val route: List<RoutePointEntity>,
            val heartRate: List<com.runtrack.app.data.HeartRateSampleEntity>,
            val weather: List<WeatherSnapshotEntity>,
        )
        val parsed = mutableListOf<RestoredWorkout>()
        val array = root.optJSONArray("workouts") ?: JSONArray()
        for (i in 0 until array.length()) {
            val entry = array.getJSONObject(i)
            val workout = entry.getJSONObject("workout").toWorkout()
            require(workout.status == WorkoutStatus.COMPLETED.name) { "Backup содержит незавершённую тренировку" }
            val routeArray = entry.optJSONArray("route") ?: JSONArray()
            val route = (0 until routeArray.length()).map { idx -> routeArray.getJSONObject(idx).toRoutePoint(workout.id) }
            val heartArray = entry.optJSONArray("heartRate") ?: JSONArray()
            val heartRate = (0 until heartArray.length()).map { idx ->
                heartArray.getJSONObject(idx).toHeartRateSample(workout.id)
            }

            /*
             * Backup versions 1-2 did not contain weather.
             * Missing weather therefore restores as an empty list.
             */
            val weatherArray = entry.optJSONArray("weather") ?: JSONArray()
            val weather = (0 until weatherArray.length()).map { idx ->
                weatherArray.getJSONObject(idx).toWeatherSnapshot(workout.id)
            }

            parsed += RestoredWorkout(
                workout = workout,
                route = route,
                heartRate = heartRate,
                weather = weather,
            )
        }

        val ids = parsed.map { it.workout.id }
        require(ids.size == ids.toSet().size) { "Backup содержит повторяющиеся workout id" }
        for (entry in parsed) {
            val workout = entry.workout
            check(dao.getWorkout(workout.id) == null) { "Тренировка ${workout.id} уже существует" }
            check(dao.getWorkoutBySessionToken(workout.sessionToken) == null) { "Сессия ${workout.sessionToken} уже существует" }
        }

        val previousSettings = settingsRepository.settings.first()
        val restoredSettings = root.optJSONObject("settings")?.toSettings()
        db.withTransaction {
            parsed.forEach { entry ->
                dao.insertWorkout(entry.workout)
                entry.route.forEach { dao.insertRoutePoint(it.copy(rowId = 0)) }
                entry.heartRate.forEach {
                    check(
                        dao.insertHeartRateSample(it.copy(rowId = 0)) > 0L
                    ) {
                        "Не удалось восстановить sample пульса"
                    }
                }

                entry.weather.forEach {
                    check(
                        dao.insertWeatherSnapshot(it.copy(rowId = 0)) > 0L
                    ) {
                        "Не удалось восстановить weather snapshot"
                    }
                }
            }
        }
        if (restoredSettings != null) {
            try {
                settingsRepository.restore(restoredSettings)
            } catch (settingsError: Throwable) {
                // Compensate the Room commit so restore remains all-or-nothing from the user's perspective.
                try {
                    db.withTransaction {
                        parsed.forEach { entry -> check(dao.deleteWorkout(entry.workout.id) == 1) }
                    }
                    settingsRepository.restore(previousSettings)
                } catch (rollbackError: Throwable) {
                    settingsError.addSuppressed(rollbackError)
                }
                throw settingsError
            }
        }
        parsed.size
    }

    private fun validatePassphrase(passphrase: CharArray) {
        require(passphrase.size >= 8) { "Пароль резервной копии должен содержать минимум 8 символов" }
    }

    private fun RunTrackSettings.toJson() = JSONObject().apply {
        put("notifications", notificationsEnabled)
        put("voiceAnnouncements", voiceAnnouncementsEnabled)
        put("profileName", profileName)
        put("keepScreenOn", keepScreenOn)
        put("units", units.name)
        put("mapLayer", mapLayer.name)
        weightKg?.let { put("weightKg", it) }
        heightCm?.let { put("heightCm", it) }
        heartRateDeviceAddress?.let { put("heartRateDeviceAddress", it) }
        heartRateDeviceName?.let { put("heartRateDeviceName", it) }
    }

    private fun JSONObject.toSettings() = RunTrackSettings(
        notificationsEnabled = optBoolean("notifications", false),
        voiceAnnouncementsEnabled = optBoolean("voiceAnnouncements", true),
        profileName = optString("profileName", "Пользователь").trim().take(80).ifBlank { "Пользователь" },
        keepScreenOn = optBoolean("keepScreenOn", false),
        units = optString("units").let { runCatching { UnitSystem.valueOf(it) }.getOrDefault(UnitSystem.METRIC) },
        mapLayer = optString("mapLayer").let { runCatching { MapLayer.valueOf(it) }.getOrDefault(MapLayer.STANDARD) },
        weightKg = if (has("weightKg")) optDouble("weightKg").takeIf { it.isFinite() && it in 30.0..300.0 } else null,
        heightCm = if (has("heightCm")) optDouble("heightCm").takeIf { it.isFinite() && it in 80.0..250.0 } else null,
        heartRateDeviceAddress = optString("heartRateDeviceAddress").takeIf { has("heartRateDeviceAddress") && it.isNotBlank() && it.length <= 32 },
        heartRateDeviceName = optString("heartRateDeviceName").takeIf { has("heartRateDeviceName") && it.isNotBlank() && it.length <= 100 },
    )

    private fun WorkoutEntity.toJson() = JSONObject().apply {
        put("id", id); put("sessionToken", sessionToken); put("type", type)
        put("goalKind", goalKind); goalDistanceMeters?.let { put("goalDistanceMeters", it) }; goalDurationMillis?.let { put("goalDurationMillis", it) }; goalReachedAt?.let { put("goalReachedAt", it) }
        put("status", status)
        put("startedAt", startedAt); put("startedElapsedRealtimeMillis", startedElapsedRealtimeMillis)
        endedAt?.let { put("endedAt", it) }
        put("elapsedMillis", elapsedMillis); put("movingMillis", movingMillis); put("distanceMeters", distanceMeters)
        put("averageSpeedMps", averageSpeedMps); put("caloriesEstimate", caloriesEstimate)
        heartRateAverageBpm?.let { put("heartRateAverageBpm", it) }; heartRateMaxBpm?.let { put("heartRateMaxBpm", it) }
        elevationGainMeters?.let { put("elevationGainMeters", it) }; elevationLossMeters?.let { put("elevationLossMeters", it) }
        title?.let { put("title", it) }; note?.let { put("note", it) }
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toWorkout(): WorkoutEntity {
        val id = getString("id").also { require(it.isNotBlank() && it.length <= 128) }
        val type = getString("type").also { require(it in setOf("RUN", "WALK", "BIKE")) }
        val startedAt = getLong("startedAt")
        val endedAt = if (has("endedAt") && !isNull("endedAt")) getLong("endedAt") else null
        val distance = getDouble("distanceMeters").also { require(it.isFinite() && it >= 0) }
        val elapsed = getLong("elapsedMillis").also { require(it >= 0L) }
        val moving = getLong("movingMillis").also { require(it in 0L..elapsed) }
        if (endedAt != null) require(endedAt >= startedAt) { "Некорректные границы времени тренировки" }
        val status = getString("status").also { require(it == WorkoutStatus.COMPLETED.name) }
        return WorkoutEntity(
            id = id,
            sessionToken = getString("sessionToken").also { require(it.isNotBlank() && it.length <= 128) },
            type = type,
            goalKind = optString("goalKind", GoalKind.NONE.name).let { runCatching { GoalKind.valueOf(it) }.getOrDefault(GoalKind.NONE).name },
            goalDistanceMeters = if (has("goalDistanceMeters")) optDouble("goalDistanceMeters").takeIf { it.isFinite() && it > 0.0 } else null,
            goalDurationMillis = if (has("goalDurationMillis")) optLong("goalDurationMillis").takeIf { it > 0L } else null,
            goalReachedAt = if (has("goalReachedAt")) optLong("goalReachedAt").takeIf { it > 0L } else null,
            status = status,
            startedAt = startedAt,
            // Monotonic timestamps are meaningful only on the original boot. They remain archival after restore.
            startedElapsedRealtimeMillis = optLong("startedElapsedRealtimeMillis", 0L).coerceAtLeast(0),
            endedAt = endedAt,
            elapsedMillis = elapsed,
            movingMillis = moving,
            distanceMeters = distance,
            averageSpeedMps = getDouble("averageSpeedMps").takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0,
            caloriesEstimate = optInt("caloriesEstimate", 0).coerceAtLeast(0),
            heartRateAverageBpm = if (has("heartRateAverageBpm")) optInt("heartRateAverageBpm").takeIf { it in 25..250 } else null,
            heartRateMaxBpm = if (has("heartRateMaxBpm")) optInt("heartRateMaxBpm").takeIf { it in 25..250 } else null,
            elevationGainMeters = nullableFiniteDouble("elevationGainMeters"),
            elevationLossMeters = nullableFiniteDouble("elevationLossMeters"),
            title = optString("title").takeIf { has("title") && it.length <= 200 },
            note = optString("note").takeIf { has("note") && it.length <= 4000 },
            createdAt = optLong("createdAt", startedAt),
            updatedAt = optLong("updatedAt", endedAt ?: startedAt),
        )
    }

    private fun RoutePointEntity.toJson() = JSONObject().apply {
        put("timestampMillis", timestampMillis); put("elapsedRealtimeMillis", elapsedRealtimeMillis)
        put("movingElapsedMillis", movingElapsedMillis); put("segmentIndex", segmentIndex)
        put("latitude", latitude); put("longitude", longitude); put("accuracyMeters", accuracyMeters.toDouble())
        altitudeMeters?.let { put("altitudeMeters", it) }; speedMps?.let { put("speedMps", it.toDouble()) }
        bearingDegrees?.let { put("bearingDegrees", it.toDouble()) }; provider?.let { put("provider", it) }
    }

    private fun JSONObject.toRoutePoint(workoutId: String): RoutePointEntity {
        val lat = getDouble("latitude").also { require(it.isFinite() && it in -90.0..90.0) }
        val lon = getDouble("longitude").also { require(it.isFinite() && it in -180.0..180.0) }
        val accuracy = getDouble("accuracyMeters").also { require(it.isFinite() && it >= 0.0 && it <= 10_000.0) }.toFloat()
        return RoutePointEntity(
            workoutId = workoutId,
            timestampMillis = getLong("timestampMillis"),
            elapsedRealtimeMillis = optLong("elapsedRealtimeMillis", 0).coerceAtLeast(0),
            movingElapsedMillis = optLong("movingElapsedMillis", 0).coerceAtLeast(0),
            segmentIndex = optInt("segmentIndex", 0).coerceAtLeast(0),
            latitude = lat,
            longitude = lon,
            accuracyMeters = accuracy,
            altitudeMeters = nullableFiniteDouble("altitudeMeters"),
            speedMps = nullableFiniteDouble("speedMps")?.takeIf { it >= 0 }?.toFloat(),
            bearingDegrees = nullableFiniteDouble("bearingDegrees")?.toFloat(),
            provider = optString("provider").takeIf { has("provider") && it.length <= 100 },
        )
    }

    private fun com.runtrack.app.data.HeartRateSampleEntity.toJson() = JSONObject().apply {
        put("timestampMillis", timestampMillis); put("elapsedRealtimeMillis", elapsedRealtimeMillis)
        put("bpm", bpm); put("source", source)
    }

    private fun JSONObject.toHeartRateSample(workoutId: String) = com.runtrack.app.data.HeartRateSampleEntity(
        workoutId = workoutId,
        timestampMillis = getLong("timestampMillis"),
        elapsedRealtimeMillis = optLong("elapsedRealtimeMillis", 0L).coerceAtLeast(0L),
        bpm = getInt("bpm").also { require(it in 25..250) },
        source = optString("source", "BLE_HRS").take(64),
    )

    private fun WeatherSnapshotEntity.toJson() = JSONObject().apply {
        put("capturedAt", capturedAt)
        put("latitude", latitude)
        put("longitude", longitude)

        temperatureC?.let { put("temperatureC", it) }
        apparentTemperatureC?.let {
            put("apparentTemperatureC", it)
        }
        relativeHumidityPercent?.let {
            put("relativeHumidityPercent", it)
        }
        windSpeedMps?.let { put("windSpeedMps", it) }
        precipitationMm?.let { put("precipitationMm", it) }
        weatherCode?.let { put("weatherCode", it) }

        put("source", source)
        put("fetchedAt", fetchedAt)
    }

    private fun JSONObject.toWeatherSnapshot(
        workoutId: String,
    ): WeatherSnapshotEntity {
        val capturedAt = getLong("capturedAt").also {
            require(it >= 0L) {
                "Некорректное время weather snapshot"
            }
        }

        val latitude = getDouble("latitude").also {
            require(it.isFinite() && it in -90.0..90.0) {
                "Некорректная latitude погоды"
            }
        }

        val longitude = getDouble("longitude").also {
            require(it.isFinite() && it in -180.0..180.0) {
                "Некорректная longitude погоды"
            }
        }

        val humidity =
            if (has("relativeHumidityPercent") &&
                !isNull("relativeHumidityPercent")
            ) {
                getInt("relativeHumidityPercent").also {
                    require(it in 0..100) {
                        "Некорректная влажность"
                    }
                }
            } else {
                null
            }

        val wind = nullableFiniteDouble("windSpeedMps")
            ?.also {
                require(it >= 0.0) {
                    "Некорректная скорость ветра"
                }
            }

        val precipitation =
            nullableFiniteDouble("precipitationMm")
                ?.also {
                    require(it >= 0.0) {
                        "Некорректные осадки"
                    }
                }

        val source = getString("source")
            .trim()
            .also {
                require(it.isNotBlank() && it.length <= 64) {
                    "Некорректный weather source"
                }
            }

        val fetchedAt = getLong("fetchedAt").also {
            require(it >= 0L) {
                "Некорректное время получения погоды"
            }
        }

        return WeatherSnapshotEntity(
            workoutId = workoutId,
            capturedAt = capturedAt,
            latitude = latitude,
            longitude = longitude,
            temperatureC =
                nullableFiniteDouble("temperatureC"),
            apparentTemperatureC =
                nullableFiniteDouble("apparentTemperatureC"),
            relativeHumidityPercent = humidity,
            windSpeedMps = wind,
            precipitationMm = precipitation,
            weatherCode =
                if (has("weatherCode") &&
                    !isNull("weatherCode")
                ) {
                    getInt("weatherCode")
                } else {
                    null
                },
            source = source,
            fetchedAt = fetchedAt,
        )
    }

    private fun JSONObject.nullableFiniteDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf(Double::isFinite)

    companion object {
        private const val BACKUP_FORMAT = "RunTrackPortableBackup"
        private const val BACKUP_VERSION = 3
        private const val MAX_BACKUP_BYTES = 256L * 1024L * 1024L
    }
}
