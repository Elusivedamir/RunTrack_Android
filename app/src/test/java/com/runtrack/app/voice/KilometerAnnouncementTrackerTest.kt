package com.runtrack.app.voice

import com.runtrack.app.domain.WorkoutGoal
import com.runtrack.app.domain.WorkoutStatus
import com.runtrack.app.domain.WorkoutType
import com.runtrack.app.tracking.TrackingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class KilometerAnnouncementTrackerTest {
    @Test
    fun `announces each newly completed kilometer once`() {
        val tracker = KilometerAnnouncementTracker()

        assertEquals(emptyList<Int>(), tracker.onSnapshot(snapshot(distance = 0.0)))
        assertEquals(emptyList<Int>(), tracker.onSnapshot(snapshot(distance = 999.9)))
        assertEquals(listOf(1), tracker.onSnapshot(snapshot(distance = 1_000.0)))
        assertEquals(emptyList<Int>(), tracker.onSnapshot(snapshot(distance = 1_500.0)))
        assertEquals(listOf(2), tracker.onSnapshot(snapshot(distance = 2_001.0)))
    }

    @Test
    fun `does not replay old milestones after service or workout baseline`() {
        val tracker = KilometerAnnouncementTracker()

        assertEquals(emptyList<Int>(), tracker.onSnapshot(snapshot(distance = 12_400.0)))
        assertEquals(listOf(13), tracker.onSnapshot(snapshot(distance = 13_010.0)))
        assertEquals(
            emptyList<Int>(),
            tracker.onSnapshot(snapshot(id = "new-workout", distance = 3_500.0)),
        )
        assertEquals(
            listOf(4),
            tracker.onSnapshot(snapshot(id = "new-workout", distance = 4_000.0)),
        )
    }

    @Test
    fun `paused progress is never replayed on resume`() {
        val tracker = KilometerAnnouncementTracker()

        tracker.onSnapshot(snapshot(distance = 900.0))
        assertEquals(
            emptyList<Int>(),
            tracker.onSnapshot(
                snapshot(distance = 1_100.0, status = WorkoutStatus.MANUAL_PAUSED)
            ),
        )
        assertEquals(
            emptyList<Int>(),
            tracker.onSnapshot(snapshot(distance = 1_100.0, status = WorkoutStatus.ACTIVE)),
        )
        assertEquals(
            listOf(2),
            tracker.onSnapshot(snapshot(distance = 2_000.0, status = WorkoutStatus.ACTIVE)),
        )
    }

    @Test
    fun `pathological jump emits only newest milestone instead of an unbounded backlog`() {
        val tracker = KilometerAnnouncementTracker()

        tracker.onSnapshot(snapshot(distance = 0.0))
        assertEquals(listOf(100), tracker.onSnapshot(snapshot(distance = 100_000.0)))
        assertEquals(listOf(101), tracker.onSnapshot(snapshot(distance = 101_000.0)))
    }

    private fun snapshot(
        id: String = "workout",
        distance: Double,
        status: WorkoutStatus = WorkoutStatus.ACTIVE,
    ) = TrackingSnapshot(
        workoutId = id,
        type = WorkoutType.RUN,
        goal = WorkoutGoal(),
        goalReached = false,
        status = status,
        startedAtMillis = 1L,
        startedElapsedRealtimeMillis = 1L,
        endedAtMillis = null,
        elapsedMillis = 0L,
        movingMillis = 0L,
        distanceMeters = distance,
        averageSpeedMps = 0.0,
        caloriesEstimate = 0,
        routePointCount = 0,
        lastAccuracyMeters = null,
        lastLocationAtMillis = null,
        gpsAvailable = true,
        lastLocationMonotonicMillis = null,
    )
}
