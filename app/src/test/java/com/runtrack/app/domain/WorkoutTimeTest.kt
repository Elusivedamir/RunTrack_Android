package com.runtrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTimeTest {
    @Test fun validObservedWallClockEndIsPreserved() {
        assertEquals(
            2_000L,
            WorkoutTime.resolveEndMillis(
                startedAtMillis = 1_000L,
                observedEndMillis = 2_000L,
                elapsedMillis = 700L,
            )
        )
    }

    @Test fun backwardsWallClockFallsBackToMonotonicElapsedDuration() {
        assertEquals(
            1_601_000L,
            WorkoutTime.resolveEndMillis(
                startedAtMillis = 1_000L,
                observedEndMillis = 500L,
                elapsedMillis = 1_600_000L,
            )
        )
    }

    @Test fun fallbackSaturatesInsteadOfOverflowing() {
        assertEquals(
            Long.MAX_VALUE,
            WorkoutTime.resolveEndMillis(
                startedAtMillis = Long.MAX_VALUE - 10L,
                observedEndMillis = 0L,
                elapsedMillis = 100L,
            )
        )
    }
}
