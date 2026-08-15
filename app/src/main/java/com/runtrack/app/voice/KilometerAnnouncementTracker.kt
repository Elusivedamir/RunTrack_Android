package com.runtrack.app.voice

import com.runtrack.app.domain.WorkoutStatus
import com.runtrack.app.tracking.TrackingSnapshot
import kotlin.math.floor

/**
 * Converts distance snapshots into one-shot whole-kilometer milestones.
 *
 * A new/recovered workout is baselined at its current distance so old milestones are never replayed.
 * A pathological GPS/state jump is bounded per update to avoid allocating or speaking an
 * unbounded backlog; the newest milestone is retained.
 */
internal class KilometerAnnouncementTracker {
    private var workoutId: String? = null
    private var lastCompletedKilometer: Int = 0

    fun onSnapshot(snapshot: TrackingSnapshot?): List<Int> {
        if (snapshot == null) {
            reset()
            return emptyList()
        }

        val completed = completedWholeKilometers(snapshot.distanceMeters)
        if (snapshot.workoutId != workoutId) {
            workoutId = snapshot.workoutId
            lastCompletedKilometer = completed
            return emptyList()
        }

        if (completed <= lastCompletedKilometer) return emptyList()

        val first = lastCompletedKilometer + 1
        val delta = completed - lastCompletedKilometer
        lastCompletedKilometer = completed

        if (snapshot.status != WorkoutStatus.ACTIVE) return emptyList()

        return if (delta <= MAX_MILESTONES_PER_UPDATE) {
            (first..completed).toList()
        } else {
            listOf(completed)
        }
    }

    private fun reset() {
        workoutId = null
        lastCompletedKilometer = 0
    }

    private fun completedWholeKilometers(distanceMeters: Double): Int {
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0) return 0
        val raw = floor(distanceMeters / METERS_PER_KILOMETER)
        return if (raw >= Int.MAX_VALUE.toDouble()) Int.MAX_VALUE else raw.toInt()
    }

    private companion object {
        const val METERS_PER_KILOMETER = 1_000.0
        const val MAX_MILESTONES_PER_UPDATE = 5
    }
}
