package com.runtrack.prototype.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.runtrack.prototype.domain.MapLayer
import com.runtrack.prototype.domain.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.runTrackDataStore by preferencesDataStore(name = "runtrack_settings")

data class RunTrackSettings(
    val notificationsEnabled: Boolean = false,
    val autoPauseEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val units: UnitSystem = UnitSystem.METRIC,
    val mapLayer: MapLayer = MapLayer.STANDARD,
    val weightKg: Double? = null,
    val profileName: String = "Пользователь",
    val heartRateDeviceAddress: String? = null,
    val heartRateDeviceName: String? = null,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val notifications = booleanPreferencesKey("notifications_enabled")
        val autoPause = booleanPreferencesKey("auto_pause_enabled")
        val keepScreen = booleanPreferencesKey("keep_screen_on")
        val units = stringPreferencesKey("unit_system")
        val mapLayer = stringPreferencesKey("map_layer")
        val weightKg = doublePreferencesKey("weight_kg")
        val profileName = stringPreferencesKey("profile_name")
        val hrAddress = stringPreferencesKey("hr_device_address")
        val hrName = stringPreferencesKey("hr_device_name")
    }

    val settings: Flow<RunTrackSettings> = context.runTrackDataStore.data.map { p ->
        RunTrackSettings(
            notificationsEnabled = p[Keys.notifications] ?: false,
            autoPauseEnabled = p[Keys.autoPause] ?: true,
            keepScreenOn = p[Keys.keepScreen] ?: false,
            units = p[Keys.units]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.METRIC,
            mapLayer = p[Keys.mapLayer]?.let { runCatching { MapLayer.valueOf(it) }.getOrNull() } ?: MapLayer.STANDARD,
            weightKg = p[Keys.weightKg]?.takeIf { it in 30.0..300.0 },
            profileName = p[Keys.profileName]?.trim()?.takeIf { it.isNotBlank() }?.take(80) ?: "Пользователь",
            heartRateDeviceAddress = p[Keys.hrAddress]?.takeIf { it.isNotBlank() },
            heartRateDeviceName = p[Keys.hrName]?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun setNotificationsEnabled(value: Boolean) = context.runTrackDataStore.edit { it[Keys.notifications] = value }
    suspend fun setAutoPauseEnabled(value: Boolean) = context.runTrackDataStore.edit { it[Keys.autoPause] = value }
    suspend fun setKeepScreenOn(value: Boolean) = context.runTrackDataStore.edit { it[Keys.keepScreen] = value }
    suspend fun setUnits(value: UnitSystem) = context.runTrackDataStore.edit { it[Keys.units] = value.name }
    suspend fun setMapLayer(value: MapLayer) = context.runTrackDataStore.edit { it[Keys.mapLayer] = value.name }
    suspend fun setHeartRateDevice(address: String?, name: String?) = context.runTrackDataStore.edit { p ->
        if (address.isNullOrBlank()) p.remove(Keys.hrAddress) else p[Keys.hrAddress] = address
        if (name.isNullOrBlank()) p.remove(Keys.hrName) else p[Keys.hrName] = name
    }

    suspend fun setProfileName(value: String) = context.runTrackDataStore.edit { p ->
        val safe = value.trim().take(80)
        if (safe.isBlank()) p.remove(Keys.profileName) else p[Keys.profileName] = safe
    }

    suspend fun setWeightKg(value: Double?) = context.runTrackDataStore.edit {
        if (value != null && value.isFinite() && value in 30.0..300.0) it[Keys.weightKg] = value else it.remove(Keys.weightKg)
    }

    suspend fun restore(value: RunTrackSettings) = context.runTrackDataStore.edit { p ->
        p[Keys.notifications] = value.notificationsEnabled
        p[Keys.autoPause] = value.autoPauseEnabled
        p[Keys.keepScreen] = value.keepScreenOn
        p[Keys.units] = value.units.name
        p[Keys.mapLayer] = value.mapLayer.name
        if (value.weightKg != null && value.weightKg in 30.0..300.0) p[Keys.weightKg] = value.weightKg else p.remove(Keys.weightKg)
        if (value.profileName.isBlank()) p.remove(Keys.profileName) else p[Keys.profileName] = value.profileName.trim().take(80)
        if (value.heartRateDeviceAddress.isNullOrBlank()) p.remove(Keys.hrAddress) else p[Keys.hrAddress] = value.heartRateDeviceAddress
        if (value.heartRateDeviceName.isNullOrBlank()) p.remove(Keys.hrName) else p[Keys.hrName] = value.heartRateDeviceName
    }

    suspend fun clearAll() = context.runTrackDataStore.edit { it.clear() }
}
