package com.runtrack.app.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RunTrackDomainTest {
    @Test fun goalValidationRejectsZero() {
        assertTrue(WorkoutGoal(GoalKind.NONE).isValid())
        assertFalse(WorkoutGoal(GoalKind.DISTANCE, distanceMeters = 0.0).isValid())
        assertFalse(WorkoutGoal(GoalKind.DURATION, durationMillis = 0).isValid())
    }

    @Test fun distanceAndPaceAreFinite() {
        val m = WorkoutMath.metrics(5000.0, 30 * 60_000L, 25 * 60_000L)
        assertEquals(3.333333, m.averageSpeedMps, 0.001)
        assertEquals(300.0, requireNotNull(m.paceSecondsPerKm), 0.01)
        val zero = WorkoutMath.metrics(0.0, 1000, 0)
        assertEquals(0.0, zero.averageSpeedMps, 0.0)
        assertNull(zero.paceSecondsPerKm)
    }

    @Test fun gpsFilterRejectsJumpAndDuplicate() {
        val filter = GpsPointFilter(GpsFilterPolicy.forType(WorkoutType.RUN))
        val a = LocationSample(1_000, 52.5200, 13.4050, 5f)
        assertTrue(filter.validate(a) is GpsValidation.Accepted)
        assertTrue(filter.validate(a.copy(timestampMillis = 1_100, latitude = 52.5200001)) is GpsValidation.Rejected)
        assertTrue(filter.validate(LocationSample(61_000, 53.5200, 13.4050, 5f)) is GpsValidation.Rejected)
    }

    @Test fun autoPauseUsesDebounceAndManualPriority() {
        val controller = AutoPauseController(AutoPausePolicy.forType(WorkoutType.RUN))
        assertEquals(AutoPauseEvent.None, controller.update(0, 0.1, 5f, false))
        assertEquals(AutoPauseEvent.None, controller.update(7_999, 0.1, 5f, false))
        assertEquals(AutoPauseEvent.Pause, controller.update(8_000, 0.1, 5f, false))
        assertEquals(AutoPauseEvent.None, controller.update(9_000, 1.5, 5f, false))
        assertEquals(AutoPauseEvent.Resume, controller.update(12_000, 1.5, 5f, false))

        val manual = AutoPauseController(AutoPausePolicy.forType(WorkoutType.RUN))
        manual.update(0, 0.0, 5f, false)
        assertEquals(AutoPauseEvent.None, manual.update(20_000, 0.0, 5f, true))
    }

    @Test fun autoPauseIgnoresImplausibleProviderSpeed() {
        val controller = AutoPauseController(AutoPausePolicy.forType(WorkoutType.RUN))
        controller.update(0, 0.0, 5f, false)
        assertEquals(AutoPauseEvent.Pause, controller.update(8_000, 0.0, 5f, false))
        assertEquals(AutoPauseEvent.None, controller.update(9_000, 99.0, 5f, false))
        assertEquals(AutoPauseEvent.None, controller.update(13_000, 99.0, 5f, false))
        assertEquals(AutoPauseEvent.None, controller.update(14_000, 1.5, 5f, false))
        assertEquals(AutoPauseEvent.Resume, controller.update(17_000, 1.5, 5f, false))
    }

    @Test fun stateMachineRejectsIllegalFinish() {
        val sm = WorkoutStateMachine()
        assertTrue(sm.transition(WorkoutStatus.PREPARING))
        assertTrue(sm.transition(WorkoutStatus.ACTIVE))
        assertFalse(sm.transition(WorkoutStatus.COMPLETED))
        assertTrue(sm.transition(WorkoutStatus.FINISHING))
        assertTrue(sm.transition(WorkoutStatus.COMPLETED))
        assertFalse(sm.transition(WorkoutStatus.ACTIVE))
    }

    @Test fun routeNormalizationKeepsPointsInsideCanvas() {
        val line = listOf(
            LocationSample(0, 55.0, 37.0, 5f),
            LocationSample(1000, 55.01, 37.01, 5f),
            LocationSample(2000, 55.02, 37.02, 5f),
        )
        val normalized = RouteGeometry.normalize(line, 300f, 200f, 20f)
        assertEquals(3, normalized.size)
        assertTrue(normalized.all { it.x in 0f..300f && it.y in 0f..200f })
        assertTrue(abs(normalized.last().x - normalized.first().x) > 1f)
        assertTrue(abs(normalized.last().y - normalized.first().y) > 1f)
    }
    @Test fun gpsFilterUsesMonotonicClockWhenWallClockMovesBackwards() {
        val filter = GpsPointFilter(GpsFilterPolicy.forType(WorkoutType.RUN))
        val a = LocationSample(10_000, 52.5200, 13.4050, 5f, monotonicMillis = 100_000)
        val b = LocationSample(9_000, 52.5201, 13.4050, 5f, monotonicMillis = 102_000)
        assertTrue(filter.validate(a) is GpsValidation.Accepted)
        assertTrue(filter.validate(b) is GpsValidation.Accepted)
    }

    @Test fun renderDownsamplingPreservesEndpointsAndBound() {
        val points = (0..9_999).map { i -> LocationSample(i.toLong(), 55.0 + i * 1e-6, 37.0, 5f) }
        val sampled = RouteGeometry.downsampleForRender(points, 500)
        assertEquals(500, sampled.size)
        assertEquals(points.first(), sampled.first())
        assertEquals(points.last(), sampled.last())
    }

    @Test fun segmentedElevationDoesNotBridgePauseAndSharedNormalizationPreservesPlacement() {
        val a = LocationSample(0, 55.0, 37.0, 5f, altitudeMeters = 100.0)
        val segments = listOf(
            listOf(a, a.copy(timestampMillis = 1_000, latitude = 55.001, longitude = 37.001, altitudeMeters = 104.0)),
            listOf(a.copy(timestampMillis = 2_000, latitude = 56.0, longitude = 38.0, altitudeMeters = 200.0), a.copy(timestampMillis = 3_000, latitude = 56.001, longitude = 38.001, altitudeMeters = 197.0)),
        )
        val elevation = WorkoutMath.elevationGainLossSegments(segments)!!
        assertEquals(4.0, elevation.first, 0.01)
        assertEquals(3.0, elevation.second, 0.01)
        val normalized = RouteGeometry.normalizeRoutes(segments, 300f, 200f, 20f)
        assertEquals(2, normalized.size)
        assertTrue(normalized[0].last().x < normalized[1].first().x)
    }

}
