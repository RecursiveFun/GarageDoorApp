package com.garagedoor.app.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.garagedoor.app.data.AppSettings
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

class GeofenceManager(private val context: Context) {

    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    @SuppressLint("MissingPermission")
    suspend fun register(settings: AppSettings) {
        require(settings.hasPolygon) { "Polygon not configured" }
        require(settings.geofenceRadiusMeters > 0f) { "Invalid geofence radius" }

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(
                settings.geofenceCenterLat,
                settings.geofenceCenterLng,
                settings.geofenceRadiusMeters,
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
            )
            .build()

        val request = GeofencingRequest.Builder()
            // Don't fire ENTER when registering while already at home (e.g. saving boundary).
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        client.addGeofences(request, pendingIntent()).await()
    }

    suspend fun removeAll() {
        client.removeGeofences(pendingIntent()).await()
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    companion object {
        const val GEOFENCE_ID = "garage_coarse_geofence"
    }
}
