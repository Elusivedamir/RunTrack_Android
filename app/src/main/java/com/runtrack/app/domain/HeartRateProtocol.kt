package com.runtrack.app.domain

/** Parser for Bluetooth SIG Heart Rate Measurement (0x2A37). */
object HeartRateProtocol {
    fun parseMeasurement(value: ByteArray): Int? {
        if (value.size < 2) return null
        val flags = value[0].toInt() and 0xFF
        val sixteenBit = flags and 0x01 != 0
        val bpm = if (sixteenBit) {
            if (value.size < 3) return null
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else value[1].toInt() and 0xFF
        return bpm.takeIf { it in 25..250 }
    }
}
