package com.runtrack.app.health

import org.junit.Assert.assertFalse
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
}
