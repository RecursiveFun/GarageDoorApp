package com.garagedoor.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.garagedoor.app.geofence.GeofenceRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class GarageApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        createNotificationChannels()
        appScope.launch {
            runCatching { GeofenceRegistrar(this@GarageApp).registerIfConfigured() }
                .onFailure { Log.e(TAG, "Startup geofence registration failed", it) }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GARAGE,
                "Garage door",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when auto-open is armed and watching your boundary"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val TAG = "GarageApp"
        const val CHANNEL_GARAGE = "garage_door"
    }
}
