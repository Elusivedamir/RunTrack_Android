package com.runtrack.app.tracking

import android.content.Context
import com.runtrack.app.data.RunTrackDatabase
import com.runtrack.app.settings.SettingsRepository
import com.runtrack.app.export.PortableBackupManager
import com.runtrack.app.export.WorkoutExportManager
import com.runtrack.app.weather.WeatherRepository
import com.runtrack.app.weather.WeatherUpdateCoordinator

object RunTrackRuntime {
    @Volatile private var initialized = false
    lateinit var database: RunTrackDatabase
        private set
    lateinit var trackingRepository: TrackingRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var backupManager: PortableBackupManager
        private set
    lateinit var exportManager: WorkoutExportManager
        private set
    lateinit var heartRateManager: BleHeartRateManager
        private set
    lateinit var resultNotificationManager: ResultNotificationManager
        private set
    lateinit var weatherRepository: WeatherRepository
        private set
    lateinit var weatherUpdateCoordinator: WeatherUpdateCoordinator
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        database = RunTrackDatabase.get(app)
        trackingRepository = TrackingRepository(database)
        settingsRepository = SettingsRepository(app)
        weatherRepository = WeatherRepository(database.workoutDao())
        weatherUpdateCoordinator = WeatherUpdateCoordinator(weatherRepository)
        backupManager = PortableBackupManager(app, database, settingsRepository)
        exportManager = WorkoutExportManager(app)
        heartRateManager = BleHeartRateManager(app, settingsRepository) { bpm, wall, elapsed ->
            trackingRepository.onHeartRateSample(bpm, wall, elapsed)
        }
        resultNotificationManager = ResultNotificationManager(app)
        initialized = true
    }

    fun requireInitialized() {
        check(initialized) { "RunTrackRuntime is not initialized" }
    }
}
