package com.runtrack.prototype.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.core.content.FileProvider
import com.runtrack.prototype.data.WorkoutWithRoute
import com.runtrack.prototype.domain.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

enum class ExportFormat(val extension: String, val mimeType: String) {
    IMAGE("png", "image/png"),
    GPX("gpx", "application/gpx+xml"),
    CSV("csv", "text/csv"),
}

data class ExportOptions(
    val includeRoute: Boolean = true,
    val includeSplits: Boolean = true,
    val units: UnitSystem = UnitSystem.METRIC,
)

data class ExportPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

class WorkoutExportManager(private val context: Context) {
    fun build(relation: WorkoutWithRoute, format: ExportFormat, options: ExportOptions): ExportPayload {
        val type = WorkoutType.valueOf(relation.workout.type)
        val ended = relation.workout.endedAt ?: relation.workout.updatedAt
        val workout = ExportWorkout(
            id = relation.workout.id,
            type = type,
            startedAtMillis = relation.workout.startedAt,
            endedAtMillis = ended,
            distanceMeters = relation.workout.distanceMeters,
            movingMillis = relation.workout.movingMillis,
        )
        val timed = relation.route.sortedBy { it.movingElapsedMillis }.map { point ->
            TimedRoutePoint(
                sample = LocationSample(
                    timestampMillis = point.timestampMillis,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    accuracyMeters = point.accuracyMeters,
                    altitudeMeters = point.altitudeMeters,
                    speedMps = point.speedMps,
                    bearingDegrees = point.bearingDegrees,
                    provider = point.provider,
                    monotonicMillis = point.elapsedRealtimeMillis,
                ),
                movingElapsedMillis = point.movingElapsedMillis,
                segmentIndex = point.segmentIndex,
            )
        }
        val stem = "runtrack_${Instant.ofEpochMilli(relation.workout.startedAt).toString().replace(':', '-')}"
        val bytes = when (format) {
            ExportFormat.GPX -> WorkoutExporter.gpx(workout, timed).toByteArray(Charsets.UTF_8)
            ExportFormat.CSV -> csvWithOptionalSplits(workout, timed, options).toByteArray(Charsets.UTF_8)
            ExportFormat.IMAGE -> renderPng(relation, timed, options)
        }
        return ExportPayload("$stem.${format.extension}", format.mimeType, bytes)
    }

    fun createShareIntent(payload: ExportPayload): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { existing -> if (existing.isFile && existing.name != payload.fileName) existing.delete() }
        val file = File(dir, payload.fileName)
        file.writeBytes(payload.bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = payload.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun writeToUri(uri: Uri, payload: ExportPayload) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "w")?.use { output ->
            output.write(payload.bytes)
            output.flush()
        } ?: error("Не удалось открыть выбранный файл")
    }

    fun clearTemporaryExports() {
        File(context.cacheDir, "exports").deleteRecursively()
    }

    private fun csvWithOptionalSplits(workout: ExportWorkout, timed: List<TimedRoutePoint>, options: ExportOptions): String {
        val base = WorkoutExporter.csv(workout, timed)
        if (!options.includeSplits) return base
        val splitMeters = if (options.units == UnitSystem.METRIC) 1000.0 else 1609.344
        val splits = SplitCalculator.splits(timed, splitMeters)
        if (splits.isEmpty()) return base
        return buildString {
            append(base)
            append("\n# splits\n")
            append("split_index,distance_m,duration_ms,pace_sec_per_km\n")
            splits.forEach { split ->
                append(split.index).append(',')
                append(split.distanceMeters).append(',')
                append(split.durationMillis).append(',')
                append(split.paceSecondsPerKm ?: "").append('\n')
            }
        }
    }

    private fun renderPng(relation: WorkoutWithRoute, timed: List<TimedRoutePoint>, options: ExportOptions): ByteArray {
        val width = 1600
        val height = 2000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF07131B.toInt() }
        val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101C25.toInt() }
        val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF62D72F.toInt() }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E9AA3.toInt() }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        white.textSize = 76f
        white.isFakeBoldText = true
        canvas.drawText("RunTrack", 110f, 145f, white)
        muted.textSize = 38f
        muted.isFakeBoldText = false
        canvas.drawText(WorkoutType.valueOf(relation.workout.type).name, 112f, 205f, muted)

        canvas.drawRoundRect(90f, 280f, 1510f, 1360f, 56f, 56f, surface)
        if (options.includeRoute && timed.isNotEmpty()) {
            val rawSegments = timed.groupBy { it.segmentIndex }.toSortedMap().values
                .map { segment ->
                    RouteGeometry.downsampleForRender(
                        segment.sortedBy { it.movingElapsedMillis }.map { it.sample },
                        maxPoints = 5_000,
                    )
                }
            val normalizedSegments = RouteGeometry.normalizeRoutes(rawSegments, 1320f, 940f, 80f)
            val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF62D72F.toInt()
                strokeWidth = 22f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            normalizedSegments.forEach { normalized ->
                if (normalized.size == 1) {
                    canvas.drawCircle(90f + normalized[0].x, 330f + normalized[0].y, 18f, green)
                } else if (normalized.size >= 2) {
                    val path = Path().apply {
                        moveTo(90f + normalized.first().x, 330f + normalized.first().y)
                        normalized.drop(1).forEach { lineTo(90f + it.x, 330f + it.y) }
                    }
                    canvas.drawPath(path, routePaint)
                }
            }
            val first = normalizedSegments.firstOrNull { it.isNotEmpty() }?.firstOrNull()
            val last = normalizedSegments.lastOrNull { it.isNotEmpty() }?.lastOrNull()
            if (first != null) canvas.drawCircle(90f + first.x, 330f + first.y, 20f, green)
            if (last != null) {
                canvas.drawCircle(90f + last.x, 330f + last.y, 25f, green)
                val endInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF07131B.toInt() }
                canvas.drawCircle(90f + last.x, 330f + last.y, 10f, endInner)
            }
        } else {
            muted.textSize = 44f
            canvas.drawText("Маршрут не включён", 510f, 830f, muted)
        }

        val units = options.units
        val metrics = WorkoutMath.metrics(relation.workout.distanceMeters, relation.workout.elapsedMillis, relation.workout.movingMillis)
        white.textSize = 72f
        white.isFakeBoldText = true
        canvas.drawText(RunTrackFormatter.distance(relation.workout.distanceMeters, units), 110f, 1510f, white)
        canvas.drawText(RunTrackFormatter.duration(relation.workout.elapsedMillis), 850f, 1510f, white)
        muted.textSize = 34f
        muted.isFakeBoldText = false
        canvas.drawText("Дистанция", 112f, 1565f, muted)
        canvas.drawText("Время", 852f, 1565f, muted)

        white.textSize = 58f
        val third = if (WorkoutType.valueOf(relation.workout.type) == WorkoutType.BIKE) {
            RunTrackFormatter.speed(metrics.averageSpeedMps, units)
        } else RunTrackFormatter.pace(metrics.paceSecondsPerKm, units)
        canvas.drawText(third, 110f, 1715f, white)
        canvas.drawText("${relation.workout.caloriesEstimate} ккал", 850f, 1715f, white)
        muted.textSize = 34f
        canvas.drawText(if (relation.workout.type == WorkoutType.BIKE.name) "Средняя скорость" else "Средний темп", 112f, 1770f, muted)
        canvas.drawText("Калории · оценка", 852f, 1770f, muted)

        if (options.includeSplits) {
            muted.textSize = 28f
            val splitMeters = if (units == UnitSystem.METRIC) 1000.0 else 1609.344
            val count = SplitCalculator.splits(timed, splitMeters).size
            canvas.drawText("Разбивок: $count", 112f, 1885f, muted)
        }

        val out = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG export failed" }
        bitmap.recycle()
        return out.toByteArray()
    }
}
