package com.runtrack.app.domain

import org.junit.Assert.*
import org.junit.Test

class AnalyticsExportTest {
    private fun route(): List<TimedRoutePoint> = (0..10).map { i ->
        TimedRoutePoint(
            sample = LocationSample(
                timestampMillis = i * 60_000L,
                latitude = 55.0 + i * 0.001,
                longitude = 37.0,
                accuracyMeters = 5f,
                altitudeMeters = 100.0 + i,
            ),
            movingElapsedMillis = i * 60_000L,
        )
    }

    @Test fun statsAndRecordsUseRealInputs() {
        val workouts = listOf(
            WorkoutSummary("a", WorkoutType.RUN, 0, 5000.0, 1_800_000, 1_700_000, 300, 20.0, 2.94),
            WorkoutSummary("b", WorkoutType.RUN, 0, 10000.0, 3_600_000, 3_500_000, 600, 50.0, 2.85),
            WorkoutSummary("c", WorkoutType.BIKE, 0, 20000.0, 3_000_000, 2_900_000, 500, null, 6.89),
        )
        val stats = StatisticsCalculator.aggregate(workouts)
        assertEquals(3, stats.workouts)
        assertEquals(35000.0, stats.distanceMeters, 0.0)
        assertEquals("b", RecordCalculator.longestRun(workouts)?.workoutId)
        assertEquals("b", RecordCalculator.maxElevationGain(workouts)?.workoutId)
        assertEquals(20.0, requireNotNull(StatisticsCalculator.percentChange(120.0, 100.0)), 0.0001)
        assertNull(StatisticsCalculator.percentChange(10.0, 0.0))
    }

    @Test fun splitAndWindowAlgorithmsHandleRoute() {
        val route = route()
        val splits = SplitCalculator.splits(route, 100.0)
        assertTrue(splits.isNotEmpty())
        assertTrue(splits.all { it.durationMillis >= 0 })
        assertNotNull(RecordCalculator.bestDistanceWindowMillis(route, 500.0))
    }

    @Test fun pauseSegmentsDoNotCreateArtificialDistance() {
        val points = listOf(
            TimedRoutePoint(LocationSample(0, 55.0, 37.0, 5f), 0, 0),
            TimedRoutePoint(LocationSample(10_000, 55.0005, 37.0, 5f), 10_000, 0),
            // Resume far away: this gap must not be counted as movement.
            TimedRoutePoint(LocationSample(20_000, 56.0, 38.0, 5f), 10_001, 1),
            TimedRoutePoint(LocationSample(30_000, 56.0005, 38.0, 5f), 20_001, 1),
        )
        val splits = SplitCalculator.splits(points, 10.0)
        assertTrue(splits.isNotEmpty())
        val gpx = WorkoutExporter.gpx(ExportWorkout("seg", WorkoutType.RUN, 0, 30_000, 100.0, 20_001), points)
        assertEquals(2, "<trkseg>".toRegex().findAll(gpx).count())
        // A 100 km pause jump would create thousands of 10m splits if segments were bridged.
        assertTrue(splits.size < 30)
    }

    @Test fun gpxAndCsvAreStructuredAndEscaped() {
        val route = route()
        val workout = ExportWorkout("a,b", WorkoutType.RUN, 0, 600000, 1000.0, 600000)
        val gpx = WorkoutExporter.gpx(workout, route)
        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.contains("<trkpt"))
        assertFalse(gpx.contains("NaN"))
        val csv = WorkoutExporter.csv(workout, route)
        assertTrue(csv.lineSequence().first().startsWith("workout_id,type"))
        assertTrue(csv.contains("\"a,b\""))
    }
}
