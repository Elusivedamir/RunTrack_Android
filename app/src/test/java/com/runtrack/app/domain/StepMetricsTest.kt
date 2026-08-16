package com.runtrack.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepMetricsTest {
    @Test fun cadenceUsesRealStepsAndMovingTime() {
        assertEquals(120.0, requireNotNull(StepMetrics.cadenceStepsPerMinute(600L, 300_000L, true)), 0.0001)
    }

    @Test fun strideUsesRealStepsAndGpsDistance() {
        assertEquals(0.8, requireNotNull(StepMetrics.strideLengthMeters(1_000L, 800.0, true)), 0.0001)
    }

    @Test fun invalidInputsNeverInventMetrics() {
        assertNull(StepMetrics.cadenceStepsPerMinute(0L, 60_000L, true))
        assertNull(StepMetrics.cadenceStepsPerMinute(100L, 0L, true))
        assertNull(StepMetrics.cadenceStepsPerMinute(100L, 60_000L, false))
        assertNull(StepMetrics.strideLengthMeters(0L, 100.0, true))
        assertNull(StepMetrics.strideLengthMeters(100L, Double.NaN, true))
        assertNull(StepMetrics.strideLengthMeters(100L, 0.0, true))
        assertNull(StepMetrics.strideLengthMeters(100L, 100.0, false))
    }

    @Test fun stepCounterUsesPausedBaselineWithoutDroppingFirstProvenActiveDelta() {
        val accumulator = StepCounterAccumulator()
        accumulator.setActive(true)
        assertEquals(0L, accumulator.onCounter(1_000L))
        assertEquals(5L, accumulator.onCounter(1_005L))

        accumulator.setActive(false)
        // This sample proves the pause baseline moved to 1015.
        assertEquals(0L, accumulator.onCounter(1_015L))

        accumulator.setActive(true)
        assertEquals(2L, accumulator.onCounter(1_017L))
        assertEquals(3L, accumulator.onCounter(1_020L))
        assertEquals(10L, accumulator.workoutSteps)
    }

    @Test fun stepCounterStillDiscardsAmbiguousResumeDeltaWithoutPausedSample() {
        val accumulator = StepCounterAccumulator()
        accumulator.setActive(true)
        assertEquals(0L, accumulator.onCounter(2_000L))
        assertEquals(5L, accumulator.onCounter(2_005L))

        accumulator.setActive(false)
        accumulator.setActive(true)

        // No sensor sample arrived during pause, so 2005 -> 2012 may contain paused steps.
        assertEquals(0L, accumulator.onCounter(2_012L))
        assertEquals(2L, accumulator.onCounter(2_014L))
        assertEquals(7L, accumulator.workoutSteps)
    }

    @Test fun counterResetCannotCreateSyntheticDelta() {
        val accumulator = StepCounterAccumulator(10L)
        accumulator.setActive(true)
        assertEquals(0L, accumulator.onCounter(5_000L))
        assertEquals(2L, accumulator.onCounter(5_002L))
        assertEquals(0L, accumulator.onCounter(3L))
        assertEquals(4L, accumulator.onCounter(7L))
        assertEquals(16L, accumulator.workoutSteps)
    }
}
