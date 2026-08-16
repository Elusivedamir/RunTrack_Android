package com.runtrack.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleReconnectCounterTest {
    @Test
    fun retryCapCannotBeBypassedWithoutExplicitReadyReset() {
        val counter = BleReconnectCounter(maxAttempts = 3)

        assertEquals(1, counter.nextAttemptOrNull())
        assertEquals(2, counter.nextAttemptOrNull())
        assertEquals(3, counter.nextAttemptOrNull())
        assertNull(counter.nextAttemptOrNull())
        assertNull(counter.nextAttemptOrNull())

        counter.reset()

        assertEquals(1, counter.nextAttemptOrNull())
    }
}
