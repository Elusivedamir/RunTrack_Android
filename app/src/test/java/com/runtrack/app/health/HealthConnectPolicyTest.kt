package com.runtrack.app.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectPolicyTest {
    @Test fun permissionsAreWriteOnlyAndExcludeExerciseRoutes() {
        val permissions = HealthConnectManager.REQUIRED_PERMISSIONS

        assertTrue(permissions.isNotEmpty())
        assertTrue(permissions.all { it.contains(".WRITE_") })
        assertFalse(permissions.any { it.contains("EXERCISE_ROUTE") })
        assertFalse(permissions.any { it.contains("LOCATION") })
    }

    @Test fun exportIntervalUsesMonotonicDurationInsteadOfEndedWallClock() {
        assertEquals(1_601_000L, healthConnectEndMillis(1_000L, 1_600_000L))
        assertThrows(IllegalArgumentException::class.java) {
            healthConnectEndMillis(1_000L, 0L)
        }
    }
}
