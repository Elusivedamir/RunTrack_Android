package com.runtrack.app.weather

import com.runtrack.app.domain.LocationSample
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controls when weather may be refreshed during an active workout.
 *
 * The coordinator is intentionally independent from GPS persistence.
 * A weather request is optional side work and must never block route
 * recording.
 *
 * Policy:
 * - first accepted GPS point -> weather request;
 * - successful weather -> refresh no sooner than 30 minutes;
 * - provider/network failure -> retry no sooner than 5 minutes;
 * - only one request may be in flight;
 * - after process restart Room is checked before another network call.
 */
class WeatherUpdateCoordinator(
    private val repository: WeatherRepository,
    private val refreshIntervalMillis: Long = DEFAULT_REFRESH_INTERVAL_MS,
    private val failureRetryIntervalMillis: Long = DEFAULT_FAILURE_RETRY_INTERVAL_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val inFlight = AtomicBoolean(false)

    private val stateLock = Any()
    private var trackedWorkoutId: String? = null
    private var nextAllowedAttemptAtMillis: Long = 0L

    init {
        require(refreshIntervalMillis > 0L)
        require(failureRetryIntervalMillis > 0L)
    }

    suspend fun onAcceptedLocation(
        workoutId: String,
        sample: LocationSample,
    ): WeatherRefreshResult {
        if (workoutId.isBlank()) {
            return WeatherRefreshResult.WorkoutMissing
        }

        if (
            !sample.latitude.isFinite() ||
            !sample.longitude.isFinite() ||
            sample.latitude !in -90.0..90.0 ||
            sample.longitude !in -180.0..180.0
        ) {
            return WeatherRefreshResult.InvalidLocation
        }

        /*
         * Do not queue weather requests behind an existing one.
         * GPS callbacks may continue freely while weather is loading.
         */
        if (!inFlight.compareAndSet(false, true)) {
            return WeatherRefreshResult.InFlight
        }

        try {
            val now = clock()

            if (!reserveAttempt(workoutId, now)) {
                return WeatherRefreshResult.Backoff
            }

            /*
             * Room is source of truth for refresh age.
             * This also prevents a duplicate request after process restart.
             */
            val latest = repository.getLatestSnapshot(workoutId)

            if (latest != null) {
                val ageMillis =
                    (now - latest.fetchedAt).coerceAtLeast(0L)

                if (ageMillis < refreshIntervalMillis) {
                    setNextAllowed(
                        workoutId = workoutId,
                        timestampMillis = safeAdd(
                            latest.fetchedAt,
                            refreshIntervalMillis,
                        ),
                    )
                    return WeatherRefreshResult.FreshEnough
                }
            }

            return when (
                val result = repository.fetchAndStoreCurrent(
                    workoutId = workoutId,
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                )
            ) {
                is WeatherFetchResult.Stored -> {
                    scheduleSuccessfulRefresh(workoutId)
                    WeatherRefreshResult.Stored
                }

                is WeatherFetchResult.AlreadyStored -> {
                    scheduleSuccessfulRefresh(workoutId)
                    WeatherRefreshResult.AlreadyStored
                }

                WeatherFetchResult.WorkoutNotFound ->
                    WeatherRefreshResult.WorkoutMissing

                is WeatherFetchResult.Failed ->
                    WeatherRefreshResult.Failed(result.reason)
            }
        } finally {
            inFlight.set(false)
        }
    }

    /**
     * Reserve one provider attempt.
     *
     * A failed network request retains the short retry deadline set here.
     * A successful request replaces it with the longer refresh interval.
     */
    private fun reserveAttempt(
        workoutId: String,
        nowMillis: Long,
    ): Boolean = synchronized(stateLock) {
        if (trackedWorkoutId != workoutId) {
            trackedWorkoutId = workoutId
            nextAllowedAttemptAtMillis = 0L
        }

        if (nowMillis < nextAllowedAttemptAtMillis) {
            return@synchronized false
        }

        nextAllowedAttemptAtMillis =
            safeAdd(nowMillis, failureRetryIntervalMillis)

        true
    }

    private fun scheduleSuccessfulRefresh(
        workoutId: String,
    ) {
        setNextAllowed(
            workoutId = workoutId,
            timestampMillis = safeAdd(
                clock(),
                refreshIntervalMillis,
            ),
        )
    }

    private fun setNextAllowed(
        workoutId: String,
        timestampMillis: Long,
    ) = synchronized(stateLock) {
        if (trackedWorkoutId == workoutId) {
            nextAllowedAttemptAtMillis = timestampMillis
        }
    }

    private fun safeAdd(
        base: Long,
        delta: Long,
    ): Long {
        if (delta <= 0L) return base

        return if (base > Long.MAX_VALUE - delta) {
            Long.MAX_VALUE
        } else {
            base + delta
        }
    }

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MS =
            30L * 60L * 1000L

        const val DEFAULT_FAILURE_RETRY_INTERVAL_MS =
            5L * 60L * 1000L
    }
}

sealed interface WeatherRefreshResult {
    data object Stored : WeatherRefreshResult
    data object AlreadyStored : WeatherRefreshResult
    data object FreshEnough : WeatherRefreshResult
    data object Backoff : WeatherRefreshResult
    data object InFlight : WeatherRefreshResult
    data object WorkoutMissing : WeatherRefreshResult
    data object InvalidLocation : WeatherRefreshResult

    data class Failed(
        val reason: String,
    ) : WeatherRefreshResult
}
