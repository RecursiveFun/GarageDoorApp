package com.garagedoor.app.geofence

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Helper class to manage foreground service requirements, especially important for Android 13+
 */
object ForegroundServiceHelper {

    private const val TAG = "ForegroundServiceHelper"

    /**
     * Safely starts a foreground service with proper error handling for Android 13+
     */
    fun startForegroundServiceSafely(context: Context, intent: android.content.Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // For Android 13+, we need to be more careful about foreground service restrictions
                context.startForegroundService(intent)
                Log.d(TAG, "Started foreground service on Android 13+")
            } else {
                context.startService(intent)
                Log.d(TAG, "Started service (pre-Android 13)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
            // In some cases, we might need to fall back to a different approach
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.e(TAG, "Android 13+ foreground service restriction likely in effect")
            }
        }
    }

    /**
     * Checks if the app has proper permissions for foreground services
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        // Check if we have location permission (required for foreground service)
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        return hasLocationPermission
    }
}