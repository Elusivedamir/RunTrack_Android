package com.runtrack.app.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.runtrack.app.MainActivity
import com.runtrack.app.R
import com.runtrack.app.data.WorkoutEntity
import com.runtrack.app.domain.RunTrackFormatter
import com.runtrack.app.domain.UnitSystem
import com.runtrack.app.domain.WorkoutType

/** Optional result notification. It is intentionally independent of mandatory FGS notifications. */
class ResultNotificationManager(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

    fun showCompleted(workout: WorkoutEntity, units: UnitSystem, enabled: Boolean) {
        if (!enabled || !canPostNotifications()) return
        createChannel()
        val type = runCatching { WorkoutType.valueOf(workout.type) }.getOrDefault(WorkoutType.RUN)
        val title = when (type) {
            WorkoutType.RUN -> "Пробежка сохранена"
            WorkoutType.WALK -> "Прогулка сохранена"
            WorkoutType.BIKE -> "Велопоездка сохранена"
        }
        val openIntent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            app,
            workout.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_runtrack)
            .setContentTitle(title)
            .setContentText("${RunTrackFormatter.distance(workout.distanceMeters, units)} · ${RunTrackFormatter.duration(workout.elapsedMillis)}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(workout.id.hashCode(), notification)
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Итоги тренировок", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Необязательные уведомления после сохранения тренировки"
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "workout_results"
    }
}
