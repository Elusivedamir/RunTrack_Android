package com.runtrack.app.tracking

import android.content.Context
import android.util.Log
import com.runtrack.app.data.RunTrackDatabase
import com.runtrack.app.settings.SettingsRepository
import com.runtrack.app.export.PortableBackupManager
import com.runtrack.app.export.WorkoutExportManager
import com.runtrack.app.weather.WeatherRepository
import com.runtrack.app.weather.WeatherUpdateCoordinator
import com.runtrack.app.voice.VoiceAnnouncementManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object RunTrackRuntime {
    @Volatile private var initialized = false
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var database: RunTrackDatabase
        private set
    lateinit var trackingRepository: TrackingRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var voiceAnnouncementManager: VoiceAnnouncementManager
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
        voiceAnnouncementManager = VoiceAnnouncementManager(app, settingsRepository)
        weatherRepository = WeatherRepository(database.workoutDao())
        weatherUpdateCoordinator = WeatherUpdateCoordinator(weatherRepository)
        backupManager = PortableBackupManager(app, database, settingsRepository)
        exportManager = WorkoutExportManager(app)
        heartRateManager = BleHeartRateManager(app, settingsRepository) { bpm, wall, elapsed ->
            trackingRepository.onHeartRateSample(bpm, wall, elapsed)
        }
        resultNotificationManager = ResultNotificationManager(app)
        initialized = true
        runtimeScope.launch {
            try {
                backupManager.recoverPendingRestore()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("RunTrackBackup", "Failed to recover interrupted backup restore", error)
            }
        }
    }

    fun requireInitialized() {
        check(initialized) { "RunTrackRuntime is not initialized" }
    }
}
