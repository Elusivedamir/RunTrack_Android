package com.runtrack.prototype.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.runtrack.prototype.domain.LocationSample
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class GpsReadiness(
    val finePermissionGranted: Boolean,
    val locationEnabled: Boolean,
    val hasFreshFix: Boolean,
    val accuracyMeters: Float?,
    val checkedAtElapsedRealtimeMillis: Long,
) {
    val ready: Boolean get() = finePermissionGranted && locationEnabled && hasFreshFix
}

class GpsReadinessChecker(private val context: Context) {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun basicStatus(): GpsReadiness {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(LocationManager::class.java)
        val enabled = runCatching { manager.isLocationEnabled }.getOrDefault(false)
        return GpsReadiness(permission, enabled, false, null, SystemClock.elapsedRealtime())
    }

    suspend fun checkCurrentFix(maxAccuracyMeters: Float = 45f): GpsReadiness {
        val basic = basicStatus()
        if (!basic.finePermissionGranted || !basic.locationEnabled) return basic
        val cancellation = CancellationTokenSource()
        val location = withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                try {
                    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    cont.invokeOnCancellation { cancellation.cancel() }
                } catch (_: SecurityException) {
                    cont.resume(null)
                }
            }
        }
        val now = SystemClock.elapsedRealtime()
        if (location == null) return basic.copy(checkedAtElapsedRealtimeMillis = now)
        val age = (now - location.elapsedRealtimeNanos / 1_000_000L).coerceAtLeast(0L)
        val accurate = location.hasAccuracy() && location.accuracy.isFinite() && location.accuracy in 0.1f..maxAccuracyMeters
        return GpsReadiness(
            finePermissionGranted = true,
            locationEnabled = true,
            hasFreshFix = age <= FRESH_FIX_MAX_AGE_MS && accurate,
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            checkedAtElapsedRealtimeMillis = now,
        )
    }

    companion object {
        private const val FRESH_FIX_MAX_AGE_MS = 15_000L
        private const val CURRENT_FIX_TIMEOUT_MS = 10_000L
    }
}
