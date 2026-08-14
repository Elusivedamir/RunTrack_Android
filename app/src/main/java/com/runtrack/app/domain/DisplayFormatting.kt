package com.runtrack.app.domain

import java.util.Locale
import kotlin.math.roundToInt

object RunTrackFormatter {
    private const val METERS_PER_MILE = 1609.344
    private const val MPS_TO_KPH = 3.6
    private const val MPS_TO_MPH = 2.2369362920544

    fun distance(meters: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String {
        val safe = meters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val value = if (units == UnitSystem.METRIC) safe / 1000.0 else safe / METERS_PER_MILE
        val suffix = if (units == UnitSystem.METRIC) "км" else "mi"
        return String.format(locale, "%.2f %s", value, suffix)
    }

    fun distanceNumber(meters: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String {
        val safe = meters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val value = if (units == UnitSystem.METRIC) safe / 1000.0 else safe / METERS_PER_MILE
        return String.format(locale, "%.2f", value)
    }

    fun duration(millis: Long): String {
        val total = (millis.coerceAtLeast(0L) / 1000L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        else "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }

    fun pace(paceSecondsPerKm: Double?, units: UnitSystem): String {
        val secondsPerKm = paceSecondsPerKm?.takeIf { it.isFinite() && it > 0.0 } ?: return "—"
        val secondsPerUnit = if (units == UnitSystem.METRIC) secondsPerKm else secondsPerKm * 1.609344
        val rounded = secondsPerUnit.roundToInt().coerceAtLeast(1)
        val minutes = rounded / 60
        val seconds = rounded % 60
        return "%d:%02d %s".format(Locale.ROOT, minutes, seconds, if (units == UnitSystem.METRIC) "/км" else "/mi")
    }

    fun speed(mps: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String {
        val safe = mps.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val value = if (units == UnitSystem.METRIC) safe * MPS_TO_KPH else safe * MPS_TO_MPH
        return String.format(locale, "%.1f %s", value, if (units == UnitSystem.METRIC) "км/ч" else "mph")
    }

    fun elevation(meters: Double?, units: UnitSystem, locale: Locale = Locale.getDefault()): String {
        val safe = meters?.takeIf { it.isFinite() } ?: return "Нет данных"
        return if (units == UnitSystem.METRIC) String.format(locale, "%.0f м", safe)
        else String.format(locale, "%.0f ft", safe * 3.280839895)
    }
}
