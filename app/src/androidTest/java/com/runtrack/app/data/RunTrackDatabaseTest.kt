package com.runtrack.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runtrack.app.domain.GoalKind
import com.runtrack.app.domain.WorkoutStatus
import com.runtrack.app.domain.WorkoutType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunTrackDatabaseTest {
    private lateinit var db: RunTrackDatabase
    private lateinit var dao: WorkoutDao

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RunTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workoutDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun workoutRouteAndHeartRateRoundTrip() = runBlocking {
        val workout = workout("w1")
        dao.insertWorkout(workout)
        dao.insertRoutePoint(route("w1", 1L, 1_000L, 0.0))
        dao.insertRoutePoint(route("w1", 2L, 2_000L, 12.0))
        assertTrue(dao.insertHeartRateSample(HeartRateSampleEntity(workoutId = "w1", timestampMillis = 1_500L, elapsedRealtimeMillis = 1_500L, bpm = 142, source = "BLE_HRS")) > 0)

        val relation = requireNotNull(dao.getWorkoutWithRoute("w1"))
        assertEquals(2, relation.route.size)
        assertEquals(1, relation.heartRateSamples.size)
        assertEquals(142, relation.heartRateSamples.single().bpm)
    }

    @Test fun deleteWorkoutCascadesSensitiveChildRows() = runBlocking {
        dao.insertWorkout(workout("w2"))
        dao.insertRoutePoint(route("w2", 1L, 1_000L, 0.0))
        dao.insertHeartRateSample(HeartRateSampleEntity(workoutId = "w2", timestampMillis = 1_000L, elapsedRealtimeMillis = 1_000L, bpm = 130, source = "BLE_HRS"))
        assertTrue(dao.deleteWorkoutTransactional("w2"))

        val sql = db.openHelper.readableDatabase
        sql.query("SELECT COUNT(*) FROM route_points").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
        sql.query("SELECT COUNT(*) FROM heart_rate_samples").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
    }

    @Test fun aggregateUsesOnlyCommittedCompletedRowsInPeriod() = runBlocking {
        dao.insertWorkout(workout("done", status = WorkoutStatus.COMPLETED, startedAt = 10_000L, distance = 5_000.0, elapsed = 1_800_000L))
        dao.insertWorkout(workout("active", status = WorkoutStatus.ACTIVE, startedAt = 11_000L, distance = 99_000.0, elapsed = 9_999_000L))
        val aggregate = dao.aggregateBetween(0L, 20_000L)
        assertEquals(1, aggregate.workouts)
        assertEquals(5_000.0, aggregate.distanceMeters, 0.001)
        assertEquals(1_800_000L, aggregate.elapsedMillis)
    }

    private fun workout(
        id: String,
        status: WorkoutStatus = WorkoutStatus.COMPLETED,
        startedAt: Long = 1_000L,
        distance: Double = 100.0,
        elapsed: Long = 60_000L,
    ) = WorkoutEntity(
        id = id,
        sessionToken = "session-$id",
        type = WorkoutType.RUN.name,
        goalKind = GoalKind.NONE.name,
        goalDistanceMeters = null,
        goalDurationMillis = null,
        goalReachedAt = null,
        status = status.name,
        startedAt = startedAt,
        startedElapsedRealtimeMillis = 500L,
        endedAt = if (status == WorkoutStatus.COMPLETED) startedAt + elapsed else null,
        elapsedMillis = elapsed,
        movingMillis = elapsed,
        distanceMeters = distance,
        averageSpeedMps = if (elapsed > 0) distance / (elapsed / 1000.0) else 0.0,
        caloriesEstimate = 100,
        heartRateAverageBpm = null,
        heartRateMaxBpm = null,
        elevationGainMeters = null,
        elevationLossMeters = null,
        title = null,
        note = null,
        createdAt = startedAt,
        updatedAt = startedAt,
    )

    private fun route(workoutId: String, rowId: Long, elapsed: Long, metersNorth: Double): RoutePointEntity {
        val lat = 55.0 + metersNorth / 111_111.0
        return RoutePointEntity(
            rowId = rowId,
            workoutId = workoutId,
            timestampMillis = 1_000L + elapsed,
            elapsedRealtimeMillis = elapsed,
            movingElapsedMillis = elapsed,
            segmentIndex = 0,
            latitude = lat,
            longitude = 37.0,
            accuracyMeters = 5f,
            altitudeMeters = null,
            speedMps = null,
            bearingDegrees = null,
            provider = "test",
        )
    }
}
