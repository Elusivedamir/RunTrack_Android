package com.runtrack.app.tracking

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.runtrack.app.R
import com.runtrack.app.domain.LocationSample
import com.runtrack.app.domain.StepCounterAccumulator
import com.runtrack.app.domain.WorkoutType
import com.runtrack.app.voice.KilometerAnnouncementTracker
import com.runtrack.app.weather.WeatherUpdateCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest

class WorkoutTrackingService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private lateinit var repository: TrackingRepository
    private lateinit var weatherUpdateCoordinator: WeatherUpdateCoordinator
    private val kilometerAnnouncementTracker = KilometerAnnouncementTracker()
    private val locationRegistration = LocationRegistrationState()
    private val locationCallbackLock = Any()
    private var activeLocationCallback: LocationCallback? = null
    @Volatile private var foregroundStarted = false
    @Volatile private var heartRateConnected = false
    private var checkpointJob: Job? = null
    private var stepSensorRegistered = false
    private var stepWorkoutId: String? = null
    private var stepAccumulator: StepCounterAccumulator? = null
    private val locationBatches = Channel<GenerationBatch<Location>>(Channel.UNLIMITED)

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val accumulator = stepAccumulator ?: return
            val delta = when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    val raw = event.values.firstOrNull()
                        ?.takeIf { it.isFinite() && it >= 0f }
                        ?.toLong()
                        ?: return
                    accumulator.onCounter(raw)
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    if ((event.values.firstOrNull() ?: 0f) > 0f) accumulator.onDetector() else 0L
                }
                else -> 0L
            }
            if (delta > 0L) scope.launch { repository.onStepDelta(delta) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun createLocationCallback(registrationToken: Long) = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!locationRegistration.isRegistered(registrationToken)) return
            if (result.locations.isEmpty()) return
            // Callback runs on the main looper. Preserve callback arrival order and let one
            // consumer own all GPS -> Room sequencing.
            locationBatches.trySend(
                GenerationBatch(
                    generation = registrationToken,
                    items = result.locations.map { Location(it) },
                )
            )
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!locationRegistration.isRegistered(registrationToken)) return
            scope.launch { repository.reportGpsAvailability(availability.isLocationAvailable) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SensorManager::class.java)
        RunTrackRuntime.initialize(this)
        repository = RunTrackRuntime.trackingRepository
        weatherUpdateCoordinator = RunTrackRuntime.weatherUpdateCoordinator
        createNotificationChannel()
        scope.launch {
            consumeGenerationBatchesSafely(
                batches = locationBatches,
                isCurrentGeneration = locationRegistration::isRegistered,
                orderBy = { it.elapsedRealtimeNanos },
                process = ::processLocation,
                onFailure = { error ->
                    recoverAndStop("GPS persistence pipeline failed", error)
                },
            )
        }
        scope.launch {
            repository.state.collectLatest { snapshot ->
                kilometerAnnouncementTracker.onSnapshot(snapshot).forEach {
                    RunTrackRuntime.voiceAnnouncementManager.announceKilometer(it)
                }
                if (foregroundStarted && snapshot != null) {
                    updateNotification(snapshot.notificationText())
                }
            }
        }
        scope.launch {
            RunTrackRuntime.heartRateManager.state.collectLatest { state ->
                val connected = state is BleHeartRateState.Connected
                if (connected != heartRateConnected) {
                    heartRateConnected = connected
                    if (foregroundStarted) {
                        val snapshot = repository.state.value
                        startForegroundSafely(
                            buildNotification(snapshot?.notificationText() ?: "Запись тренировки активна")
                        )
                    }
                }
            }
        }
        checkpointJob = scope.launch {
            while (isActive) {
                delay(CHECKPOINT_INTERVAL_MS)
                val nowWall = System.currentTimeMillis()
                val nowElapsed = SystemClock.elapsedRealtime()
                val fineGranted = ContextCompat.checkSelfPermission(this@WorkoutTrackingService, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!fineGranted) {
                    repository.requireRecovery(nowWall, nowElapsed)
                    stopSelfSafely()
                    break
                }
                val locationEnabled = runCatching {
                    val manager = getSystemService(LocationManager::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        manager.isLocationEnabled
                    } else {
                        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    }
                }.getOrDefault(false)
                repository.reportGpsAvailability(locationEnabled)
                val snapshot = repository.state.value
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    snapshot?.type != WorkoutType.BIKE &&
                    snapshot?.stepTrackingReliable == true &&
                    ContextCompat.checkSelfPermission(
                        this@WorkoutTrackingService,
                        Manifest.permission.ACTIVITY_RECOGNITION,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    unregisterStepSensor()
                    repository.configureStepTracking(false, nowWall)
                }
                val checkpointSucceeded = checkpointWithRecoveryOnFailure(
                    checkpoint = {
                        repository.checkpoint(nowWall, nowElapsed)
                        Unit
                    },
                    onFailure = { error ->
                        recoverAndStop("Durability checkpoint failed", error)
                    },
                )
                if (!checkpointSucceeded) break
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_UPDATES -> {
                if (!ensureForeground(paused = false)) return START_NOT_STICKY
                configureStepSensor(active = true, allowInitialize = true)
                requestLocationUpdatesIfAllowed {
                    repository.state.value?.let {
                        RunTrackRuntime.voiceAnnouncementManager.announceStart(it.workoutId)
                    }
                }
            }
            ACTION_RESUME_UPDATES -> {
                if (!ensureForeground(paused = false)) return START_NOT_STICKY
                configureStepSensor(active = true, allowInitialize = false)
                requestLocationUpdatesIfAllowed()
            }
            ACTION_PAUSE_UPDATES -> {
                if (!ensureForeground(paused = true)) return START_NOT_STICKY
                stepAccumulator?.setActive(false)
                removeLocationUpdates()
            }
            ACTION_STOP_UPDATES -> stopSelfSafely()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_runtrack)
        .setContentTitle("RunTrack")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun ensureForeground(paused: Boolean): Boolean =
        startForegroundSafely(
            buildNotification(if (paused) "Тренировка на паузе" else "Запись тренировки активна")
        )

    private fun startForegroundSafely(notification: Notification): Boolean = try {
        startForegroundCompat(notification)
        true
    } catch (_: SecurityException) {
        scope.launch {
            repository.requireRecovery(System.currentTimeMillis(), SystemClock.elapsedRealtime())
            stopSelfSafely()
        }
        false
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (heartRateConnected) {
                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && shouldUseHealthForegroundType()) {
                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }


    private fun shouldUseHealthForegroundType(): Boolean {
        val snapshot = repository.state.value ?: return false
        if (snapshot.type == WorkoutType.BIKE) return false
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }

    private suspend fun recoverAndStop(message: String, error: Exception) {
        android.util.Log.e("RunTrackTracking", message, error)
        try {
            repository.requireRecovery(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (recoveryError: Exception) {
            // The original failure may itself be a Room/storage failure. Do not crash the process
            // while attempting the recovery write; the persisted ACTIVE session is still recoverable
            // on the next process start by restoreRecoverable().
            android.util.Log.e(
                "RunTrackTracking",
                "Failed to persist RECOVERY_REQUIRED after tracking failure",
                recoveryError,
            )
        }
        stopSelfSafely()
    }

    private suspend fun processLocation(location: Location) {
        val sample = location.toSample()
        val accepted = repository.onLocation(
            sample = sample,
            elapsedRealtimeMillis = location.elapsedRealtimeNanos / 1_000_000L,
        )

        // Weather is optional side work and never blocks GPS -> Room persistence.
        if (accepted) {
            val workoutId = repository.state.value?.workoutId
            if (workoutId != null) {
                scope.launch {
                    weatherUpdateCoordinator.onAcceptedLocation(
                        workoutId = workoutId,
                        sample = sample,
                    )
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        if (!foregroundStarted) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun TrackingSnapshot.notificationText(): String = when {
        status == com.runtrack.app.domain.WorkoutStatus.MANUAL_PAUSED ->
            if (goalReached) "Цель достигнута · тренировка на паузе" else "Тренировка на паузе"
        goalReached -> "Цель достигнута · запись продолжается"
        else -> "Запись тренировки активна"
    }

    private fun configureStepSensor(active: Boolean, allowInitialize: Boolean) {
        val snapshot = repository.state.value ?: run {
            unregisterStepSensor()
            return
        }

        if (snapshot.type == WorkoutType.BIKE) {
            unregisterStepSensor()
            if (allowInitialize || snapshot.stepTrackingReliable) {
                scope.launch { repository.configureStepTracking(false, System.currentTimeMillis()) }
            }
            return
        }

        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            unregisterStepSensor()
            scope.launch { repository.configureStepTracking(false, System.currentTimeMillis()) }
            return
        }

        if (!allowInitialize && !snapshot.stepTrackingReliable) {
            unregisterStepSensor()
            return
        }

        if (stepSensorRegistered && stepWorkoutId == snapshot.workoutId) {
            stepAccumulator?.setActive(active)
            return
        }

        unregisterStepSensor()
        val sensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                ?: run {
                    scope.launch { repository.configureStepTracking(false, System.currentTimeMillis()) }
                    return
                }

        val needsReliabilityInitialization =
            allowInitialize && !snapshot.stepTrackingReliable
        val accumulator = StepCounterAccumulator(snapshot.stepCount ?: 0L).apply {
            setActive(active && !needsReliabilityInitialization)
        }
        stepWorkoutId = snapshot.workoutId
        stepAccumulator = accumulator
        stepSensorRegistered = sensorManager.registerListener(
            stepListener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        if (!stepSensorRegistered) {
            stepWorkoutId = null
            stepAccumulator = null
            scope.launch { repository.configureStepTracking(false, System.currentTimeMillis()) }
        } else if (needsReliabilityInitialization) {
            val workoutId = snapshot.workoutId
            scope.launch {
                val enabled =
                    repository.configureStepTracking(true, System.currentTimeMillis())
                if (enabled) {
                    withContext(Dispatchers.Main.immediate) {
                        if (stepWorkoutId == workoutId) {
                            stepAccumulator?.setActive(active)
                        }
                    }
                }
            }
        }
    }

    private fun unregisterStepSensor() {
        if (stepSensorRegistered) sensorManager.unregisterListener(stepListener)
        stepSensorRegistered = false
        stepWorkoutId = null
        stepAccumulator = null
    }

    private fun requestLocationUpdatesIfAllowed(onRegistered: (() -> Unit)? = null) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            scope.launch {
                recoverAndStop(
                    "Fine location permission missing before GPS registration",
                    SecurityException("ACCESS_FINE_LOCATION is not granted"),
                )
            }
            return
        }

        val token = synchronized(locationCallbackLock) {
            locationRegistration.beginIfNeeded()
        } ?: return
        val requestCallback = createLocationCallback(token)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            // Do not batch fitness points: different batches may legally arrive out of order.
            // Also require a fresh first point after start/resume so pause movement is never bridged.
            .setMaxUpdateAgeMillis(0L)
            .build()

        try {
            fused.requestLocationUpdates(request, requestCallback, Looper.getMainLooper())
                .addOnSuccessListener {
                    val accepted = synchronized(locationCallbackLock) {
                        if (locationRegistration.markSuccess(token)) {
                            activeLocationCallback = requestCallback
                            true
                        } else {
                            false
                        }
                    }
                    if (accepted) {
                        runCatching { onRegistered?.invoke() }
                            .onFailure {
                                android.util.Log.w(
                                    "RunTrackTracking",
                                    "Post-registration action failed",
                                    it,
                                )
                            }
                    } else {
                        // The request completed after pause/stop or after a newer generation began.
                        // It owns a generation-specific callback, so removing it cannot cancel a newer one.
                        runCatching { fused.removeLocationUpdates(requestCallback) }
                            .onFailure {
                                android.util.Log.w(
                                    "RunTrackTracking",
                                    "Failed to remove stale location callback",
                                    it,
                                )
                            }
                    }
                }
                .addOnFailureListener { error ->
                    val currentFailure = synchronized(locationCallbackLock) {
                        locationRegistration.markFailure(token)
                    }
                    if (currentFailure) {
                        scope.launch {
                            recoverAndStop("Fused location registration failed", error)
                        }
                    }
                }
        } catch (error: Exception) {
            val currentFailure = synchronized(locationCallbackLock) {
                locationRegistration.markFailure(token)
            }
            if (currentFailure) {
                scope.launch {
                    recoverAndStop("Fused location registration threw before completion", error)
                }
            }
        }
    }

    private fun removeLocationUpdates() {
        val callbackToRemove = synchronized(locationCallbackLock) {
            locationRegistration.cancel()
            activeLocationCallback.also { activeLocationCallback = null }
        }
        if (callbackToRemove != null) {
            try {
                fused.removeLocationUpdates(callbackToRemove)
            } catch (error: Exception) {
                android.util.Log.w(
                    "RunTrackTracking",
                    "Failed to request location callback removal",
                    error,
                )
            }
        }
    }

    private fun stopSelfSafely() {
        removeLocationUpdates()
        unregisterStepSensor()
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
            foregroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        removeLocationUpdates()
        unregisterStepSensor()
        checkpointJob?.cancel()
        checkpointJob = null
        locationBatches.close()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Активная тренировка", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Обязательное уведомление во время записи GPS"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun Location.toSample() = LocationSample(
        timestampMillis = time,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMps = if (hasSpeed()) speed else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        provider = provider,
        monotonicMillis = elapsedRealtimeNanos / 1_000_000L,
    )

    companion object {
        const val ACTION_START_UPDATES = "com.runtrack.app.action.START_LOCATION_UPDATES"
        const val ACTION_PAUSE_UPDATES = "com.runtrack.app.action.PAUSE_LOCATION_UPDATES"
        const val ACTION_RESUME_UPDATES = "com.runtrack.app.action.RESUME_LOCATION_UPDATES"
        const val ACTION_STOP_UPDATES = "com.runtrack.app.action.STOP_LOCATION_UPDATES"
        private const val CHANNEL_ID = "active_workout"
        private const val NOTIFICATION_ID = 1001

        private const val LOCATION_INTERVAL_MS = 2_000L
        private const val MIN_LOCATION_INTERVAL_MS = 1_000L
        private const val MIN_DISTANCE_METERS = 2f
        private const val CHECKPOINT_INTERVAL_MS = 15_000L
    }
}
