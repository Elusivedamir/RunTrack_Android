package com.runtrack.app.tracking

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.runtrack.app.R
import com.runtrack.app.domain.LocationSample
import com.runtrack.app.voice.KilometerAnnouncementTracker
import com.runtrack.app.weather.WeatherUpdateCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class WorkoutTrackingService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var repository: TrackingRepository
    private lateinit var weatherUpdateCoordinator: WeatherUpdateCoordinator
    private val kilometerAnnouncementTracker = KilometerAnnouncementTracker()
    @Volatile private var updatesRequested = false
    @Volatile private var foregroundStarted = false
    @Volatile private var heartRateConnected = false
    private var checkpointJob: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val ordered = result.locations.sortedBy { it.elapsedRealtimeNanos }
            scope.launch {
                for (location in ordered) {
                    val sample = location.toSample()

                    val accepted = repository.onLocation(
                        sample = sample,
                        elapsedRealtimeMillis = location.elapsedRealtimeNanos / 1_000_000L,
                    )

                    /*
                     * Weather is optional side work.
                     *
                     * Launch it separately so an HTTP timeout can never
                     * stall GPS ingestion or Room route persistence.
                     */
                    if (accepted) {
                        val workoutId =
                            repository.state.value?.workoutId

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
            }
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            scope.launch { repository.reportGpsAvailability(availability.isLocationAvailable) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        RunTrackRuntime.initialize(this)
        repository = RunTrackRuntime.trackingRepository
        weatherUpdateCoordinator = RunTrackRuntime.weatherUpdateCoordinator
        createNotificationChannel()
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
                        startForegroundCompat(buildNotification(snapshot?.notificationText() ?: "Запись тренировки активна"))
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
                runCatching { repository.checkpoint(nowWall, nowElapsed) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_UPDATES -> {
                ensureForeground(paused = false)
                if (requestLocationUpdatesIfAllowed()) {
                    repository.state.value?.let {
                        RunTrackRuntime.voiceAnnouncementManager.announceStart(it.workoutId)
                    }
                }
            }
            ACTION_RESUME_UPDATES -> {
                ensureForeground(paused = false)
                requestLocationUpdatesIfAllowed()
            }
            ACTION_PAUSE_UPDATES -> {
                ensureForeground(paused = true)
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

    private fun ensureForeground(paused: Boolean) {
        startForegroundCompat(buildNotification(if (paused) "Тренировка на паузе" else "Запись тренировки активна"))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (heartRateConnected) types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
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

    private fun requestLocationUpdatesIfAllowed(): Boolean {
        if (updatesRequested) return true
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            scope.launch {
                repository.requireRecovery(System.currentTimeMillis(), SystemClock.elapsedRealtime())
                stopSelfSafely()
            }
            return false
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            updatesRequested = true
            return true
        } catch (_: SecurityException) {
            scope.launch {
                repository.requireRecovery(System.currentTimeMillis(), SystemClock.elapsedRealtime())
                stopSelfSafely()
            }
            return false
        }
    }

    private fun removeLocationUpdates() {
        if (!updatesRequested) return
        fused.removeLocationUpdates(callback)
        updatesRequested = false
    }

    private fun stopSelfSafely() {
        removeLocationUpdates()
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
            foregroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        removeLocationUpdates()
        checkpointJob?.cancel()
        checkpointJob = null
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
        private const val MAX_UPDATE_DELAY_MS = 4_000L
        private const val MIN_DISTANCE_METERS = 2f
        private const val CHECKPOINT_INTERVAL_MS = 15_000L
    }
}
