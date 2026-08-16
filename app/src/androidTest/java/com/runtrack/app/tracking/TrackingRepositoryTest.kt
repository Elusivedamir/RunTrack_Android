package com.runtrack.app.tracking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runtrack.app.data.RunTrackDatabase
import com.runtrack.app.domain.Geo
import com.runtrack.app.domain.LocationSample
import com.runtrack.app.domain.WorkoutGoal
import com.runtrack.app.domain.WorkoutStatus
import com.runtrack.app.domain.WorkoutType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingRepositoryTest {
    private lateinit var db: RunTrackDatabase
    private lateinit var repository: TrackingRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RunTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TrackingRepository(db)
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun plausibleLongGpsGapStartsNewSegmentWithoutInventingGapDistance() = runBlocking {
        val id = repository.start(
            type = WorkoutType.RUN,
            goal = WorkoutGoal(),
            wallClockMillis = 1_000L,
            elapsedRealtimeMillis = 1_000L,
        )
        val a = LocationSample(
            timestampMillis = 1_000L,
            latitude = 55.0,
            longitude = 37.0,
            accuracyMeters = 5f,
            monotonicMillis = 1_000L,
        )
        val b = a.copy(
            timestampMillis = 61_000L,
            latitude = 55.0 + 220.0 / 111_111.0,
            monotonicMillis = 61_000L,
        )
        val c = b.copy(
            timestampMillis = 64_000L,
            latitude = b.latitude + 12.0 / 111_111.0,
            monotonicMillis = 64_000L,
        )

        assertTrue(repository.onLocation(a, 1_000L))
        assertTrue(repository.onLocation(b, 61_000L))
        assertTrue(repository.onLocation(c, 64_000L))

        val relation = requireNotNull(db.workoutDao().getWorkoutWithRoute(id))
        val route = relation.route.sortedBy { it.elapsedRealtimeMillis }
        assertEquals(listOf(0, 1, 1), route.map { it.segmentIndex })

        val expectedRecordedDistance = Geo.distanceMeters(b, c)
        assertEquals(expectedRecordedDistance, relation.workout.distanceMeters, 1.0)
    }

    @Test fun finishIsDurablyFinishingBeforeItBecomesCompleted() = runBlocking {
        val id = repository.start(
            type = WorkoutType.RUN,
            goal = WorkoutGoal(),
            wallClockMillis = 1_000L,
            elapsedRealtimeMillis = 1_000L,
        )
        assertTrue(repository.manualPause(2_000L, 2_000L))
        assertTrue(repository.resume(2_500L, 2_500L))

        assertEquals(id, repository.requestFinish(4_000L, 4_000L))
        assertEquals(
            WorkoutStatus.FINISHING.name,
            requireNotNull(db.workoutDao().getWorkout(id)).status,
        )

        assertEquals(id, repository.commitFinish(4_100L, 70.0))
        assertEquals(
            WorkoutStatus.COMPLETED.name,
            requireNotNull(db.workoutDao().getWorkout(id)).status,
        )
        assertNull(repository.state.value)
    }

    @Test fun activeSessionRestoresAsRecoveryRequiredAfterProcessOwnershipLoss() = runBlocking {
        val id = repository.start(
            type = WorkoutType.WALK,
            goal = WorkoutGoal(),
            wallClockMillis = 10_000L,
            elapsedRealtimeMillis = 5_000L,
        )
        repository.checkpoint(11_000L, 6_000L)

        val recreatedRepository = TrackingRepository(db)
        val restored = requireNotNull(
            recreatedRepository.restoreRecoverable(
                nowWallClockMillis = 12_000L,
                nowElapsedRealtimeMillis = 7_000L,
            )
        )
        assertEquals(id, restored.workoutId)
        assertEquals(WorkoutStatus.RECOVERY_REQUIRED, restored.status)
        assertEquals(
            WorkoutStatus.RECOVERY_REQUIRED.name,
            requireNotNull(db.workoutDao().getWorkout(id)).status,
        )
    }
}
