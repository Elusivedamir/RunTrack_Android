package com.runtrack.app.domain

import org.junit.Assert.*
import org.junit.Test

class HeartRateProtocolTest {
    @Test fun parses8And16BitMeasurements() {
        assertEquals(72, HeartRateProtocol.parseMeasurement(byteArrayOf(0x00, 72)))
        assertEquals(180, HeartRateProtocol.parseMeasurement(byteArrayOf(0x01, 0xB4.toByte(), 0x00)))
    }

    @Test fun rejectsMalformedAndImplausibleMeasurements() {
        assertNull(HeartRateProtocol.parseMeasurement(byteArrayOf()))
        assertNull(HeartRateProtocol.parseMeasurement(byteArrayOf(0x01, 0x10)))
        assertNull(HeartRateProtocol.parseMeasurement(byteArrayOf(0x00, 5)))
    }
}
