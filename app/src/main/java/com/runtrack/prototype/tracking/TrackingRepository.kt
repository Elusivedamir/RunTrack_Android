package com.runtrack.prototype.tracking

import androidx.room.withTransaction
import com.runtrack.prototype.data.*
import com.runtrack.prototype.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class TrackingSnapshot(
    val workoutId: String,
    val type: WorkoutType,
    val goal: WorkoutGoal,
    val goalReached: Boolean,
    val status: WorkoutStatus,
    val startedAtMillis: Long,
    val startedElapsedRealtimeMillis: Long,
    val endedAtMillis: Long?,
    val elapsedMillis: Long,
    val movingMillis: Long,
    val distanceMeters: Double,
    val averageSpeedMps: Double,
    val caloriesEstimate: Int,
    val routePointCount: Int,
    val lastAccuracyMeters: Float?,
    val lastLocationAtMillis: Long?,
    val gpsAvailable: Boolean = false,
    val lastLocationMonotonicMillis: Long? = null,
)

class TrackingRepository(private val db: RunTrackDatabase) {
    private val dao = db.workoutDao()
    private val mutex = Mutex()
    private val _state = MutableStateFlow<TrackingSnapshot?>(null)
    val state: StateFlow<TrackingSnapshot?> = _state.asStateFlow()

    private var filter: GpsPointFilter? = null
    private var autoPauseController: AutoPauseController? = null
    private var stateMachine: WorkoutStateMachine? = null
    private var activeWorkoutId: String? = null
    private var type: WorkoutType? = null
    private var lastAccepted: LocationSample? = null
    private var accumulatedDistanceMeters = 0.0
    private var accumulatedElapsedMillis = 0L
    private var elapsedStartedAtElapsedMillis: Long? = null
    private var movingStartedAtElapsedMillis: Long? = null
    private var accumulatedMovingMillis = 0L
    private var routePointCount = 0
    private var segmentIndex = 0

    suspend fun start(type: WorkoutType, goal: WorkoutGoal, wallClockMillis: Long, elapsedRealtimeMillis: Long): String = mutex.withLock {
        require(goal.isValid()) { "invalid workout goal" }
        check(activeWorkoutId == null) { "workout already active" }
        val existing = dao.getRecoverableWorkout()
        check(existing == null) { "recoverable workout exists: ${existing.id}" }

        val id = UUID.randomUUID().toString()
        val token = UUID.randomUUID().toString()
        val sm = WorkoutStateMachine()
        check(sm.transition(WorkoutStatus.PREPARING))
        val entity = WorkoutEntity(
            id = id,
            sessionToken = token,
            type = type.name,
            goalKind = goal.kind.name,
            goalDistanceMeters = goal.distanceMeters,
            goalDurationMillis = goal.durationMillis,
            goalReachedAt = null,
            status = WorkoutStatus.PREPARING.name,
            startedAt = wallClockMillis,
            startedElapsedRealtimeMillis = elapsedRealtimeMillis,
            endedAt = null,
            elapsedMillis = 0,
            movingMillis = 0,
            distanceMeters = 0.0,
            averageSpeedMps = 0.0,
            caloriesEstimate = 0,
            heartRateAverageBpm = null,
            heartRateMaxBpm = null,
            elevationGainMeters = null,
            elevationLossMeters = null,
            title = null,
            note = null,
            createdAt = wallClockMillis,
            updatedAt = wallClockMillis,
        )
        dao.insertWorkout(entity)
        check(sm.canTransition(WorkoutStatus.ACTIVE))
        val active = entity.copy(status = WorkoutStatus.ACTIVE.name, updatedAt = wallClockMillis)
        check(dao.updateWorkout(active) == 1)
        check(sm.transition(WorkoutStatus.ACTIVE))

        activeWorkoutId = id
        this.type = type
        stateMachine = sm
        filter = GpsPointFilter(GpsFilterPolicy.forType(type))
        autoPauseController = AutoPauseController(AutoPausePolicy.forType(type))
        lastAccepted = null
        accumulatedDistanceMeters = 0.0
        accumulatedElapsedMillis = 0L
        elapsedStartedAtElapsedMillis = elapsedRealtimeMillis
        accumulatedMovingMillis = 0L
        movingStartedAtElapsedMillis = elapsedRealtimeMillis
        routePointCount = 0
        segmentIndex = 0
        _state.value = active.toSnapshot(type, routePointCount, null, null, gpsAvailable = true)
        id
    }

    /**
     * Restore only what can be proven from persisted state. An ACTIVE session after process death is
     * not assumed to still be recording: the service shares the same process and uses START_NOT_STICKY.
     * It therefore becomes RECOVERY_REQUIRED until the user explicitly resumes or finishes it.
     */
    suspend fun restoreRecoverable(nowWallClockMillis: Long, nowElapsedRealtimeMillis: Long): TrackingSnapshot? = mutex.withLock {
        if (activeWorkoutId != null) return _state.value
        val relation = dao.getRecoverableWorkoutWithRoute() ?: return null
        val entity = relation.workout
        val workoutType = runCatching { WorkoutType.valueOf(entity.type) }.getOrNull() ?: run {
            markRecoveryRequired(entity, nowWallClockMillis)
            return requireNotNull(dao.getWorkout(entity.id)).toSnapshot(WorkoutType.RUN, relation.route.size, null, null)
        }
        val persistedStatus = runCatching { WorkoutStatus.valueOf(entity.status) }.getOrNull() ?: WorkoutStatus.RECOVERY_REQUIRED
        val rebooted = nowElapsedRealtimeMillis < entity.startedElapsedRealtimeMillis
        val restoredStatus = when {
            rebooted -> WorkoutStatus.RECOVERY_REQUIRED
            persistedStatus in setOf(
                WorkoutStatus.ACTIVE,
                WorkoutStatus.PREPARING,
                WorkoutStatus.MANUAL_PAUSED,
                WorkoutStatus.AUTO_PAUSED,
            ) -> WorkoutStatus.RECOVERY_REQUIRED
            else -> persistedStatus
        }
        val effective = if (restoredStatus != persistedStatus) {
            val changed = entity.copy(status = restoredStatus.name, updatedAt = nowWallClockMillis)
            check(dao.updateWorkout(changed) == 1)
            changed
        } else entity

        activeWorkoutId = effective.id
        type = workoutType
        stateMachine = WorkoutStateMachine(restoredStatus)
        filter = GpsPointFilter(GpsFilterPolicy.forType(workoutType))
        autoPauseController = AutoPauseController(AutoPausePolicy.forType(workoutType)).apply {
            restoreAutoPaused(restoredStatus == WorkoutStatus.AUTO_PAUSED)
        }
        accumulatedDistanceMeters = recomputeDistance(relation.route)
        accumulatedElapsedMillis = effective.elapsedMillis.coerceAtLeast(0L)
        elapsedStartedAtElapsedMillis = null
        accumulatedMovingMillis = effective.movingMillis.coerceAtLeast(0L)
        movingStartedAtElapsedMillis = null
        lastAccepted = null
        routePointCount = relation.route.size
        segmentIndex = relation.route.maxOfOrNull { it.segmentIndex } ?: 0
        val last = relation.route.maxWithOrNull(compareBy<RoutePointEntity> { it.segmentIndex }.thenBy { it.elapsedRealtimeMillis })
        val snapshot = effective.copy(distanceMeters = accumulatedDistanceMeters).toSnapshot(
            workoutType, routePointCount, last?.accuracyMeters, last?.timestampMillis
        )
        _state.value = snapshot
        snapshot
    }

    suspend fun requireRecovery(wallClockMillis: Long, elapsedRealtimeMillis: Long): Boolean = mutex.withLock {
        val id = activeWorkoutId ?: return false
        val sm = stateMachine ?: return false
        if (sm.state == WorkoutStatus.RECOVERY_REQUIRED) return true
        if (!sm.canTransition(WorkoutStatus.RECOVERY_REQUIRED)) return false

        // Persist first. Runtime ownership is mutated only after durable state agrees.
        persistStatus(id, WorkoutStatus.RECOVERY_REQUIRED, wallClockMillis, elapsedRealtimeMillis)
        accumulatedElapsedMillis = currentElapsedMillis(elapsedRealtimeMillis)
        elapsedStartedAtElapsedMillis = null
        if (sm.state == WorkoutStatus.ACTIVE) closeMovingSegment(elapsedRealtimeMillis)
        check(sm.transition(WorkoutStatus.RECOVERY_REQUIRED))
        autoPauseController?.reset()
        filter?.reset()
        lastAccepted = null
        true
    }

    suspend fun resumeRecovered(wallClockMillis: Long, elapsedRealtimeMillis: Long): Boolean = mutex.withLock {
        val id = activeWorkoutId ?: return false
        val sm = stateMachine ?: return false
        if (sm.state != WorkoutStatus.RECOVERY_REQUIRED || !sm.canTransition(WorkoutStatus.ACTIVE)) return false

        persistStatus(id, WorkoutStatus.ACTIVE, wallClockMillis, elapsedRealtimeMillis)
        check(sm.transition(WorkoutStatus.ACTIVE))
        segmentIndex += 1
        elapsedStartedAtElapsedMillis = elapsedRealtimeMillis
        movingStartedAtElapsedMillis = elapsedRealtimeMillis
        filter?.reset()
        autoPauseController?.reset()
        lastAccepted = null
        true
    }

    suspend fun onLocation(sample: LocationSample, elapsedRealtimeMillis: Long, autoPauseEnabled: Boolean): Boolean = mutex.withLock {
        val id = activeWorkoutId ?: return false
        val sm = stateMachine ?: return false
        val workoutType = type ?: return false
        val monotonic = sample.monotonicMillis ?: elapsedRealtimeMillis
        _state.value = _state.value?.copy(
            gpsAvailable = true,
            lastAccuracyMeters = sample.accuracyMeters,
            lastLocationAtMillis = sample.timestampMillis,
            lastLocationMonotonicMillis = monotonic,
        )

        if (sm.state == WorkoutStatus.MANUAL_PAUSED || sm.state == WorkoutStatus.FINISHING || sm.state == WorkoutStatus.RECOVERY_REQUIRED) {
            return false
        }

        // Turning auto-pause off while currently auto-paused must resume deterministically.
        if (!autoPauseEnabled && sm.state == WorkoutStatus.AUTO_PAUSED && sm.canTransition(WorkoutStatus.ACTIVE)) {
            persistStatus(id, WorkoutStatus.ACTIVE, sample.timestampMillis, elapsedRealtimeMillis)
            check(sm.transition(WorkoutStatus.ACTIVE))
            segmentIndex += 1
            movingStartedAtElapsedMillis = elapsedRealtimeMillis
            autoPauseController?.reset()
            filter?.reset()
            lastAccepted = null
        }

        if (autoPauseEnabled && (sm.state == WorkoutStatus.ACTIVE || sm.state == WorkoutStatus.AUTO_PAUSED)) {
            when (autoPauseController?.update(monotonic, sample.speedMps?.toDouble(), sample.accuracyMeters, manualPaused = false)) {
                AutoPauseEvent.Pause -> {
                    if (sm.state == WorkoutStatus.ACTIVE && sm.canTransition(WorkoutStatus.AUTO_PAUSED)) {
                        persistStatus(id, WorkoutStatus.AUTO_PAUSED, sample.timestampMillis, elapsedRealtimeMillis)
                        closeMovingSegment(elapsedRealtimeMillis)
                        check(sm.transition(WorkoutStatus.AUTO_PAUSED))
                        filter?.reset()
                        lastAccepted = null
                    }
                    return false
                }
                AutoPauseEvent.Resume -> {
                    if (sm.state == WorkoutStatus.AUTO_PAUSED && sm.canTransition(WorkoutStatus.ACTIVE)) {
                        persistStatus(id, WorkoutStatus.ACTIVE, sample.timestampMillis, elapsedRealtimeMillis)
                        check(sm.transition(WorkoutStatus.ACTIVE))
                        segmentIndex += 1
                        movingStartedAtElapsedMillis = elapsedRealtimeMillis
                        filter?.reset()
                        lastAccepted = null
                    }
                }
                AutoPauseEvent.None, null -> Unit
            }
        }

        if (sm.state != WorkoutStatus.ACTIVE) return false
        val f = filter ?: return false
        if (f.evaluate(sample) !is GpsValidation.Accepted) return false

        val previous = lastAccepted
        val segmentDistance = previous?.let { Geo.distanceMeters(it, sample) } ?: 0.0
        val candidateDistance = accumulatedDistanceMeters + segmentDistance
        val moving = currentMovingMillis(elapsedRealtimeMillis)
        val elapsed = currentElapsedMillis(elapsedRealtimeMillis)
        val point = RoutePointEntity(
            workoutId = id,
            timestampMillis = sample.timestampMillis,
            elapsedRealtimeMillis = monotonic,
            movingElapsedMillis = moving,
            segmentIndex = segmentIndex,
            latitude = sample.latitude,
            longitude = sample.longitude,
            accuracyMeters = sample.accuracyMeters,
            altitudeMeters = sample.altitudeMeters,
            speedMps = sample.speedMps,
            bearingDegrees = sample.bearingDegrees,
            provider = sample.provider,
        )
        val workout = requireNotNull(dao.getWorkout(id))
        val metrics = WorkoutMath.metrics(candidateDistance, elapsed, moving)
        val goalReachedAt = workout.goalReachedAt ?: if (isGoalReached(workout.toGoal(), metrics)) sample.timestampMillis else null
        val updated = workout.copy(
            elapsedMillis = metrics.elapsedMillis,
            movingMillis = metrics.movingMillis,
            distanceMeters = metrics.distanceMeters,
            averageSpeedMps = metrics.averageSpeedMps,
            goalReachedAt = goalReachedAt,
            updatedAt = sample.timestampMillis,
        )

        // Commit DB first; only then advance in-memory filter/distance ownership.
        db.withTransaction {
            dao.insertRoutePoint(point)
            check(dao.updateWorkout(updated) == 1)
        }
        accumulatedDistanceMeters = candidateDistance
        lastAccepted = sample
        f.accept(sample)
        routePointCount += 1
        _state.value = updated.toSnapshot(
            workoutType,
            routePointCount,
            sample.accuracyMeters,
            sample.timestampMillis,
            gpsAvailable = true,
            lastLocationMonotonicMillis = monotonic,
        )
        true
    }

    suspend fun reportGpsAvailability(available: Boolean) = mutex.withLock {
        _state.value = _state.value?.copy(gpsAvailable = available)
    }

    suspend fun onHeartRateSample(bpm: Int, wallClockMillis: Long, elapsedRealtimeMillis: Long): Boolean = mutex.withLock {
        if (bpm !in 25..250) return false
        val id = activeWorkoutId ?: return false
        val status = stateMachine?.state ?: return false
        if (status != WorkoutStatus.ACTIVE) return false
        dao.insertHeartRateSample(
            HeartRateSampleEntity(
                workoutId = id,
                timestampMillis = wallClockMillis,
                elapsedRealtimeMillis = elapsedRealtimeMillis,
                bpm = bpm,
                source = "BLE_HRS",
            )
        ) > 0
    }

    suspend fun manualPause(wallClockMillis: Long, elapsedRealtimeMillis: Long): Boolean = mutex.withLock {
        val id = activeWorkoutId ?: return false
        val sm = stateMachine ?: return false
        if (sm.state == WorkoutStatus.MANUAL_PAUSED) return true
        if (!sm.canTransition(WorkoutStatus.MANUAL_PAUSED)) return false

        persistStatus(id, WorkoutStatus.MANUAL_PAUSED, wallClockMillis, elapsedRealtimeMillis)
        if (sm.state == WorkoutStatus.ACTIVE) closeMovingSegment(elapsedRealtimeMillis)
        check(sm.transition(WorkoutStatus.MANUAL_PAUSED))
        autoPauseController?.reset()
        filter?.reset()
        lastAccepted = null
        true
    }

    suspend fun resume(wallClockMillis: Long, elapsedRealtimeMillis: Long): Boolean = mutex.withLock {
        val id = activeWorkoutId ?: return false
        val sm = stateMachine ?: return false
        if ((sm.state != WorkoutStatus.MANUAL_PAUSED && sm.state != WorkoutStatus.AUTO_PAUSED) || !sm.canTransition(WorkoutStatus.ACTIVE)) return false

        persistStatus(id, WorkoutStatus.ACTIVE, wallClockMillis, elapsedRealtimeMillis)
        check(sm.transition(WorkoutStatus.ACTIVE))
        segmentIndex += 1
        movingStartedAtElapsedMillis = elapsedRealtimeMillis
        filter?.reset()
        autoPauseController?.reset()
        lastAccepted = null
        true
    }

    /** Stop recording and persist a FINISHING snapshot. This is intentionally not a success/COMPLETED operation. */
    suspend fun requestFinish(wallClockMillis: Long, elapsedRealtimeMillis: Long): String? = mutex.withLock {
        val id = activeWorkoutId ?: return null
        val sm = stateMachine ?: return null
        if (sm.state == WorkoutStatus.FINISHING) return id
        if (!sm.canTransition(WorkoutStatus.FINISHING)) return null

        val current = requireNotNull(dao.getWorkout(id))
        val elapsed = currentElapsedMillis(elapsedRealtimeMillis)
        val moving = currentMovingMillis(elapsedRealtimeMillis)
        val metrics = WorkoutMath.metrics(accumulatedDistanceMeters, elapsed, moving)
        val goalReachedAt = current.goalReachedAt ?: if (isGoalReached(current.toGoal(), metrics)) wallClockMillis else null
        val finishing = current.copy(
            status = WorkoutStatus.FINISHING.name,
            endedAt = wallClockMillis,
            elapsedMillis = metrics.elapsedMillis,
            movingMillis = metrics.movingMillis,
            distanceMeters = metrics.distanceMeters,
            averageSpeedMps = metrics.averageSpeedMps,
            goalReachedAt = goalReachedAt,
            updatedAt = wallClockMillis,
        )
        check(dao.updateWorkout(finishing) == 1)

        accumulatedElapsedMillis = elapsed
        elapsedStartedAtElapsedMillis = null
        if (sm.state == WorkoutStatus.ACTIVE) closeMovingSegment(elapsedRealtimeMillis)
        check(sm.transition(WorkoutStatus.FINISHING))
        autoPauseController?.reset()
        filter?.reset()
        lastAccepted = null
        _state.value = finishing.toSnapshot(requireNotNull(type), routePointCount, _state.value?.lastAccuracyMeters, _state.value?.lastLocationAtMillis)
        id
    }

    /** Transactional final save. Navigation to a result screen must happen only after this returns an id. */
    suspend fun commitFinish(wallClockMillis: Long, weightKg: Double?): String? = mutex.withLock {
        val id = activeWorkoutId ?: return null
        val sm = stateMachine ?: return null
        val current = requireNotNull(dao.getWorkoutWithRoute(id))
        if (current.workout.status == WorkoutStatus.COMPLETED.name) {
            clearRuntime()
            return id
        }
        if (sm.state != WorkoutStatus.FINISHING) return null

        val workoutType = requireNotNull(type)
        val routeSegments = current.route
            .groupBy { it.segmentIndex }
            .toSortedMap()
            .values
            .map { segment -> segment.sortedBy { it.movingElapsedMillis }.map { it.toSample() } }
        val elevation = WorkoutMath.elevationGainLossSegments(routeSegments)
        val heartRates = current.heartRateSamples.map { it.bpm }.filter { it in 25..250 }
        val heartRateAverage = heartRates.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val heartRateMax = heartRates.maxOrNull()
        val calories = WorkoutMath.estimatedCalories(workoutType, current.workout.movingMillis, current.workout.distanceMeters, weightKg)
        val finalEntity = current.workout.copy(
            status = WorkoutStatus.COMPLETED.name,
            caloriesEstimate = calories,
            heartRateAverageBpm = heartRateAverage,
            heartRateMaxBpm = heartRateMax,
            elevationGainMeters = elevation?.first,
            elevationLossMeters = elevation?.second,
            updatedAt = wallClockMillis,
        )
        try {
            db.withTransaction {
                check(dao.updateWorkout(finalEntity) == 1) { "final workout update failed" }
            }
        } catch (t: Throwable) {
            // The transaction rolled back: keep FINISHING both in DB and runtime so Save is safely retryable.
            _state.value = current.workout.toSnapshot(
                workoutType, routePointCount, _state.value?.lastAccuracyMeters, _state.value?.lastLocationAtMillis
            )
            throw t
        }
        check(sm.transition(WorkoutStatus.COMPLETED))
        // Room is now the source of truth for completed results. Clear live ownership so a late collector
        // cannot resurrect the finished session or restart the UI ticker after Save.
        clearRuntime()
        id
    }

    /** Used only after explicit destructive user confirmation. Stops in-memory ownership before DB wipe. */
    suspend fun resetForDeleteAll() = mutex.withLock {
        clearRuntime()
    }

    suspend fun snapshot(nowElapsedRealtimeMillis: Long): TrackingSnapshot? = mutex.withLock {
        val base = _state.value ?: return null
        val liveElapsed = when (base.status) {
            WorkoutStatus.ACTIVE, WorkoutStatus.MANUAL_PAUSED, WorkoutStatus.AUTO_PAUSED -> currentElapsedMillis(nowElapsedRealtimeMillis)
            else -> base.elapsedMillis
        }
        val liveMoving = if (base.status == WorkoutStatus.ACTIVE) currentMovingMillis(nowElapsedRealtimeMillis) else base.movingMillis
        val metrics = WorkoutMath.metrics(accumulatedDistanceMeters, liveElapsed, liveMoving)
        base.copy(
            goalReached = base.goalReached || isGoalReached(base.goal, metrics),
            elapsedMillis = metrics.elapsedMillis,
            movingMillis = metrics.movingMillis,
            distanceMeters = metrics.distanceMeters,
            averageSpeedMps = metrics.averageSpeedMps,
        )
    }

    /** Periodic durability checkpoint for stationary workouts where no accepted GPS point arrives. */
    suspend fun checkpoint(wallClockMillis: Long, elapsedRealtimeMillis: Long): Boolean = mutex.withLock {
        val id = activeWorkoutId ?: return false
        val status = stateMachine?.state ?: return false
        if (status !in setOf(WorkoutStatus.ACTIVE, WorkoutStatus.MANUAL_PAUSED, WorkoutStatus.AUTO_PAUSED)) return false
        persistStatus(id, status, wallClockMillis, elapsedRealtimeMillis)
        true
    }

    private suspend fun persistStatus(id: String, status: WorkoutStatus, wallClockMillis: Long, elapsedRealtimeMillis: Long) {
        val workout = requireNotNull(dao.getWorkout(id))
        val elapsed = currentElapsedMillis(elapsedRealtimeMillis)
        val moving = currentMovingMillis(elapsedRealtimeMillis)
        val metrics = WorkoutMath.metrics(accumulatedDistanceMeters, elapsed, moving)
        val goalReachedAt = workout.goalReachedAt ?: if (isGoalReached(workout.toGoal(), metrics)) wallClockMillis else null
        val updated = workout.copy(
            status = status.name,
            goalReachedAt = goalReachedAt,
            elapsedMillis = metrics.elapsedMillis,
            movingMillis = metrics.movingMillis,
            distanceMeters = metrics.distanceMeters,
            averageSpeedMps = metrics.averageSpeedMps,
            updatedAt = wallClockMillis,
        )
        check(dao.updateWorkout(updated) == 1)
        _state.value = updated.toSnapshot(
            requireNotNull(type),
            routePointCount,
            _state.value?.lastAccuracyMeters,
            _state.value?.lastLocationAtMillis,
            gpsAvailable = _state.value?.gpsAvailable ?: false,
            lastLocationMonotonicMillis = _state.value?.lastLocationMonotonicMillis,
        )
    }

    private suspend fun markRecoveryRequired(entity: WorkoutEntity, wallClockMillis: Long) {
        dao.updateWorkout(entity.copy(status = WorkoutStatus.RECOVERY_REQUIRED.name, updatedAt = wallClockMillis))
    }

    private fun currentElapsedMillis(elapsedRealtimeMillis: Long): Long {
        val segmentStart = elapsedStartedAtElapsedMillis ?: return accumulatedElapsedMillis
        return accumulatedElapsedMillis + (elapsedRealtimeMillis - segmentStart).coerceAtLeast(0)
    }

    private fun currentMovingMillis(elapsedRealtimeMillis: Long): Long {
        val segmentStart = movingStartedAtElapsedMillis ?: return accumulatedMovingMillis
        return accumulatedMovingMillis + (elapsedRealtimeMillis - segmentStart).coerceAtLeast(0)
    }

    private fun closeMovingSegment(elapsedRealtimeMillis: Long) {
        accumulatedMovingMillis = currentMovingMillis(elapsedRealtimeMillis)
        movingStartedAtElapsedMillis = null
    }

    private fun recomputeDistance(route: List<RoutePointEntity>): Double = route
        .groupBy { it.segmentIndex }
        .values
        .sumOf { segment ->
            segment.sortedBy { it.movingElapsedMillis }
                .zipWithNext()
                .sumOf { (a, b) -> Geo.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) }
        }

    private fun RoutePointEntity.toSample() = LocationSample(
        timestampMillis = timestampMillis,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        altitudeMeters = altitudeMeters,
        speedMps = speedMps,
        bearingDegrees = bearingDegrees,
        provider = provider,
        monotonicMillis = elapsedRealtimeMillis,
    )

    private fun WorkoutEntity.toSnapshot(
        type: WorkoutType,
        routePointCount: Int,
        lastAccuracyMeters: Float?,
        lastLocationAtMillis: Long?,
        gpsAvailable: Boolean = false,
        lastLocationMonotonicMillis: Long? = null,
    ) = TrackingSnapshot(
        workoutId = id,
        type = type,
        goal = toGoal(),
        goalReached = goalReachedAt != null,
        status = runCatching { WorkoutStatus.valueOf(status) }.getOrDefault(WorkoutStatus.RECOVERY_REQUIRED),
        startedAtMillis = startedAt,
        startedElapsedRealtimeMillis = startedElapsedRealtimeMillis,
        endedAtMillis = endedAt,
        elapsedMillis = elapsedMillis,
        movingMillis = movingMillis,
        distanceMeters = distanceMeters,
        averageSpeedMps = averageSpeedMps,
        caloriesEstimate = caloriesEstimate,
        routePointCount = routePointCount,
        lastAccuracyMeters = lastAccuracyMeters,
        lastLocationAtMillis = lastLocationAtMillis,
        gpsAvailable = gpsAvailable,
        lastLocationMonotonicMillis = lastLocationMonotonicMillis,
    )

    private fun WorkoutEntity.toGoal(): WorkoutGoal {
        val kind = runCatching { GoalKind.valueOf(goalKind) }.getOrDefault(GoalKind.NONE)
        return WorkoutGoal(kind = kind, distanceMeters = goalDistanceMeters, durationMillis = goalDurationMillis)
    }

    private fun isGoalReached(goal: WorkoutGoal, metrics: WorkoutMetrics): Boolean = when (goal.kind) {
        GoalKind.NONE -> false
        GoalKind.DISTANCE -> goal.distanceMeters?.let { metrics.distanceMeters >= it } == true
        GoalKind.DURATION -> goal.durationMillis?.let { metrics.elapsedMillis >= it } == true
    }

    private fun clearRuntime(clearPublishedState: Boolean = true) {
        activeWorkoutId = null
        type = null
        stateMachine = null
        filter = null
        autoPauseController = null
        lastAccepted = null
        accumulatedDistanceMeters = 0.0
        accumulatedElapsedMillis = 0L
        elapsedStartedAtElapsedMillis = null
        accumulatedMovingMillis = 0L
        movingStartedAtElapsedMillis = null
        routePointCount = 0
        segmentIndex = 0
        if (clearPublishedState) _state.value = null
    }
}
