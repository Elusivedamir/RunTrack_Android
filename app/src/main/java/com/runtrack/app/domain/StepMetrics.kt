package com.runtrack.app.domain

object StepMetrics {
    fun cadenceStepsPerMinute(stepCount: Long?, movingMillis: Long, reliable: Boolean): Double? {
        if (!reliable) return null
        val steps = stepCount?.takeIf { it > 0L } ?: return null
        if (movingMillis <= 0L) return null
        return steps.toDouble() * 60_000.0 / movingMillis.toDouble()
    }

    fun strideLengthMeters(stepCount: Long?, distanceMeters: Double, reliable: Boolean): Double? {
        if (!reliable) return null
        val steps = stepCount?.takeIf { it > 0L } ?: return null
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0) return null
        return distanceMeters / steps.toDouble()
    }
}

class StepCounterAccumulator(initialWorkoutSteps: Long = 0L) {
    var workoutSteps: Long = initialWorkoutSteps.coerceAtLeast(0L)
        private set

    private var active = false
    private var lastCounterValue: Long? = null
    private var discardNextCounterDelta = true

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) discardNextCounterDelta = true
    }

    fun onCounter(rawValue: Long): Long {
        if (rawValue < 0L) return 0L
        val previous = lastCounterValue
        lastCounterValue = rawValue
        if (previous == null) {
            discardNextCounterDelta = false
            return 0L
        }
        if (rawValue < previous) {
            discardNextCounterDelta = false
            return 0L
        }
        if (discardNextCounterDelta) {
            discardNextCounterDelta = false
            return 0L
        }
        if (!active) return 0L
        val delta = rawValue - previous
        if (delta <= 0L || workoutSteps > Long.MAX_VALUE - delta) return 0L
        workoutSteps += delta
        return delta
    }

    fun onDetector(): Long {
        if (!active || workoutSteps == Long.MAX_VALUE) return 0L
        workoutSteps += 1L
        return 1L
    }
}
