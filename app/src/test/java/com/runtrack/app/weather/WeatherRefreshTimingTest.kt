package com.runtrack.app.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshTimingTest {
    @Test
    fun wallClockRollbackDoesNotTreatFutureSnapshotAsFresh() {
        val thirtyMinutes = 30L * 60L * 1000L

        assertNull(
            remainingFreshWeatherMillis(
                nowWallClockMillis = 10L * 60L * 60L * 1000L,
                fetchedAtWallClockMillis = 12L * 60L * 60L * 1000L,
                refreshIntervalMillis = thirtyMinutes,
            )
        )
    }

    @Test
    fun normalPersistedSnapshotReturnsOnlyRemainingFreshTime() {
        val minute = 60_000L

        assertEquals(
            20L * minute,
            remainingFreshWeatherMillis(
                nowWallClockMillis = 1_000_000L + 10L * minute,
                fetchedAtWallClockMillis = 1_000_000L,
                refreshIntervalMillis = 30L * minute,
            )
        )
    }

    @Test
    fun retryGateUsesOnlyMonotonicDeadlines() {
        val gate = WeatherRefreshGate()

        assertTrue(gate.reserve("workout", nowMonotonicMillis = 1_000L, retryDelayMillis = 500L))
        assertFalse(gate.reserve("workout", nowMonotonicMillis = 1_499L, retryDelayMillis = 500L))
        assertTrue(gate.reserve("workout", nowMonotonicMillis = 1_500L, retryDelayMillis = 500L))

        gate.schedule("workout", nowMonotonicMillis = 1_500L, delayMillis = 2_000L)

        assertFalse(gate.reserve("workout", nowMonotonicMillis = 3_499L, retryDelayMillis = 500L))
        assertTrue(gate.reserve("workout", nowMonotonicMillis = 3_500L, retryDelayMillis = 500L))
    }
}
