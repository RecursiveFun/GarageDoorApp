package com.garagedoor.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.garagedoor.app.data.SettingsRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.e(TAG, "Geofence error: ${event.errorCode}")
            return
        }

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.i(TAG, "Coarse geofence ENTER")
                // Must call startForegroundService synchronously for the geofence FGS exemption.
                // Service decides armed vs disarmed and only enables GPS when armed.
                LocationCheckService.onCoarseEnter(context.applicationContext)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.i(TAG, "Coarse geofence EXIT — arming (GPS off)")
                LocationCheckService.startIdleArmed(context.applicationContext)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    SettingsRepository(context.applicationContext).setWasInsidePolygon(false)
                    pending.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
    }
}
