package com.runtrack.app.tracking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Serializes one asynchronous location-registration lifecycle.
 * A token from an older/cancelled request can never become current again.
 */
internal class LocationRegistrationState {
    private var generation: Long = 0L
    private var pending: Boolean = false
    private var registered: Boolean = false

    @Synchronized
    fun beginIfNeeded(): Long? {
        if (pending || registered) return null
        generation += 1L
        pending = true
        return generation
    }

    @Synchronized
    fun markSuccess(token: Long): Boolean {
        if (!pending || token != generation) return false
        pending = false
        registered = true
        return true
    }

    @Synchronized
    fun markFailure(token: Long): Boolean {
        if (!pending || token != generation) return false
        pending = false
        registered = false
        return true
    }

    @Synchronized
    fun cancel() {
        generation += 1L
        pending = false
        registered = false
    }

    @Synchronized
    fun isRegistered(token: Long): Boolean = registered && token == generation
}

/**
 * Owns ordered batch consumption while keeping ordinary processing failures inside the
 * tracking recovery boundary. Coroutine cancellation is never converted into a failure.
 */
internal suspend fun <T> consumeBatchesSafely(
    batches: ReceiveChannel<List<T>>,
    orderBy: (T) -> Long,
    process: suspend (T) -> Unit,
    onFailure: suspend (Exception) -> Unit,
) {
    try {
        for (batch in batches) {
            for (item in batch.sortedBy(orderBy)) {
                process(item)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(error)
    }
}

/**
 * Runs one durability checkpoint without hiding storage failures.
 *
 * Cancellation remains cooperative. Any ordinary checkpoint exception crosses the existing
 * tracking recovery boundary before the caller stops its checkpoint loop.
 */
internal suspend fun checkpointWithRecoveryOnFailure(
    checkpoint: suspend () -> Unit,
    onFailure: suspend (Exception) -> Unit,
): Boolean {
    return try {
        checkpoint()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(error)
        false
    }
}

/**
 * Makes the persisted PAUSED -> ACTIVE transition and service restart one recoverable operation.
 */
internal suspend fun resumeWithRecoveryOnStartFailure(
    resume: suspend () -> Boolean,
    startService: () -> Unit,
    requireRecovery: suspend () -> Unit,
): Boolean {
    if (!resume()) return false

    try {
        startService()
    } catch (startError: Exception) {
        try {
            requireRecovery()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (recoveryError: Exception) {
            startError.addSuppressed(recoveryError)
        }
        throw startError
    }

    return true
}
