package com.runtrack.app.domain

/**
 * Resolves a persisted wall-clock end instant without letting a backwards system-clock jump
 * create an impossible completed interval.
 */
object WorkoutTime {
    fun resolveEndMillis(
        startedAtMillis: Long,
        observedEndMillis: Long,
        elapsedMillis: Long,
    ): Long {
        require(startedAtMillis >= 0L) { "startedAtMillis must be non-negative" }
        require(elapsedMillis >= 0L) { "elapsedMillis must be non-negative" }

        if (observedEndMillis >= startedAtMillis) return observedEndMillis

        return if (startedAtMillis > Long.MAX_VALUE - elapsedMillis) {
            Long.MAX_VALUE
        } else {
            startedAtMillis + elapsedMillis
        }
    }
}
