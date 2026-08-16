package com.runtrack.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunTrackMigrationTest {
    @Test fun migration3To4OpensAndValidatesAgainstFullRoomSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "runtrack-migration-${System.nanoTime()}.db"
        context.deleteDatabase(name)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion3Schema(db)
                        db.execSQL(
                            "INSERT INTO `workouts` (" +
                                "`id`,`sessionToken`,`type`,`goalKind`,`status`,`startedAt`," +
                                "`startedElapsedRealtimeMillis`,`elapsedMillis`,`movingMillis`," +
                                "`distanceMeters`,`averageSpeedMps`,`caloriesEstimate`,`createdAt`,`updatedAt`" +
                            ") VALUES ('legacy','session-legacy','RUN','NONE','COMPLETED',1000,500,60000,60000,100.0,1.666,10,1000,61000)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase
        helper.close()

        val room = Room.databaseBuilder(context, RunTrackDatabase::class.java, name)
            .addMigrations(RunTrackDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening the DB makes Room validate every table, FK and index after MIGRATION_3_4.
            room.openHelper.writableDatabase
            val legacy = requireNotNull(room.workoutDao().getWorkout("legacy"))
            assertNull(legacy.stepCount)
            assertTrue(!legacy.stepTrackingReliable)
            assertEquals(100.0, legacy.distanceMeters, 0.001)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }

    private fun createVersion3Schema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `workouts` (`id` TEXT NOT NULL, `sessionToken` TEXT NOT NULL, `type` TEXT NOT NULL, `goalKind` TEXT NOT NULL, `goalDistanceMeters` REAL, `goalDurationMillis` INTEGER, `goalReachedAt` INTEGER, `status` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `startedElapsedRealtimeMillis` INTEGER NOT NULL, `endedAt` INTEGER, `elapsedMillis` INTEGER NOT NULL, `movingMillis` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, `averageSpeedMps` REAL NOT NULL, `caloriesEstimate` INTEGER NOT NULL, `heartRateAverageBpm` INTEGER, `heartRateMaxBpm` INTEGER, `elevationGainMeters` REAL, `elevationLossMeters` REAL, `title` TEXT, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_startedAt` ON `workouts` (`startedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_type` ON `workouts` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_status` ON `workouts` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_status_startedAt` ON `workouts` (`status`, `startedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_status_type_startedAt` ON `workouts` (`status`, `type`, `startedAt`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workouts_sessionToken` ON `workouts` (`sessionToken`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `route_points` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `elapsedRealtimeMillis` INTEGER NOT NULL, `movingElapsedMillis` INTEGER NOT NULL, `segmentIndex` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracyMeters` REAL NOT NULL, `altitudeMeters` REAL, `speedMps` REAL, `bearingDegrees` REAL, `provider` TEXT, FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_route_points_workoutId_segmentIndex_elapsedRealtimeMillis` ON `route_points` (`workoutId`, `segmentIndex`, `elapsedRealtimeMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_points_workoutId_timestampMillis` ON `route_points` (`workoutId`, `timestampMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_points_workoutId` ON `route_points` (`workoutId`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `heart_rate_samples` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `elapsedRealtimeMillis` INTEGER NOT NULL, `bpm` INTEGER NOT NULL, `source` TEXT NOT NULL, FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_heart_rate_samples_workoutId_elapsedRealtimeMillis` ON `heart_rate_samples` (`workoutId`, `elapsedRealtimeMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_heart_rate_samples_workoutId_timestampMillis` ON `heart_rate_samples` (`workoutId`, `timestampMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_heart_rate_samples_workoutId` ON `heart_rate_samples` (`workoutId`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `weather_snapshots` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` TEXT NOT NULL, `capturedAt` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `temperatureC` REAL, `apparentTemperatureC` REAL, `relativeHumidityPercent` INTEGER, `windSpeedMps` REAL, `precipitationMm` REAL, `weatherCode` INTEGER, `source` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weather_snapshots_workoutId_capturedAt` ON `weather_snapshots` (`workoutId`, `capturedAt`)")
    }
}
