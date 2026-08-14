package com.runtrack.prototype.tracking

import android.content.Context
import com.runtrack.prototype.data.RunTrackDatabase
import com.runtrack.prototype.settings.SettingsRepository
import com.runtrack.prototype.export.PortableBackupManager
import com.runtrack.prototype.export.WorkoutExportManager

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

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        database = RunTrackDatabase.get(app)
        trackingRepository = TrackingRepository(database)
        settingsRepository = SettingsRepository(app)
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
