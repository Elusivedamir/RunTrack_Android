package com.runtrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsDisplayTest {
    private fun workout(id: String, elapsedMillis: Long) = WorkoutSummary(
        id = id,
        type = WorkoutType.RUN,
        startedAtMillis = 0L,
        distanceMeters = 0.0,
        elapsedMillis = elapsedMillis,
        movingMillis = elapsedMillis.coerceAtLeast(0L),
        calories = 0,
        elevationGainMeters = null,
        averageSpeedMps = 0.0,
    )

    @Test fun aggregateAddsTheWholeSecondsVisibleOnEachWorkoutCard() {
        val stats = StatisticsCalculator.aggregate(listOf(workout("a", 15_900L), workout("b", 6_900L)))
        assertEquals(21_000L, stats.elapsedMillis)
    }

    @Test fun aggregateKeepsExactWholeSeconds() {
        val stats = StatisticsCalculator.aggregate(listOf(workout("a", 15_000L), workout("b", 6_000L)))
        assertEquals(21_000L, stats.elapsedMillis)
    }

    @Test fun aggregateHandlesZeroAndSeveralWorkouts() {
        assertEquals(0L, StatisticsCalculator.aggregate(listOf(workout("zero", 0L))).elapsedMillis)
        assertEquals(
            6_000L,
            StatisticsCalculator.aggregate(
                listOf(workout("a", 1_999L), workout("b", 2_001L), workout("c", 3_999L))
            ).elapsedMillis,
        )
    }
}
