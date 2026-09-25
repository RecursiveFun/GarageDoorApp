package com.garagedoor.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "Boot completed — re-registering geofence")
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                GeofenceRegistrar(context).registerIfConfigured()
            }.onFailure { Log.e(TAG, "Failed to register geofence on boot", it) }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
