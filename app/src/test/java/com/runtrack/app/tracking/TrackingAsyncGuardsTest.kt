package com.runtrack.app.tracking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TrackingAsyncGuardsTest {

    @Test
    fun staleRegistrationCompletionCannotBecomeCurrent() {
        val state = LocationRegistrationState()
        val first = state.beginIfNeeded() ?: error("first request not created")

        state.cancel()
        val second = state.beginIfNeeded() ?: error("second request not created")

        assertTrue(first != second)
        assertFalse(state.markSuccess(first))
        assertTrue(state.markSuccess(second))
        assertTrue(state.isRegistered(second))
        assertFalse(state.isRegistered(first))
    }

    @Test
    fun failedRegistrationAllowsARealRetry() {
        val state = LocationRegistrationState()
        val first = state.beginIfNeeded() ?: error("first request not created")

        assertTrue(state.markFailure(first))
        val retry = state.beginIfNeeded() ?: error("retry request not created")

        assertTrue(first != retry)
        assertTrue(state.markSuccess(retry))
        assertTrue(state.isRegistered(retry))
    }

    @Test
    fun batchConsumerReportsProcessingFailureWithoutEscaping() = runBlocking {
        val channel = Channel<List<Int>>(capacity = 1)
        channel.send(listOf(3, 1, 2))
        channel.close()

        val processed = mutableListOf<Int>()
        var reported: Exception? = null

        consumeBatchesSafely(
            batches = channel,
            orderBy = { it.toLong() },
            process = { value ->
                if (value == 2) error("boom")
                processed += value
            },
            onFailure = { reported = it },
        )

        assertEquals(listOf(1), processed)
        assertEquals("boom", reported?.message)
    }

    @Test
    fun batchConsumerNeverConvertsCancellationIntoTrackingFailure() = runBlocking {
        val channel = Channel<List<Int>>(capacity = 1)
        channel.send(listOf(1))
        channel.close()
        var failureCalled = false

        try {
            consumeBatchesSafely(
                batches = channel,
                orderBy = { it.toLong() },
                process = { throw CancellationException("cancel") },
                onFailure = { failureCalled = true },
            )
            fail("CancellationException must propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("cancel", cancelled.message)
        }

        assertFalse(failureCalled)
    }

    @Test
    fun resumeServiceStartFailureRequestsRecoveryBeforeRethrow() = runBlocking {
        var recoveryCalls = 0

        try {
            resumeWithRecoveryOnStartFailure(
                resume = { true },
                startService = { error("service start failed") },
                requireRecovery = {
                    recoveryCalls += 1
                    Unit
                },
            )
            fail("service start failure must be rethrown")
        } catch (error: IllegalStateException) {
            assertEquals("service start failed", error.message)
        }

        assertEquals(1, recoveryCalls)
    }

    @Test
    fun checkpointFailureTriggersRecoveryAndStopsLoop() = runBlocking {
        var recoveryCalls = 0
        var captured: Exception? = null

        val succeeded = checkpointWithRecoveryOnFailure(
            checkpoint = { error("disk write failed") },
            onFailure = { error ->
                recoveryCalls += 1
                captured = error
            },
        )

        assertFalse(succeeded)
        assertEquals(1, recoveryCalls)
        assertEquals("disk write failed", captured?.message)
    }

    @Test
    fun checkpointCancellationPropagatesWithoutRecovery() = runBlocking {
        var recoveryCalled = false

        try {
            checkpointWithRecoveryOnFailure(
                checkpoint = { throw CancellationException("cancel checkpoint") },
                onFailure = { recoveryCalled = true },
            )
            fail("CancellationException must propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("cancel checkpoint", cancelled.message)
        }

        assertFalse(recoveryCalled)
    }
}
