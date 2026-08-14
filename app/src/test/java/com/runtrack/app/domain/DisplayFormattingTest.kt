package com.runtrack.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DisplayFormattingTest {
    @Test fun canonicalValuesFormatWithoutChangingStorage() {
        assertEquals("5.00 км", RunTrackFormatter.distance(5000.0, UnitSystem.METRIC, Locale.US))
        assertEquals("3.11 mi", RunTrackFormatter.distance(5000.0, UnitSystem.IMPERIAL, Locale.US))
        assertEquals("5:00 /км", RunTrackFormatter.pace(300.0, UnitSystem.METRIC))
        assertEquals("8:03 /mi", RunTrackFormatter.pace(300.0, UnitSystem.IMPERIAL))
        assertEquals("18.0 км/ч", RunTrackFormatter.speed(5.0, UnitSystem.METRIC, Locale.US))
    }

    @Test fun unavailableAndInvalidAreNeverNaN() {
        assertEquals("—", RunTrackFormatter.pace(null, UnitSystem.METRIC))
        assertEquals("Нет данных", RunTrackFormatter.elevation(null, UnitSystem.METRIC, Locale.US))
        assertFalse(RunTrackFormatter.distance(Double.NaN, UnitSystem.METRIC, Locale.US).contains("NaN"))
    }
}
