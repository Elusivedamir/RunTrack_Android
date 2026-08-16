package com.runtrack.app.export

import android.content.Context
import android.util.AtomicFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runtrack.app.data.RunTrackDatabase
import com.runtrack.app.data.WorkoutEntity
import com.runtrack.app.domain.MapLayer
import com.runtrack.app.domain.UnitSystem
import com.runtrack.app.domain.WorkoutStatus
import com.runtrack.app.settings.RunTrackSettings
import com.runtrack.app.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PortableBackupRecoveryTest {
    private lateinit var context: Context
    private lateinit var db: RunTrackDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var manager: PortableBackupManager
    private lateinit var journalFile: File

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RunTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        settings.clearAll()
        manager = PortableBackupManager(context, db, settings)
        journalFile = File(context.filesDir, "pending_restore_journal_v1.json")
        AtomicFile(journalFile).delete()
    }

    @After
    fun tearDown() = runBlocking {
        AtomicFile(journalFile).delete()
        settings.clearAll()
        db.close()
    }

    @Test
    fun committedRoomRestoreAppliesTargetSettingsAndClearsJournal() = runBlocking {
        val target = RunTrackSettings(
            profileName = "Recovered",
            units = UnitSystem.IMPERIAL,
            mapLayer = MapLayer.TERRAIN,
        )
        settings.restore(RunTrackSettings(profileName = "Before"))

        val id = "restore-workout-1"
        val token = "restore-session-1"
        writeJournal(target, id, token)
        db.workoutDao().insertWorkout(completedWorkout(id, token))

        assertTrue(manager.recoverPendingRestore())
        val restored = settings.settings.first()
        assertEquals("Recovered", restored.profileName)
        assertEquals(UnitSystem.IMPERIAL, restored.units)
        assertEquals(MapLayer.TERRAIN, restored.mapLayer)
        assertFalse(journalFile.exists())
    }

    @Test
    fun missingRoomCommitLeavesExistingSettingsAndClearsJournal() = runBlocking {
        settings.restore(RunTrackSettings(profileName = "Before", units = UnitSystem.METRIC))
        writeJournal(
            RunTrackSettings(profileName = "Target", units = UnitSystem.IMPERIAL),
            "not-committed",
            "not-committed-session",
        )

        assertFalse(manager.recoverPendingRestore())
        val restored = settings.settings.first()
        assertEquals("Before", restored.profileName)
        assertEquals(UnitSystem.METRIC, restored.units)
        assertFalse(journalFile.exists())
    }

    private fun writeJournal(target: RunTrackSettings, id: String, sessionToken: String) {
        val raw = JSONObject().apply {
            put("version", 1)
            put("backupSha256", "ab".repeat(32))
            put("targetSettings", settingsJson(target))
            put("workouts", JSONArray().put(JSONObject().apply {
                put("id", id)
                put("sessionToken", sessionToken)
            }))
        }.toString().toByteArray(Charsets.UTF_8)
        val atomic = AtomicFile(journalFile)
        val output = atomic.startWrite()
        try {
            output.write(raw)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun settingsJson(value: RunTrackSettings) = JSONObject().apply {
        put("notifications", value.notificationsEnabled)
        put("voiceAnnouncements", value.voiceAnnouncementsEnabled)
        put("profileName", value.profileName)
        put("keepScreenOn", value.keepScreenOn)
        put("units", value.units.name)
        put("mapLayer", value.mapLayer.name)
        value.weightKg?.let { put("weightKg", it) }
        value.heightCm?.let { put("heightCm", it) }
        value.heartRateDeviceAddress?.let { put("heartRateDeviceAddress", it) }
        value.heartRateDeviceName?.let { put("heartRateDeviceName", it) }
    }

    private fun completedWorkout(id: String, token: String) = WorkoutEntity(
        id = id,
        sessionToken = token,
        type = "RUN",
        goalKind = "NONE",
        goalDistanceMeters = null,
        goalDurationMillis = null,
        goalReachedAt = null,
        status = WorkoutStatus.COMPLETED.name,
        startedAt = 1_000L,
        startedElapsedRealtimeMillis = 1_000L,
        endedAt = 61_000L,
        elapsedMillis = 60_000L,
        movingMillis = 60_000L,
        distanceMeters = 100.0,
        averageSpeedMps = 100.0 / 60.0,
        caloriesEstimate = 10,
        heartRateAverageBpm = null,
        heartRateMaxBpm = null,
        elevationGainMeters = null,
        elevationLossMeters = null,
        title = null,
        note = null,
        createdAt = 1_000L,
        updatedAt = 61_000L,
        stepCount = null,
        stepTrackingReliable = false,
    )
}
