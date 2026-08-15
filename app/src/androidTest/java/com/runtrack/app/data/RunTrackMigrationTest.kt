package com.runtrack.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunTrackMigrationTest {
    @Test fun migration3To4AddsBackwardCompatibleStepColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "runtrack-migration-${System.nanoTime()}.db"
        context.deleteDatabase(name)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `workouts` (`id` TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO `workouts` (`id`) VALUES ('legacy')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        try {
            val db = helper.writableDatabase
            RunTrackDatabase.MIGRATION_3_4.migrate(db)
            var sawStepCount = false
            var sawReliable = false
            db.query("PRAGMA table_info(`workouts`)").use { cursor ->
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                    when (cursor.getString(nameIndex)) {
                        "stepCount" -> {
                            sawStepCount = true
                            assertNull(cursor.getString(defaultIndex))
                        }
                        "stepTrackingReliable" -> {
                            sawReliable = true
                            assertEquals("0", cursor.getString(defaultIndex))
                        }
                    }
                }
            }
            assertTrue(sawStepCount)
            assertTrue(sawReliable)

            db.query(
                "SELECT `stepCount`, `stepTrackingReliable` " +
                    "FROM `workouts` WHERE `id` = 'legacy'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals(0, cursor.getInt(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
