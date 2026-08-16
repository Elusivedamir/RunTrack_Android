package com.runtrack.app.weather

/**
 * Monotonic in-process deadline gate. Wall-clock changes must never affect retry cadence.
 */
internal class WeatherRefreshGate {
    private val stateLock = Any()
    private var trackedWorkoutId: String? = null
    private var nextAllowedAttemptAtMonotonicMillis: Long = 0L

    fun reserve(
        workoutId: String,
        nowMonotonicMillis: Long,
        retryDelayMillis: Long,
    ): Boolean = synchronized(stateLock) {
        require(retryDelayMillis > 0L)

        if (trackedWorkoutId != workoutId) {
            trackedWorkoutId = workoutId
            nextAllowedAttemptAtMonotonicMillis = 0L
        }

        if (nowMonotonicMillis < nextAllowedAttemptAtMonotonicMillis) {
            return@synchronized false
        }

        nextAllowedAttemptAtMonotonicMillis =
            safeDeadline(nowMonotonicMillis, retryDelayMillis)

        true
    }

    fun schedule(
        workoutId: String,
        nowMonotonicMillis: Long,
        delayMillis: Long,
    ) = synchronized(stateLock) {
        require(delayMillis > 0L)
        if (trackedWorkoutId == workoutId) {
            nextAllowedAttemptAtMonotonicMillis =
                safeDeadline(nowMonotonicMillis, delayMillis)
        }
    }
}

/**
 * Returns how much longer a persisted weather snapshot is fresh.
 *
 * If fetchedAt is in the future relative to the current wall clock, the clock moved backwards.
 * Treat the snapshot as requiring a refresh instead of inventing a future freshness period.
 */
internal fun remainingFreshWeatherMillis(
    nowWallClockMillis: Long,
    fetchedAtWallClockMillis: Long,
    refreshIntervalMillis: Long,
): Long? {
    require(refreshIntervalMillis > 0L)

    if (fetchedAtWallClockMillis > nowWallClockMillis) return null
    val ageMillis = nowWallClockMillis - fetchedAtWallClockMillis
    if (ageMillis < 0L || ageMillis >= refreshIntervalMillis) return null
    return refreshIntervalMillis - ageMillis
}

private fun safeDeadline(baseMillis: Long, delayMillis: Long): Long {
    if (baseMillis > Long.MAX_VALUE - delayMillis) return Long.MAX_VALUE
    return baseMillis + delayMillis
}
