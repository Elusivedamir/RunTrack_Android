package com.runtrack.app.tracking

/**
 * Counts automatic reconnects in one BLE connection attempt chain.
 *
 * A transient STATE_CONNECTED callback is not success: the counter is reset only by the manager
 * after Heart Rate notifications are fully subscribed (CCCD write succeeded), or by an explicit
 * user connect action.
 */
internal class BleReconnectCounter(
    private val maxAttempts: Int,
) {
    private var attempts = 0

    init {
        require(maxAttempts >= 0)
    }

    fun reset() {
        attempts = 0
    }

    fun nextAttemptOrNull(): Int? {
        if (attempts >= maxAttempts) return null
        attempts += 1
        return attempts
    }
}
