package com.runtrack.prototype.domain

import java.time.Instant
import java.util.Locale
import kotlin.math.max

/** Route point enriched with authoritative moving time for pause-safe split calculations. */
data class TimedRoutePoint(
    val sample: LocationSample,
    val movingElapsedMillis: Long,
    val segmentIndex: Int = 0,
)

data class DistanceSplit(
    val index: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val paceSecondsPerKm: Double?,
)

object SplitCalculator {
    fun splits(points: List<TimedRoutePoint>, splitMeters: Double): List<DistanceSplit> {
        if (splitMeters <= 0.0 || points.size < 2) return emptyList()
        val valid = points.filterIndexed { index, p ->
            p.movingElapsedMillis >= 0L && (index == 0 || p.movingElapsedMillis >= points[index - 1].movingElapsedMillis)
        }
        if (valid.size < 2) return emptyList()

        val cumulative = DoubleArray(valid.size)
        for (i in 1 until valid.size) {
            val segmentDistance = if (valid[i - 1].segmentIndex == valid[i].segmentIndex) {
                Geo.distanceMeters(valid[i - 1].sample, valid[i].sample)
            } else 0.0
            cumulative[i] = cumulative[i - 1] + segmentDistance
        }
        val total = cumulative.last()
        if (total <= 0.0) return emptyList()

        val result = mutableListOf<DistanceSplit>()
        var boundary = splitMeters
        var splitStartDistance = 0.0
        var splitStartTime = valid.first().movingElapsedMillis.toDouble()
        var i = 1
        while (boundary <= total + 1e-6) {
            while (i < cumulative.size && cumulative[i] < boundary) i++
            if (i >= cumulative.size) break
            val d0 = cumulative[i - 1]
            val d1 = cumulative[i]
            val t0 = valid[i - 1].movingElapsedMillis.toDouble()
            val t1 = valid[i].movingElapsedMillis.toDouble()
            val fraction = if (d1 > d0) ((boundary - d0) / (d1 - d0)).coerceIn(0.0, 1.0) else 0.0
            val boundaryTime = t0 + (t1 - t0) * fraction
            val distance = boundary - splitStartDistance
            val duration = max(0.0, boundaryTime - splitStartTime).toLong()
            val pace = if (distance > 0 && duration > 0) (duration / 1000.0) / (distance / 1000.0) else null
            result += DistanceSplit(result.size + 1, distance, duration, pace)
            splitStartDistance = boundary
            splitStartTime = boundaryTime
            boundary += splitMeters
        }

        if (total - splitStartDistance > 1.0) {
            val endTime = valid.last().movingElapsedMillis.toDouble()
            val distance = total - splitStartDistance
            val duration = max(0.0, endTime - splitStartTime).toLong()
            val pace = if (distance > 0 && duration > 0) (duration / 1000.0) / (distance / 1000.0) else null
            result += DistanceSplit(result.size + 1, distance, duration, pace)
        }
        return result
    }
}

data class WorkoutSummary(
    val id: String,
    val type: WorkoutType,
    val startedAtMillis: Long,
    val distanceMeters: Double,
    val elapsedMillis: Long,
    val movingMillis: Long,
    val calories: Int,
    val elevationGainMeters: Double?,
    val averageSpeedMps: Double,
)

data class PeriodStats(
    val workouts: Int,
    val distanceMeters: Double,
    val elapsedMillis: Long,
    val movingMillis: Long,
    val calories: Int,
    val byTypeDistanceMeters: Map<WorkoutType, Double>,
)

object StatisticsCalculator {
    fun aggregate(workouts: List<WorkoutSummary>): PeriodStats = PeriodStats(
        workouts = workouts.size,
        distanceMeters = workouts.sumOf { it.distanceMeters.coerceAtLeast(0.0) },
        elapsedMillis = workouts.sumOf { it.elapsedMillis.coerceAtLeast(0) },
        movingMillis = workouts.sumOf { it.movingMillis.coerceAtLeast(0) },
        calories = workouts.sumOf { it.calories.coerceAtLeast(0) },
        byTypeDistanceMeters = WorkoutType.entries.associateWith { type ->
            workouts.asSequence().filter { it.type == type }.sumOf { it.distanceMeters.coerceAtLeast(0.0) }
        },
    )

    fun percentChange(current: Double, previous: Double): Double? {
        if (!current.isFinite() || !previous.isFinite()) return null
        if (previous == 0.0) return if (current == 0.0) 0.0 else null
        return ((current - previous) / kotlin.math.abs(previous)) * 100.0
    }
}

data class RecordResult(
    val workoutId: String,
    val value: Double,
)

object RecordCalculator {
    fun longestRun(workouts: List<WorkoutSummary>): RecordResult? = workouts
        .asSequence()
        .filter { it.type == WorkoutType.RUN && it.distanceMeters > 0.0 }
        .maxByOrNull { it.distanceMeters }
        ?.let { RecordResult(it.id, it.distanceMeters) }

    fun maxElevationGain(workouts: List<WorkoutSummary>): RecordResult? = workouts
        .asSequence()
        .mapNotNull { w -> w.elevationGainMeters?.takeIf { it.isFinite() && it > 0.0 }?.let { RecordResult(w.id, it) } }
        .maxByOrNull { it.value }

    /** Returns best moving-time duration for a contiguous route distance window. */
    fun bestDistanceWindowMillis(points: List<TimedRoutePoint>, windowMeters: Double): Long? {
        if (windowMeters <= 0.0 || points.size < 2) return null
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val segmentDistance = if (points[i - 1].segmentIndex == points[i].segmentIndex) {
                Geo.distanceMeters(points[i - 1].sample, points[i].sample)
            } else 0.0
            cumulative[i] = cumulative[i - 1] + segmentDistance
        }
        if (cumulative.last() + 1e-6 < windowMeters) return null

        var best: Double? = null
        var startIndex = 0
        for (end in 1 until points.size) {
            val targetStartDistance = cumulative[end] - windowMeters
            if (targetStartDistance < 0.0) continue
            while (startIndex + 1 < end && cumulative[startIndex + 1] <= targetStartDistance) startIndex++
            val d0 = cumulative[startIndex]
            val d1 = cumulative[startIndex + 1]
            val t0 = points[startIndex].movingElapsedMillis.toDouble()
            val t1 = points[startIndex + 1].movingElapsedMillis.toDouble()
            val fraction = if (d1 > d0) ((targetStartDistance - d0) / (d1 - d0)).coerceIn(0.0, 1.0) else 0.0
            val startTime = t0 + (t1 - t0) * fraction
            val endTime = points[end].movingElapsedMillis.toDouble()
            val duration = endTime - startTime
            if (duration > 0.0 && (best == null || duration < best)) best = duration
        }
        return best?.toLong()
    }
}

data class ExportWorkout(
    val id: String,
    val type: WorkoutType,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val distanceMeters: Double,
    val movingMillis: Long,
)

object WorkoutExporter {
    fun gpx(workout: ExportWorkout, points: List<TimedRoutePoint>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"RunTrack\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <metadata><time>${Instant.ofEpochMilli(workout.startedAtMillis)}</time></metadata>\n")
        append("  <trk><name>RunTrack ${xml(workout.type.name)}</name>\n")
        points.groupBy { it.segmentIndex }.toSortedMap().values.forEach { segment ->
            append("    <trkseg>\n")
            segment.sortedBy { it.movingElapsedMillis }.forEach { timed ->
                val p = timed.sample
                append("      <trkpt lat=\"")
                append(String.format(Locale.US, "%.7f", p.latitude))
                append("\" lon=\"")
                append(String.format(Locale.US, "%.7f", p.longitude))
                append("\">")
                p.altitudeMeters?.takeIf(Double::isFinite)?.let { append("<ele>${String.format(Locale.US, "%.2f", it)}</ele>") }
                append("<time>${Instant.ofEpochMilli(p.timestampMillis)}</time></trkpt>\n")
            }
            append("    </trkseg>\n")
        }
        append("  </trk>\n</gpx>\n")
    }

    fun csv(workout: ExportWorkout, points: List<TimedRoutePoint>): String = buildString {
        append("workout_id,type,timestamp_utc,moving_elapsed_ms,segment_index,latitude,longitude,accuracy_m,altitude_m,speed_mps,bearing_deg,provider\n")
        points.forEach { p ->
            val s = p.sample
            val values = listOf(
                workout.id,
                workout.type.name,
                Instant.ofEpochMilli(s.timestampMillis).toString(),
                p.movingElapsedMillis.toString(),
                p.segmentIndex.toString(),
                String.format(Locale.US, "%.7f", s.latitude),
                String.format(Locale.US, "%.7f", s.longitude),
                String.format(Locale.US, "%.2f", s.accuracyMeters),
                s.altitudeMeters?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                s.speedMps?.takeIf(Float::isFinite)?.let { String.format(Locale.US, "%.3f", it) } ?: "",
                s.bearingDegrees?.takeIf(Float::isFinite)?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                s.provider ?: "",
            )
            append(values.joinToString(",") { csvCell(it) }).append('\n')
        }
    }

    private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun csvCell(value: String): String = if (value.any { it == ',' || it == '\"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else value
}
