package com.runtrack.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.kilocalories
import androidx.health.connect.client.units.meters
import com.runtrack.app.data.WorkoutEntity
import com.runtrack.app.domain.WorkoutStatus
import com.runtrack.app.domain.WorkoutType
import java.time.Instant
import java.time.ZoneId

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

internal fun healthConnectEndMillis(startedAtMillis: Long, elapsedMillis: Long): Long {
    require(elapsedMillis > 0L) { "У тренировки некорректная длительность" }
    require(startedAtMillis >= 0L) { "У тренировки некорректное время начала" }
    return if (startedAtMillis > Long.MAX_VALUE - elapsedMillis) {
        Long.MAX_VALUE
    } else {
        startedAtMillis + elapsedMillis
    }
}

data class HealthConnectExportResult(
    val workoutId: String,
    val recordCount: Int,
)

/**
 * Optional, foreground-only Health Connect export.
 *
 * The caller must start every permission request and export from an explicit user action. This
 * class intentionally has no route permission and never creates an ExerciseRoute payload.
 */
class HealthConnectManager(context: Context) {
    private val app = context.applicationContext

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(app)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
        else -> HealthConnectAvailability.UNAVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        if (availability() != HealthConnectAvailability.AVAILABLE) return false
        val granted = client().permissionController.getGrantedPermissions()
        return REQUIRED_PERMISSIONS.all(granted::contains)
    }

    fun settingsOrInstallIntent(): Intent = when (availability()) {
        HealthConnectAvailability.AVAILABLE ->
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        HealthConnectAvailability.UPDATE_REQUIRED ->
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"),
            ).setPackage("com.android.vending")
        HealthConnectAvailability.UNAVAILABLE ->
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${app.packageName}"))
    }

    suspend fun exportWorkout(workout: WorkoutEntity): HealthConnectExportResult {
        check(availability() == HealthConnectAvailability.AVAILABLE) {
            "Health Connect недоступен"
        }
        check(hasAllPermissions()) {
            "Разрешения Health Connect не выданы"
        }
        require(workout.status == WorkoutStatus.COMPLETED.name) {
            "Можно экспортировать только завершённую тренировку"
        }

        val startTime = Instant.ofEpochMilli(workout.startedAt)
        // The workout duration is measured from elapsedRealtime and survives wall-clock changes.
        // Health Connect needs an absolute interval, so derive the end from that monotonic duration
        // instead of trusting endedAt, which can move backwards if the system clock is changed.
        val endMillis = healthConnectEndMillis(workout.startedAt, workout.elapsedMillis)
        val endTime = Instant.ofEpochMilli(endMillis)
        val zoneRules = ZoneId.systemDefault().rules
        val device = Device(type = Device.TYPE_PHONE)
        fun metadata(suffix: String) = Metadata.activelyRecorded(
            device = device,
            clientRecordId = "runtrack:${workout.id}:$suffix",
            clientRecordVersion = workout.updatedAt.coerceAtLeast(0L),
        )

        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = zoneRules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = zoneRules.getOffset(endTime),
                exerciseType = workout.exerciseType(),
                metadata = metadata("session"),
            )
        )
        workout.distanceMeters.takeIf { it.isFinite() && it > 0.0 }?.let { distance ->
            records += DistanceRecord(
                startTime = startTime,
                startZoneOffset = zoneRules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = zoneRules.getOffset(endTime),
                distance = distance.meters,
                metadata = metadata("distance"),
            )
        }
        workout.caloriesEstimate.takeIf { it > 0 }?.let { calories ->
            records += TotalCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = zoneRules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = zoneRules.getOffset(endTime),
                energy = calories.kilocalories,
                metadata = metadata("calories"),
            )
        }

        client().insertRecords(records)
        return HealthConnectExportResult(workout.id, records.size)
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(app)

    private fun WorkoutEntity.exerciseType(): Int = when (
        runCatching { WorkoutType.valueOf(type) }.getOrNull()
    ) {
        WorkoutType.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        WorkoutType.WALK -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        WorkoutType.BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        null -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    companion object {
        private const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        )
    }
}
