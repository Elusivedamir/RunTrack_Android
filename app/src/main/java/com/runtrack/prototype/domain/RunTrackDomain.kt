package com.runtrack.prototype.domain

import kotlin.math.*

enum class WorkoutType { RUN, WALK, BIKE }
enum class WorkoutStatus { IDLE, PREPARING, ACTIVE, MANUAL_PAUSED, AUTO_PAUSED, FINISHING, COMPLETED, RECOVERY_REQUIRED, FAILED }
enum class GoalKind { NONE, DISTANCE, DURATION }
enum class UnitSystem { METRIC, IMPERIAL }
enum class MapLayer { STANDARD, TERRAIN }

data class WorkoutGoal(
    val kind: GoalKind = GoalKind.NONE,
    val distanceMeters: Double? = null,
    val durationMillis: Long? = null,
) {
    fun isValid(): Boolean = when (kind) {
        GoalKind.NONE -> true
        GoalKind.DISTANCE -> (distanceMeters ?: 0.0) > 0.0 && distanceMeters!!.isFinite()
        GoalKind.DURATION -> (durationMillis ?: 0L) > 0L
    }
}

/**
 * timestampMillis is UTC/wall-clock time for persistence/export.
 * monotonicMillis is elapsed-realtime time used for live ordering/speed calculations.
 * Wall clock must never be used for an active-session duration when monotonic time is available.
 */
data class LocationSample(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
    val provider: String? = null,
    val monotonicMillis: Long? = null,
)

data class GpsFilterPolicy(
    val maxAccuracyMeters: Float,
    val maxSpeedMps: Double,
    val maxJumpMeters: Double,
    val duplicateDistanceMeters: Double,
    val minTimestampDeltaMillis: Long,
) {
    companion object {
        fun forType(type: WorkoutType): GpsFilterPolicy = when (type) {
            WorkoutType.RUN -> GpsFilterPolicy(35f, 12.0, 180.0, 1.2, 250)
            WorkoutType.WALK -> GpsFilterPolicy(40f, 6.5, 120.0, 1.0, 250)
            WorkoutType.BIKE -> GpsFilterPolicy(45f, 30.0, 350.0, 2.0, 200)
        }
    }
}

sealed interface GpsValidation {
    data object Accepted : GpsValidation
    data class Rejected(val reason: String) : GpsValidation
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMeters(a: LocationSample, b: LocationSample): Double = distanceMeters(
        a.latitude, a.longitude, b.latitude, b.longitude
    )

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2.0 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

class GpsPointFilter(private val policy: GpsFilterPolicy) {
    private var lastAccepted: LocationSample? = null

    fun reset() { lastAccepted = null }

    /** Pure validation. Call [accept] only after the point has been durably persisted. */
    fun evaluate(sample: LocationSample): GpsValidation {
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite()) return GpsValidation.Rejected("non_finite_coordinate")
        if (sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0) return GpsValidation.Rejected("coordinate_range")
        if (!sample.accuracyMeters.isFinite() || sample.accuracyMeters <= 0f) return GpsValidation.Rejected("invalid_accuracy")
        if (sample.accuracyMeters > policy.maxAccuracyMeters) return GpsValidation.Rejected("poor_accuracy")

        val previous = lastAccepted
        if (previous != null) {
            val currentClock = sample.monotonicMillis ?: sample.timestampMillis
            val previousClock = previous.monotonicMillis ?: previous.timestampMillis
            val dt = currentClock - previousClock
            if (dt < policy.minTimestampDeltaMillis) return GpsValidation.Rejected("non_monotonic_or_too_frequent")
            val distance = Geo.distanceMeters(previous, sample)
            if (distance < policy.duplicateDistanceMeters) return GpsValidation.Rejected("duplicate")
            if (distance > policy.maxJumpMeters) return GpsValidation.Rejected("impossible_jump")
            val impliedSpeed = distance / (dt / 1000.0)
            if (!impliedSpeed.isFinite() || impliedSpeed > policy.maxSpeedMps) return GpsValidation.Rejected("impossible_speed")
        }
        val providerSpeed = sample.speedMps?.toDouble()
        if (providerSpeed != null && providerSpeed.isFinite() && providerSpeed > policy.maxSpeedMps) {
            return GpsValidation.Rejected("provider_speed")
        }
        return GpsValidation.Accepted
    }

    fun accept(sample: LocationSample) { lastAccepted = sample }

    /** Convenience for pure-domain callers where persistence cannot fail. */
    fun validate(sample: LocationSample): GpsValidation {
        val result = evaluate(sample)
        if (result is GpsValidation.Accepted) accept(sample)
        return result
    }
}

data class WorkoutMetrics(
    val distanceMeters: Double,
    val elapsedMillis: Long,
    val movingMillis: Long,
    val averageSpeedMps: Double,
    val paceSecondsPerKm: Double?,
)

object WorkoutMath {
    fun metrics(distanceMeters: Double, elapsedMillis: Long, movingMillis: Long): WorkoutMetrics {
        val safeDistance = distanceMeters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeElapsed = elapsedMillis.coerceAtLeast(0)
        val safeMoving = movingMillis.coerceIn(0, safeElapsed)
        val speed = if (safeMoving > 0L) safeDistance / (safeMoving / 1000.0) else 0.0
        val pace = if (safeDistance >= 1.0 && safeMoving > 0L && speed > 0.0) {
            (safeMoving / 1000.0) / (safeDistance / 1000.0)
        } else null
        return WorkoutMetrics(safeDistance, safeElapsed, safeMoving, speed.takeIf { it.isFinite() } ?: 0.0, pace?.takeIf { it.isFinite() && it > 0 })
    }

    fun estimatedCalories(type: WorkoutType, movingMillis: Long, distanceMeters: Double, weightKg: Double?): Int {
        val hours = movingMillis.coerceAtLeast(0) / 3_600_000.0
        if (hours <= 0.0) return 0
        val weight = weightKg?.takeIf { it.isFinite() && it in 30.0..300.0 } ?: 70.0
        val km = distanceMeters.coerceAtLeast(0.0) / 1000.0
        val met = when (type) {
            WorkoutType.RUN -> (km / hours).let { if (it >= 10) 10.0 else 8.3 }
            WorkoutType.WALK -> (km / hours).let { if (it >= 5.5) 4.3 else 3.5 }
            WorkoutType.BIKE -> (km / hours).let { if (it >= 20) 8.0 else 6.8 }
        }
        return (met * weight * hours).roundToInt().coerceAtLeast(0)
    }

    fun elevationGainLoss(samples: List<LocationSample>, minStepMeters: Double = 2.5): Pair<Double, Double>? {
        val altitudes = samples.mapNotNull { it.altitudeMeters?.takeIf(Double::isFinite) }
        if (altitudes.size < 2) return null
        var gain = 0.0
        var loss = 0.0
        var previous = altitudes.first()
        for (current in altitudes.drop(1)) {
            val delta = current - previous
            if (abs(delta) >= minStepMeters) {
                if (delta > 0) gain += delta else loss += -delta
                previous = current
            }
        }
        return gain to loss
    }

    /** Pause-safe elevation aggregation: a new route segment never creates synthetic gain/loss. */
    fun elevationGainLossSegments(segments: List<List<LocationSample>>, minStepMeters: Double = 2.5): Pair<Double, Double>? {
        val values = segments.mapNotNull { elevationGainLoss(it, minStepMeters) }
        if (values.isEmpty()) return null
        return values.sumOf { it.first } to values.sumOf { it.second }
    }
}

data class AutoPausePolicy(
    val pauseBelowMps: Double,
    val resumeAboveMps: Double,
    val pauseAfterMillis: Long,
    val resumeAfterMillis: Long,
    val maxAccuracyMeters: Float,
    val maxPlausibleSpeedMps: Double,
) {
    companion object {
        fun forType(type: WorkoutType): AutoPausePolicy = when (type) {
            WorkoutType.RUN -> AutoPausePolicy(0.55, 1.05, 8_000, 3_000, 35f, 12.0)
            WorkoutType.WALK -> AutoPausePolicy(0.30, 0.70, 10_000, 3_500, 40f, 6.5)
            WorkoutType.BIKE -> AutoPausePolicy(1.10, 2.00, 10_000, 3_000, 45f, 30.0)
        }
    }
}

sealed interface AutoPauseEvent {
    data object None : AutoPauseEvent
    data object Pause : AutoPauseEvent
    data object Resume : AutoPauseEvent
}

class AutoPauseController(private val policy: AutoPausePolicy) {
    private var belowSince: Long? = null
    private var aboveSince: Long? = null
    private var autoPaused = false

    fun reset() { belowSince = null; aboveSince = null; autoPaused = false }
    fun restoreAutoPaused(value: Boolean) { belowSince = null; aboveSince = null; autoPaused = value }

    fun update(monotonicMillis: Long, speedMps: Double?, accuracyMeters: Float, manualPaused: Boolean): AutoPauseEvent {
        if (manualPaused) {
            belowSince = null
            aboveSince = null
            return AutoPauseEvent.None
        }
        if (!accuracyMeters.isFinite() || accuracyMeters > policy.maxAccuracyMeters || speedMps == null || !speedMps.isFinite() || speedMps < 0 || speedMps > policy.maxPlausibleSpeedMps) {
            belowSince = null
            aboveSince = null
            return AutoPauseEvent.None
        }

        if (!autoPaused) {
            aboveSince = null
            if (speedMps <= policy.pauseBelowMps) {
                val start = belowSince ?: monotonicMillis.also { belowSince = it }
                if (monotonicMillis - start >= policy.pauseAfterMillis) {
                    autoPaused = true
                    belowSince = null
                    return AutoPauseEvent.Pause
                }
            } else {
                belowSince = null
            }
            return AutoPauseEvent.None
        }

        belowSince = null
        if (speedMps >= policy.resumeAboveMps) {
            val start = aboveSince ?: monotonicMillis.also { aboveSince = it }
            if (monotonicMillis - start >= policy.resumeAfterMillis) {
                autoPaused = false
                aboveSince = null
                return AutoPauseEvent.Resume
            }
        } else {
            aboveSince = null
        }
        return AutoPauseEvent.None
    }
}

class WorkoutStateMachine(initial: WorkoutStatus = WorkoutStatus.IDLE) {
    var state: WorkoutStatus = initial
        private set

    fun canTransition(target: WorkoutStatus): Boolean {
        if (target == state) return true
        val allowed = when (state) {
            WorkoutStatus.IDLE -> setOf(WorkoutStatus.PREPARING)
            WorkoutStatus.PREPARING -> setOf(WorkoutStatus.ACTIVE, WorkoutStatus.FAILED, WorkoutStatus.RECOVERY_REQUIRED)
            WorkoutStatus.ACTIVE -> setOf(WorkoutStatus.MANUAL_PAUSED, WorkoutStatus.AUTO_PAUSED, WorkoutStatus.FINISHING, WorkoutStatus.RECOVERY_REQUIRED)
            WorkoutStatus.MANUAL_PAUSED -> setOf(WorkoutStatus.ACTIVE, WorkoutStatus.FINISHING, WorkoutStatus.RECOVERY_REQUIRED)
            WorkoutStatus.AUTO_PAUSED -> setOf(WorkoutStatus.ACTIVE, WorkoutStatus.MANUAL_PAUSED, WorkoutStatus.FINISHING, WorkoutStatus.RECOVERY_REQUIRED)
            WorkoutStatus.FINISHING -> setOf(WorkoutStatus.COMPLETED, WorkoutStatus.RECOVERY_REQUIRED, WorkoutStatus.FAILED)
            WorkoutStatus.RECOVERY_REQUIRED -> setOf(WorkoutStatus.ACTIVE, WorkoutStatus.MANUAL_PAUSED, WorkoutStatus.FINISHING, WorkoutStatus.FAILED)
            WorkoutStatus.COMPLETED, WorkoutStatus.FAILED -> emptySet()
        }
        return target in allowed
    }

    fun transition(target: WorkoutStatus): Boolean {
        if (!canTransition(target)) return false
        state = target
        return true
    }
}

data class NormalizedPoint(val x: Float, val y: Float)

object RouteGeometry {
    /** Render-only decimation. Original accepted RoutePoint rows remain untouched in Room. */
    fun downsampleForRender(points: List<LocationSample>, maxPoints: Int = 1_000): List<LocationSample> {
        require(maxPoints >= 2) { "maxPoints must be >= 2" }
        if (points.size <= maxPoints) return points
        val lastIndex = points.lastIndex
        return buildList(maxPoints) {
            for (i in 0 until maxPoints) {
                val index = ((i.toLong() * lastIndex) / (maxPoints - 1)).toInt()
                add(points[index])
            }
        }
    }

    /** Normalize multiple route segments against one shared bounding box without connecting them. */
    fun normalizeRoutes(routes: List<List<LocationSample>>, width: Float, height: Float, padding: Float): List<List<NormalizedPoint>> {
        if (width <= 0f || height <= 0f) return routes.map { emptyList() }
        val all = routes.flatten()
        if (all.isEmpty()) return routes.map { emptyList() }
        if (all.size == 1) {
            val only = NormalizedPoint(width / 2f, height / 2f)
            return routes.map { route -> route.map { only } }
        }

        val meanLat = all.map { it.latitude }.average()
        val cosLat = cos(Math.toRadians(meanLat)).coerceAtLeast(0.01)
        fun projected(p: LocationSample) = (p.longitude * cosLat) to p.latitude
        val projectedAll = all.map(::projected)
        val minX = projectedAll.minOf { it.first }
        val maxX = projectedAll.maxOf { it.first }
        val minY = projectedAll.minOf { it.second }
        val maxY = projectedAll.maxOf { it.second }
        val spanX = (maxX - minX).coerceAtLeast(1e-12)
        val spanY = (maxY - minY).coerceAtLeast(1e-12)
        val availableW = (width - padding * 2f).coerceAtLeast(1f)
        val availableH = (height - padding * 2f).coerceAtLeast(1f)
        val scale = min(availableW / spanX, availableH / spanY)
        val usedW = spanX * scale
        val usedH = spanY * scale
        val offsetX = (width - usedW) / 2.0
        val offsetY = (height - usedH) / 2.0
        return routes.map { route ->
            route.map { point ->
                val (x, y) = projected(point)
                NormalizedPoint(
                    x = (offsetX + (x - minX) * scale).toFloat(),
                    y = (height - (offsetY + (y - minY) * scale)).toFloat(),
                )
            }
        }
    }

    fun normalize(points: List<LocationSample>, width: Float, height: Float, padding: Float): List<NormalizedPoint> {
        if (points.isEmpty() || width <= 0f || height <= 0f) return emptyList()
        if (points.size == 1) return listOf(NormalizedPoint(width / 2f, height / 2f))

        val meanLat = points.map { it.latitude }.average()
        val cosLat = cos(Math.toRadians(meanLat)).coerceAtLeast(0.01)
        val raw = points.map { p -> (p.longitude * cosLat) to p.latitude }
        val minX = raw.minOf { it.first }
        val maxX = raw.maxOf { it.first }
        val minY = raw.minOf { it.second }
        val maxY = raw.maxOf { it.second }
        val spanX = (maxX - minX).coerceAtLeast(1e-12)
        val spanY = (maxY - minY).coerceAtLeast(1e-12)
        val availableW = (width - padding * 2f).coerceAtLeast(1f)
        val availableH = (height - padding * 2f).coerceAtLeast(1f)
        val scale = min(availableW / spanX, availableH / spanY)
        val usedW = spanX * scale
        val usedH = spanY * scale
        val offsetX = (width - usedW) / 2.0
        val offsetY = (height - usedH) / 2.0
        return raw.map { (x, y) ->
            NormalizedPoint(
                x = (offsetX + (x - minX) * scale).toFloat(),
                y = (height - (offsetY + (y - minY) * scale)).toFloat(),
            )
        }
    }
}
