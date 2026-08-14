package com.runtrack.app.maps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Runtime view of Maps configuration.
 *
 * Maps remain optional. GPS recording, distance calculation,
 * route persistence and route-art generation must not depend
 * on Google Maps being configured or available.
 */
object MapsRuntimeConfig {

    const val API_KEY_METADATA =
        "com.google.android.geo.API_KEY"

    fun isConfigured(
        context: Context,
    ): Boolean {
        val key = readApiKey(context)

        return !key.isNullOrBlank() &&
            key != "\${MAPS_API_KEY}"
    }

    private fun readApiKey(
        context: Context,
    ): String? {
        val appInfo = if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.ApplicationInfoFlags.of(
                    PackageManager.GET_META_DATA.toLong()
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
        }

        return appInfo.metaData
            ?.getString(API_KEY_METADATA)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
