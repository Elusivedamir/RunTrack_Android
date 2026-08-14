package com.runtrack.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runtrack.app.data.WorkoutEntity
import com.runtrack.app.data.WorkoutWithRoute
import com.runtrack.app.domain.*
import com.runtrack.app.health.HealthConnectAvailability
import com.runtrack.app.health.HealthConnectManager
import com.runtrack.app.settings.RunTrackSettings
import com.runtrack.app.tracking.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.time.temporal.TemporalAdjusters

sealed interface UiOperationState {
    data object Idle : UiOperationState
    data class Running(val name: String) : UiOperationState
    data class Error(val message: String) : UiOperationState
}

data class DailyStatPoint(val epochDay: Long, val distanceMeters: Double, val durationMillis: Long)

data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val permissionsGranted: Boolean = false,
    val inProgress: Boolean = false,
    val message: String? = null,
    val lastExportedWorkoutId: String? = null,
)

data class RunTrackStatsUiState(
    val all: PeriodStats = StatisticsCalculator.aggregate(emptyList()),
    val currentWeek: PeriodStats = StatisticsCalculator.aggregate(emptyList()),
    val previousWeek: PeriodStats = StatisticsCalculator.aggregate(emptyList()),
    val weeklyDistanceChangePercent: Double? = null,
    val currentWeekRunPaceSecondsPerKm: Double? = null,
    val previousWeekRunPaceSecondsPerKm: Double? = null,
    val longestRun: RecordResult? = null,
    val maxElevationGain: RecordResult? = null,
    val fastest5kMillis: Pair<String, Long>? = null,
    val best1kMillis: Pair<String, Long>? = null,
    val daily: List<DailyStatPoint> = emptyList(),
)

class RunTrackViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: Context = application.applicationContext
    private val runtime = RunTrackRuntime.apply { initialize(appContext) }
    private val dao = runtime.database.workoutDao()
    private val tracking = runtime.trackingRepository
    private val settingsRepo = runtime.settingsRepository
    private val backupManager = runtime.backupManager
    private val exportManager = runtime.exportManager
    private val heartRateManager = runtime.heartRateManager
    private val resultNotificationManager = runtime.resultNotificationManager
    private val healthConnectManager = HealthConnectManager(appContext)
    private val gpsChecker = GpsReadinessChecker(appContext)
    private val actionMutex = Mutex()

    val settings: StateFlow<RunTrackSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunTrackSettings())

    val history: StateFlow<List<WorkoutEntity>> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val latestWorkout: StateFlow<WorkoutEntity?> = dao.observeLatestWorkout()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedWorkoutId = MutableStateFlow<String?>(null)
    val selectedWorkoutId: StateFlow<String?> = _selectedWorkoutId.asStateFlow()

    val selectedWorkout: StateFlow<WorkoutWithRoute?> = _selectedWorkoutId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else dao.observeWorkoutWithRoute(id).onStart { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _allRoutes = MutableStateFlow<List<WorkoutWithRoute>>(emptyList())
    val allRoutes: StateFlow<List<WorkoutWithRoute>> = _allRoutes.asStateFlow()

    private val _workoutType = MutableStateFlow(WorkoutType.RUN)
    val workoutType: StateFlow<WorkoutType> = _workoutType.asStateFlow()

    private val _goal = MutableStateFlow(WorkoutGoal())
    val goal: StateFlow<WorkoutGoal> = _goal.asStateFlow()

    private val _gpsReadiness = MutableStateFlow(gpsChecker.basicStatus())
    val gpsReadiness: StateFlow<GpsReadiness> = _gpsReadiness.asStateFlow()

    private val _operation = MutableStateFlow<UiOperationState>(UiOperationState.Idle)
    val operation: StateFlow<UiOperationState> = _operation.asStateFlow()

    private val _stats = MutableStateFlow(RunTrackStatsUiState())
    val stats: StateFlow<RunTrackStatsUiState> = _stats.asStateFlow()

    private val _liveTracking = MutableStateFlow<TrackingSnapshot?>(null)
    val liveTracking: StateFlow<TrackingSnapshot?> = _liveTracking.asStateFlow()
    val heartRateState: StateFlow<BleHeartRateState> = heartRateManager.state
    val heartRateDevices: StateFlow<List<BleHeartRateDevice>> = heartRateManager.devices
    private val _healthConnectState = MutableStateFlow(
        HealthConnectUiState(availability = healthConnectManager.availability())
    )
    val healthConnectState: StateFlow<HealthConnectUiState> = _healthConnectState.asStateFlow()
    val healthConnectPermissions: Set<String> = HealthConnectManager.REQUIRED_PERMISSIONS
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val restored = tracking.restoreRecoverable(System.currentTimeMillis(), SystemClock.elapsedRealtime())
            _liveTracking.value = restored
            if (restored != null) {
                _selectedWorkoutId.value = restored.workoutId
                _workoutType.value = restored.type
                _goal.value = restored.goal
            }
            ensureTicker(restored != null)
        }
        viewModelScope.launch {
            tracking.state.collect { snapshot ->
                _liveTracking.value = snapshot
                if (snapshot != null) {
                    _selectedWorkoutId.value = snapshot.workoutId
                    _workoutType.value = snapshot.type
                    _goal.value = snapshot.goal
                }
                ensureTicker(snapshot != null)
            }
        }
        viewModelScope.launch {
            history.collectLatest { completed ->
                val relations = buildList {
                    for (workout in completed) dao.getWorkoutWithRoute(workout.id)?.let(::add)
                }
                _allRoutes.value = relations
                _stats.value = buildStats(completed, relations)
            }
        }
        refreshHealthConnect()
    }

    fun chooseWorkoutType(type: WorkoutType) { _workoutType.value = type }
    fun setGoal(goal: WorkoutGoal) { if (goal.isValid()) _goal.value = goal }
    fun clearGoal() { _goal.value = WorkoutGoal() }
    fun selectWorkout(id: String) { _selectedWorkoutId.value = id }

    fun refreshGpsReadiness() {
        viewModelScope.launch {
            _gpsReadiness.value = gpsChecker.checkCurrentFix(GpsFilterPolicy.forType(_workoutType.value).maxAccuracyMeters)
        }
    }

    fun onPermissionStateChanged() {
        _gpsReadiness.value = gpsChecker.basicStatus()
        if (_gpsReadiness.value.finePermissionGranted && _gpsReadiness.value.locationEnabled) refreshGpsReadiness()
    }

    fun startWorkout(onStarted: (String) -> Unit = {}) = launchExclusive("start") {
        val goal = _goal.value
        if (!goal.isValid()) error("Некорректная цель тренировки")
        val gps = gpsChecker.checkCurrentFix(GpsFilterPolicy.forType(_workoutType.value).maxAccuracyMeters)
        _gpsReadiness.value = gps
        check(gps.ready) { gpsFailureMessage(gps) }

        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val id = tracking.start(_workoutType.value, goal, nowWall, nowElapsed)
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_START_UPDATES),
            )
        } catch (t: Throwable) {
            tracking.requireRecovery(System.currentTimeMillis(), SystemClock.elapsedRealtime())
            throw IllegalStateException("Не удалось запустить GPS-службу", t)
        }
        _selectedWorkoutId.value = id
        _liveTracking.value = tracking.snapshot(SystemClock.elapsedRealtime())
        ensureTicker(true)
        onStarted(id)
    }

    fun pauseWorkout(onPaused: () -> Unit = {}) = launchExclusive("pause") {
        if (tracking.manualPause(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            appContext.startService(Intent(appContext, WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_PAUSE_UPDATES))
            _liveTracking.value = tracking.snapshot(SystemClock.elapsedRealtime())
            onPaused()
        }
    }

    fun resumeWorkout(onResumed: () -> Unit = {}) = launchExclusive("resume") {
        if (tracking.resume(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            appContext.startService(Intent(appContext, WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_RESUME_UPDATES))
            _liveTracking.value = tracking.snapshot(SystemClock.elapsedRealtime())
            onResumed()
        }
    }

    fun resumeRecoveredWorkout(onResumed: () -> Unit = {}) = launchExclusive("recover") {
        if (tracking.resumeRecovered(System.currentTimeMillis(), SystemClock.elapsedRealtime())) {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_RESUME_UPDATES),
            )
            _liveTracking.value = tracking.snapshot(SystemClock.elapsedRealtime())
            onResumed()
        }
    }

    fun requestFinish(onReadyToSave: (String) -> Unit = {}) = launchExclusive("finish") {
        val id = tracking.requestFinish(System.currentTimeMillis(), SystemClock.elapsedRealtime()) ?: return@launchExclusive
        appContext.startService(Intent(appContext, WorkoutTrackingService::class.java).setAction(WorkoutTrackingService.ACTION_STOP_UPDATES))
        _selectedWorkoutId.value = id
        _liveTracking.value = tracking.snapshot(SystemClock.elapsedRealtime())
        onReadyToSave(id)
    }

    fun saveFinishedWorkout(onSaved: (String) -> Unit = {}) = launchExclusive("save") {
        val id = tracking.commitFinish(System.currentTimeMillis(), settings.value.weightKg) ?: return@launchExclusive
        val saved = dao.getWorkout(id)
        if (saved != null) resultNotificationManager.showCompleted(saved, settings.value.units, settings.value.notificationsEnabled)
        _selectedWorkoutId.value = id
        _liveTracking.value = null
        _goal.value = WorkoutGoal()
        ensureTicker(false)
        onSaved(id)
    }

    fun deleteWorkout(id: String, onDeleted: () -> Unit = {}) = launchExclusive("delete") {
        check(dao.deleteWorkoutTransactional(id)) { "Тренировка не найдена" }
        if (_selectedWorkoutId.value == id) _selectedWorkoutId.value = null
        onDeleted()
    }

    fun refreshBluetoothState() = heartRateManager.refreshSystemState()
    fun startHeartRateScan() = heartRateManager.startScan()
    fun cancelHeartRateScan() = heartRateManager.cancelScan()
    fun connectHeartRateDevice(address: String) = heartRateManager.connect(address)
    fun connectSavedHeartRateDevice() { settings.value.heartRateDeviceAddress?.let(heartRateManager::connectSaved) }
    fun disconnectHeartRateDevice() = heartRateManager.disconnect()

    fun refreshHealthConnect() {
        viewModelScope.launch {
            val availability = healthConnectManager.availability()
            val granted = if (availability == HealthConnectAvailability.AVAILABLE) {
                try {
                    healthConnectManager.hasAllPermissions()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
            _healthConnectState.value = _healthConnectState.value.copy(
                availability = availability,
                permissionsGranted = granted,
                inProgress = false,
                message = null,
            )
        }
    }

    fun onHealthConnectPermissionsResult(granted: Set<String>) {
        val allGranted = healthConnectPermissions.all(granted::contains)
        _healthConnectState.value = _healthConnectState.value.copy(
            permissionsGranted = allGranted,
            message = if (allGranted) "Health Connect подключён" else "Разрешения Health Connect не выданы полностью",
        )
    }

    fun healthConnectSettingsIntent(): Intent = healthConnectManager.settingsOrInstallIntent()

    fun exportSelectedWorkoutToHealthConnect() {
        viewModelScope.launch {
            actionMutex.withLock {
                val selectedId = _selectedWorkoutId.value
                if (selectedId == null) {
                    _healthConnectState.value = _healthConnectState.value.copy(
                        inProgress = false,
                        message = "Сначала откройте сохранённую тренировку",
                    )
                    return@withLock
                }
                val workout = dao.getWorkout(selectedId)
                if (workout == null) {
                    _healthConnectState.value = _healthConnectState.value.copy(
                        inProgress = false,
                        message = "Тренировка не найдена",
                    )
                    return@withLock
                }

                _healthConnectState.value = _healthConnectState.value.copy(
                    inProgress = true,
                    message = null,
                )
                try {
                    val result = healthConnectManager.exportWorkout(workout)
                    _healthConnectState.value = _healthConnectState.value.copy(
                        permissionsGranted = true,
                        inProgress = false,
                        message = "Тренировка записана в Health Connect",
                        lastExportedWorkoutId = result.workoutId,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _healthConnectState.value = _healthConnectState.value.copy(
                        inProgress = false,
                        message = error.message ?: "Не удалось записать тренировку в Health Connect",
                    )
                }
            }
        }
    }

    fun clearOperationError() { if (_operation.value is UiOperationState.Error) _operation.value = UiOperationState.Idle }

    fun setNotificationsEnabled(value: Boolean) = viewModelScope.launch { settingsRepo.setNotificationsEnabled(value) }
    fun setAutoPauseEnabled(value: Boolean) = viewModelScope.launch { settingsRepo.setAutoPauseEnabled(value) }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { settingsRepo.setKeepScreenOn(value) }
    fun setUnits(value: UnitSystem) = viewModelScope.launch { settingsRepo.setUnits(value) }
    fun setMapLayer(value: MapLayer) = viewModelScope.launch { settingsRepo.setMapLayer(value) }
    fun setWeightKg(value: Double?) = viewModelScope.launch { settingsRepo.setWeightKg(value) }
    fun setProfileName(value: String) = viewModelScope.launch { settingsRepo.setProfileName(value) }

    fun createEncryptedBackup(passphrase: String, onReady: (ByteArray) -> Unit) = launchExclusive("backup") {
        val bytes = backupManager.buildEncrypted(passphrase.toCharArray())
        onReady(bytes)
    }

    fun saveBackupToUri(uri: Uri, bytes: ByteArray, onSaved: () -> Unit = {}) = launchExclusive("backup-save") {
        backupManager.writeToUri(uri, bytes)
        onSaved()
    }

    fun restoreEncryptedBackup(uri: Uri, passphrase: String, onRestored: (Int) -> Unit = {}) = launchExclusive("restore") {
        check(_liveTracking.value == null) { "Завершите активную тренировку перед восстановлением" }
        val bytes = backupManager.readFromUri(uri)
        val count = backupManager.restoreEncrypted(bytes, passphrase.toCharArray())
        onRestored(count)
    }

    fun deleteAllData(onDeleted: () -> Unit = {}) = launchExclusive("delete-all") {
        appContext.stopService(Intent(appContext, WorkoutTrackingService::class.java))
        tracking.resetForDeleteAll()
        dao.deleteAllWorkouts()
        settingsRepo.clearAll()
        exportManager.clearTemporaryExports()
        _selectedWorkoutId.value = null
        _liveTracking.value = null
        _allRoutes.value = emptyList()
        _stats.value = RunTrackStatsUiState()
        ensureTicker(false)
        onDeleted()
    }

    private fun launchExclusive(name: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            actionMutex.withLock {
                _operation.value = UiOperationState.Running(name)
                try {
                    block()
                    _operation.value = UiOperationState.Idle
                } catch (t: Throwable) {
                    _operation.value = UiOperationState.Error(t.message ?: "Операция не выполнена")
                }
            }
        }
    }

    private fun ensureTicker(enabled: Boolean) {
        if (!enabled) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                _liveTracking.value = tracking.snapshot(SystemClock.elapsedRealtime()) ?: _liveTracking.value
                delay(1_000L)
            }
        }
    }

    private suspend fun buildStats(completed: List<WorkoutEntity>, relations: List<WorkoutWithRoute>): RunTrackStatsUiState {
        val summaries = withContext(Dispatchers.Default) { completed.mapNotNull { it.toSummaryOrNull() } }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val previousStart = weekStart.minusWeeks(1)
        fun inRange(summary: WorkoutSummary, start: LocalDate, endExclusive: LocalDate): Boolean {
            val date = Instant.ofEpochMilli(summary.startedAtMillis).atZone(zone).toLocalDate()
            return !date.isBefore(start) && date.isBefore(endExclusive)
        }
        val currentWeekSummaries = summaries.filter { inRange(it, weekStart, weekStart.plusWeeks(1)) }
        val previousWeekSummaries = summaries.filter { inRange(it, previousStart, weekStart) }
        val currentWeek = StatisticsCalculator.aggregate(currentWeekSummaries)
        val previousWeek = StatisticsCalculator.aggregate(previousWeekSummaries)
        fun runPace(items: List<WorkoutSummary>): Double? {
            val runs = items.filter { it.type == WorkoutType.RUN && it.distanceMeters > 0.0 && it.movingMillis > 0L }
            val distance = runs.sumOf { it.distanceMeters }
            val moving = runs.sumOf { it.movingMillis }
            return if (distance > 0.0 && moving > 0L) moving.toDouble() / distance else null
        }
        val currentRunPace = runPace(currentWeekSummaries)
        val previousRunPace = runPace(previousWeekSummaries)
        val runRelations = relations.filter { it.workout.type == WorkoutType.RUN.name }
        val recordPairs = withContext(Dispatchers.Default) {
            runRelations.map { relation ->
                val points = relation.route.sortedBy { it.movingElapsedMillis }.map { point ->
                    TimedRoutePoint(
                        LocationSample(
                            timestampMillis = point.timestampMillis,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            accuracyMeters = point.accuracyMeters,
                            altitudeMeters = point.altitudeMeters,
                            speedMps = point.speedMps,
                            bearingDegrees = point.bearingDegrees,
                            provider = point.provider,
                            monotonicMillis = point.elapsedRealtimeMillis,
                        ),
                        point.movingElapsedMillis,
                        point.segmentIndex,
                    )
                }
                Triple(
                    relation.workout.id,
                    RecordCalculator.bestDistanceWindowMillis(points, 5_000.0),
                    RecordCalculator.bestDistanceWindowMillis(points, 1_000.0),
                )
            }
        }
        val fastest5k = recordPairs.mapNotNull { (id, value, _) -> value?.let { id to it } }.minByOrNull { it.second }
        val best1k = recordPairs.mapNotNull { (id, _, value) -> value?.let { id to it } }.minByOrNull { it.second }
        val daily = withContext(Dispatchers.Default) {
            summaries.groupBy { Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate().toEpochDay() }
                .map { (day, items) -> DailyStatPoint(day, items.sumOf { it.distanceMeters }, items.sumOf { it.elapsedMillis }) }
                .sortedBy { it.epochDay }
        }
        return RunTrackStatsUiState(
            all = StatisticsCalculator.aggregate(summaries),
            currentWeek = currentWeek,
            previousWeek = previousWeek,
            weeklyDistanceChangePercent = StatisticsCalculator.percentChange(currentWeek.distanceMeters, previousWeek.distanceMeters),
            currentWeekRunPaceSecondsPerKm = currentRunPace,
            previousWeekRunPaceSecondsPerKm = previousRunPace,
            longestRun = RecordCalculator.longestRun(summaries),
            maxElevationGain = RecordCalculator.maxElevationGain(summaries),
            fastest5kMillis = fastest5k,
            best1kMillis = best1k,
            daily = daily,
        )
    }

    private fun WorkoutEntity.toSummaryOrNull(): WorkoutSummary? {
        val type = runCatching { WorkoutType.valueOf(type) }.getOrNull() ?: return null
        return WorkoutSummary(
            id = id,
            type = type,
            startedAtMillis = startedAt,
            distanceMeters = distanceMeters,
            elapsedMillis = elapsedMillis,
            movingMillis = movingMillis,
            calories = caloriesEstimate,
            elevationGainMeters = elevationGainMeters,
            averageSpeedMps = averageSpeedMps,
        )
    }

    private fun gpsFailureMessage(gps: GpsReadiness): String = when {
        !gps.finePermissionGranted -> "Нужно разрешение на точную геолокацию"
        !gps.locationEnabled -> "Включите геолокацию Android"
        !gps.hasFreshFix -> "GPS ещё не получил достаточно точную позицию"
        else -> "GPS недоступен"
    }
}
