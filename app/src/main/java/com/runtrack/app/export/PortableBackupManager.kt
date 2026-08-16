package com.runtrack.app.export

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import com.runtrack.app.data.HeartRateSampleEntity
import com.runtrack.app.data.RoutePointEntity
import com.runtrack.app.data.RunTrackDatabase
import com.runtrack.app.data.WorkoutEntity
import com.runtrack.app.data.WeatherSnapshotEntity
import com.runtrack.app.domain.MapLayer
import com.runtrack.app.domain.PortableBackupCrypto
import com.runtrack.app.domain.GoalKind
import com.runtrack.app.domain.UnitSystem
import com.runtrack.app.domain.WorkoutStatus
import com.runtrack.app.domain.WorkoutTime
import com.runtrack.app.settings.RunTrackSettings
import com.runtrack.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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
    private val restoreMutex = Mutex()
    private val restoreJournalFile = File(context.filesDir, RESTORE_JOURNAL_FILE)

    private data class RestoreWorkoutIdentity(
        val id: String,
        val sessionToken: String,
    )

    private data class PendingRestoreJournal(
        val backupSha256: String,
        val targetSettings: RunTrackSettings?,
        val workouts: List<RestoreWorkoutIdentity>,
    )

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
        restoreMutex.withLock {
            validatePassphrase(passphrase)
            val plain = try {
                PortableBackupCrypto.decrypt(bytes, passphrase)
            } finally {
                passphrase.fill('\u0000')
            }
            val fingerprint = sha256Hex(plain)
            val root = runCatching { JSONObject(String(plain, StandardCharsets.UTF_8)) }
                .getOrElse { throw IllegalArgumentException("Повреждённая резервная копия", it) }
            check(root.optString("format") == BACKUP_FORMAT) { "Неизвестный формат резервной копии" }
            val version = root.optInt("version", -1)
            check(version in 1..BACKUP_VERSION) { "Версия резервной копии не поддерживается: $version" }

            // Resolve an interrupted previous restore before validating duplicates for a new one.
            val pendingBefore = loadPendingRestoreJournal()
            if (pendingBefore != null) {
                val committed = recoverPendingRestoreLocked(pendingBefore)
                if (committed && pendingBefore.backupSha256 == fingerprint) {
                    return@withLock pendingBefore.workouts.size
                }
            }

            data class RestoredWorkout(
                val workout: WorkoutEntity,
                val route: List<RoutePointEntity>,
                val heartRate: List<HeartRateSampleEntity>,
                val weather: List<WeatherSnapshotEntity>,
            )
            val parsed = mutableListOf<RestoredWorkout>()
            val array = root.optJSONArray("workouts") ?: JSONArray()
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                val workout = entry.getJSONObject("workout").toWorkout()
                require(workout.status == WorkoutStatus.COMPLETED.name) { "Backup содержит незавершённую тренировку" }
                val routeArray = entry.optJSONArray("route") ?: JSONArray()
                val route = (0 until routeArray.length()).map { idx ->
                    routeArray.getJSONObject(idx).toRoutePoint(workout.id)
                }
                val heartArray = entry.optJSONArray("heartRate") ?: JSONArray()
                val heartRate = (0 until heartArray.length()).map { idx ->
                    heartArray.getJSONObject(idx).toHeartRateSample(workout.id)
                }

                /* Backup versions 1-2 did not contain weather. */
                val weatherArray = entry.optJSONArray("weather") ?: JSONArray()
                val weather = (0 until weatherArray.length()).map { idx ->
                    weatherArray.getJSONObject(idx).toWeatherSnapshot(workout.id)
                }

                parsed += RestoredWorkout(workout, route, heartRate, weather)
            }

            val ids = parsed.map { it.workout.id }
            require(ids.size == ids.toSet().size) { "Backup содержит повторяющиеся workout id" }
            for (entry in parsed) {
                val workout = entry.workout
                check(dao.getWorkout(workout.id) == null) { "Тренировка ${workout.id} уже существует" }
                check(dao.getWorkoutBySessionToken(workout.sessionToken) == null) { "Сессия ${workout.sessionToken} уже существует" }
            }

            val restoredSettings = root.optJSONObject("settings")?.toSettings()
            val pending = PendingRestoreJournal(
                backupSha256 = fingerprint,
                targetSettings = restoredSettings,
                workouts = parsed.map {
                    RestoreWorkoutIdentity(it.workout.id, it.workout.sessionToken)
                },
            )

            // The AtomicFile journal is durable before Room commits. On process death it tells
            // startup whether to finish settings or discard an uncommitted restore intent.
            writePendingRestoreJournal(pending)
            try {
                db.withTransaction {
                    parsed.forEach { entry ->
                        dao.insertWorkout(entry.workout)
                        entry.route.forEach { dao.insertRoutePoint(it.copy(rowId = 0)) }
                        entry.heartRate.forEach {
                            check(dao.insertHeartRateSample(it.copy(rowId = 0)) > 0L) {
                                "Не удалось восстановить sample пульса"
                            }
                        }
                        entry.weather.forEach {
                            check(dao.insertWeatherSnapshot(it.copy(rowId = 0)) > 0L) {
                                "Не удалось восстановить weather snapshot"
                            }
                        }
                    }
                }
            } catch (restoreError: Throwable) {
                // The Room transaction rolled back. Do not let cancellation interrupt cleanup of
                // the restore intent; existing user settings were never changed.
                try {
                    withContext(NonCancellable) { clearPendingRestoreJournal() }
                } catch (journalError: Throwable) {
                    restoreError.addSuppressed(journalError)
                }
                throw restoreError
            }

            // If this throws or the process dies here, the journal stays durable. The next startup
            // (or retry of the same backup) sees the committed Room rows and applies settings
            // idempotently before clearing the journal.
            if (restoredSettings != null) {
                settingsRepository.restore(restoredSettings)
            }
            clearPendingRestoreJournal()
            parsed.size
        }
    }

    /** Completes or discards a restore interrupted by cancellation/process death/device restart. */
    suspend fun recoverPendingRestore(): Boolean = withContext(Dispatchers.IO) {
        restoreMutex.withLock { recoverPendingRestoreLocked() }
    }

    private suspend fun recoverPendingRestoreLocked(
        pendingOverride: PendingRestoreJournal? = null,
    ): Boolean {
        val pending = pendingOverride ?: loadPendingRestoreJournal() ?: return false
        val actualTokens = pending.workouts.map { expected ->
            dao.getWorkout(expected.id)?.sessionToken
        }
        val roomCommitted = pending.workouts.indices.all { index ->
            actualTokens[index] == pending.workouts[index].sessionToken
        }
        if (roomCommitted) {
            pending.targetSettings?.let { settingsRepository.restore(it) }
            clearPendingRestoreJournal()
            return true
        }

        if (actualTokens.all { it == null }) {
            clearPendingRestoreJournal()
            return false
        }

        // Room restore is transactional, so a partial/mismatched set is not a state we can safely
        // infer. Keep the journal for diagnosis instead of overwriting user settings or data.
        error("Незавершённое восстановление имеет несогласованное состояние БД")
    }

    private fun writePendingRestoreJournal(pending: PendingRestoreJournal) {
        val bytes = pending.toJson().toString().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_RESTORE_JOURNAL_BYTES) { "Журнал восстановления слишком большой" }
        val atomic = AtomicFile(restoreJournalFile)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun loadPendingRestoreJournal(): PendingRestoreJournal? {
        if (!restoreJournalFile.exists()) return null
        val bytes = AtomicFile(restoreJournalFile).openRead().use { input ->
            input.readBytes().also {
                require(it.size <= MAX_RESTORE_JOURNAL_BYTES) { "Журнал восстановления слишком большой" }
            }
        }
        return runCatching {
            JSONObject(String(bytes, StandardCharsets.UTF_8)).toPendingRestoreJournal()
        }.getOrElse { throw IllegalStateException("Повреждён внутренний журнал восстановления", it) }
    }

    private fun clearPendingRestoreJournal() {
        AtomicFile(restoreJournalFile).delete()
    }

    private fun PendingRestoreJournal.toJson() = JSONObject().apply {
        put("version", RESTORE_JOURNAL_VERSION)
        put("backupSha256", backupSha256)
        targetSettings?.let { put("targetSettings", it.toJson()) }
        put("workouts", JSONArray().apply {
            workouts.forEach { identity ->
                put(JSONObject().apply {
                    put("id", identity.id)
                    put("sessionToken", identity.sessionToken)
                })
            }
        })
    }

    private fun JSONObject.toPendingRestoreJournal(): PendingRestoreJournal {
        check(getInt("version") == RESTORE_JOURNAL_VERSION) { "Неподдерживаемая версия журнала восстановления" }
        val fingerprint = getString("backupSha256").lowercase()
        require(fingerprint.matches(Regex("[0-9a-f]{64}"))) { "Некорректный fingerprint журнала восстановления" }
        val array = optJSONArray("workouts") ?: JSONArray()
        val workouts = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            RestoreWorkoutIdentity(
                id = item.getString("id").also { require(it.isNotBlank() && it.length <= 128) },
                sessionToken = item.getString("sessionToken").also { require(it.isNotBlank() && it.length <= 128) },
            )
        }
        require(workouts.map { it.id }.distinct().size == workouts.size) { "Журнал восстановления содержит повторяющиеся workout id" }
        return PendingRestoreJournal(
            backupSha256 = fingerprint,
            targetSettings = optJSONObject("targetSettings")?.toSettings(),
            workouts = workouts,
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

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
        stepCount?.let { put("stepCount", it) }
        put("stepTrackingReliable", stepTrackingReliable)
        title?.let { put("title", it) }; note?.let { put("note", it) }
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toWorkout(): WorkoutEntity {
        val id = getString("id").also { require(it.isNotBlank() && it.length <= 128) }
        val type = getString("type").also { require(it in setOf("RUN", "WALK", "BIKE")) }
        val startedAt = getLong("startedAt").also {
            require(it >= 0L) { "Некорректное время начала тренировки" }
        }
        val rawEndedAt = if (has("endedAt") && !isNull("endedAt")) getLong("endedAt") else null
        val distance = getDouble("distanceMeters").also { require(it.isFinite() && it >= 0) }
        val elapsed = getLong("elapsedMillis").also { require(it >= 0L) }
        val moving = getLong("movingMillis").also { require(it in 0L..elapsed) }
        // Older RunTrack versions could persist endedAt < startedAt after a backwards wall-clock
        // adjustment. Such backups are normalized from the authoritative monotonic duration.
        val endedAt = rawEndedAt?.let {
            WorkoutTime.resolveEndMillis(startedAt, it, elapsed)
        }
        val status = getString("status").also { require(it == WorkoutStatus.COMPLETED.name) }
        val restoredStepCount =
            if (has("stepCount") && !isNull("stepCount")) {
                getLong("stepCount").takeIf { it >= 0L }
            } else {
                null
            }
        val restoredStepReliable =
            optBoolean("stepTrackingReliable", false) && restoredStepCount != null
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
            stepCount = restoredStepCount,
            stepTrackingReliable = restoredStepReliable,
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
        private const val BACKUP_VERSION = 4
        private const val MAX_BACKUP_BYTES = 256L * 1024L * 1024L
        private const val RESTORE_JOURNAL_VERSION = 1
        private const val RESTORE_JOURNAL_FILE = "pending_restore_journal_v1.json"
        private const val MAX_RESTORE_JOURNAL_BYTES = 4 * 1024 * 1024
    }
}
