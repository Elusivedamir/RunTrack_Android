package com.runtrack.app.weather

import android.os.SystemClock
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
 *
 * Persisted weather timestamps use wall clock because they must survive process/device restarts.
 * In-process retry deadlines use elapsed realtime so changing the system clock cannot extend or
 * shorten a backoff window.
 */
class WeatherUpdateCoordinator(
    private val repository: WeatherRepository,
    private val refreshIntervalMillis: Long = DEFAULT_REFRESH_INTERVAL_MS,
    private val failureRetryIntervalMillis: Long = DEFAULT_FAILURE_RETRY_INTERVAL_MS,
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    private val monotonicClock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val inFlight = AtomicBoolean(false)
    private val refreshGate = WeatherRefreshGate()

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
            val nowWallClockMillis = wallClock()
            val nowMonotonicMillis = monotonicClock()

            if (
                !refreshGate.reserve(
                    workoutId = workoutId,
                    nowMonotonicMillis = nowMonotonicMillis,
                    retryDelayMillis = failureRetryIntervalMillis,
                )
            ) {
                return WeatherRefreshResult.Backoff
            }

            /*
             * Room is source of truth for refresh age after process restart.
             * A persisted timestamp that is now in the future means wall clock moved backwards;
             * it must not create a multi-hour artificial "fresh" window.
             */
            val latest = repository.getLatestSnapshot(workoutId)

            if (latest != null) {
                val remainingFreshMillis = remainingFreshWeatherMillis(
                    nowWallClockMillis = nowWallClockMillis,
                    fetchedAtWallClockMillis = latest.fetchedAt,
                    refreshIntervalMillis = refreshIntervalMillis,
                )

                if (remainingFreshMillis != null) {
                    refreshGate.schedule(
                        workoutId = workoutId,
                        nowMonotonicMillis = nowMonotonicMillis,
                        delayMillis = remainingFreshMillis,
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

    private fun scheduleSuccessfulRefresh(workoutId: String) {
        refreshGate.schedule(
            workoutId = workoutId,
            nowMonotonicMillis = monotonicClock(),
            delayMillis = refreshIntervalMillis,
        )
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
