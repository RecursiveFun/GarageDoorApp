package com.garagedoor.app.geofence

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.garagedoor.app.data.SettingsRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class GeofenceRegistrar(context: Context) {

    private val appContext = context.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val geofenceManager = GeofenceManager(appContext)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    suspend fun registerIfConfigured() {
        val settings = settingsRepository.getSettings()
        if (!settings.hasPolygon || !settings.autoOpenEnabled || !settings.hasCredentials) {
            Log.i(TAG, "Auto-open off / incomplete — stopping monitor")
            runCatching { geofenceManager.removeAll() }
            LocationCheckService.stop(appContext)
            return
        }

        runCatching {
            geofenceManager.removeAll()
            geofenceManager.register(settings)
            Log.i(
                TAG,
                "Geofence registered at (${settings.geofenceCenterLat}, ${settings.geofenceCenterLng}) " +
                    "radius=${settings.geofenceRadiusMeters.toInt()}m",
            )
            restoreArmedState()
        }.onFailure { error ->
            Log.e(TAG, "Failed to register geofence", error)
            throw error
        }
    }

    suspend fun ensureMonitoringActive() {
        val settings = settingsRepository.getSettings()
        if (!settings.hasPolygon || !settings.autoOpenEnabled || !settings.hasCredentials) {
            return
        }
        restoreArmedState()
    }

    /**
     * Disarmed (at home) → no GPS.
     * Armed + near home → low-power GPS for polygon re-entry.
     * Armed + away → GPS off until coarse ENTER.
     */
    @SuppressLint("MissingPermission")
    private suspend fun restoreArmedState() {
        val settings = settingsRepository.getSettings()
        if (settings.wasInsidePolygon) {
            Log.i(TAG, "Disarmed at home — GPS off")
            LocationCheckService.startDisarmed(appContext)
            return
        }

        val location = runCatching {
            fusedClient.lastLocation.await()
                ?: fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token,
                ).await()
        }.getOrNull()

        val nearHome = location?.let {
            isNearHome(it, settings.geofenceCenterLat, settings.geofenceCenterLng, settings.geofenceRadiusMeters)
        }

        if (nearHome == true) {
            Log.i(TAG, "Armed near home — low-power GPS")
            LocationCheckService.startArmedNearHome(appContext)
        } else {
            Log.i(TAG, "Armed away — GPS off")
            LocationCheckService.startIdleArmed(appContext)
        }
    }

    private fun isNearHome(
        location: Location,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Float,
    ): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            centerLat,
            centerLng,
            results,
        )
        return results[0] <= radiusMeters * 1.15f
    }

    companion object {
        private const val TAG = "GeofenceRegistrar"
    }
}
