package com.garagedoor.app.geofence

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.garagedoor.app.GarageApp
import com.garagedoor.app.MainActivity
import com.garagedoor.app.R
import com.garagedoor.app.data.GarageRepository
import com.garagedoor.app.data.SettingsRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Battery-aware auto-open watch.
 *
 * Location is polled **only while armed** (waiting to open on re-entry):
 * - Disarmed (at home): notification only, GPS off — coarse EXIT arms you
 * - Armed + away: GPS off — coarse ENTER starts arrival GPS
 * - Armed + near home: low-power GPS until trigger or you leave again
 */
class LocationCheckService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var arrivalHandler: PolygonArrivalHandler

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var mode = Mode.DISARMED
    private var timeoutJob: Job? = null
    private var activeIntervalMs = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            scope.launch { handleLocation(location) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        arrivalHandler = PolygonArrivalHandler(settingsRepository, GarageRepository(applicationContext))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISARMED -> startDisarmed()
            ACTION_IDLE_ARMED -> startIdleArmed()
            ACTION_ARMED_NEAR_HOME -> startArmedNearHome()
            ACTION_ARRIVAL_CHECK -> startArrivalCheck()
            ACTION_COARSE_ENTER -> onCoarseEnterCommand()
            // Legacy aliases
            ACTION_NEAR_HOME, ACTION_HOME_WATCH -> startArmedNearHome()
            else -> startDisarmed()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        timeoutJob?.cancel()
        stopLocationUpdates()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** At home / not armed — no location polling. */
    private fun startDisarmed() {
        mode = Mode.DISARMED
        timeoutJob?.cancel()
        timeoutJob = null
        stopLocationUpdates()
        startForeground(NOTIFICATION_ID, buildNotification("Auto-open ready"))
        Log.i(TAG, "Disarmed — GPS off")
    }

    /** Armed but away — no GPS; wait for coarse geofence ENTER. */
    private fun startIdleArmed() {
        mode = Mode.IDLE_ARMED
        timeoutJob?.cancel()
        timeoutJob = null
        stopLocationUpdates()
        startForeground(NOTIFICATION_ID, buildNotification("Auto-open armed"))
        Log.i(TAG, "Idle armed — GPS off, waiting for coarse ENTER")
    }

    /** Armed and near home — low-power GPS until polygon re-entry triggers. */
    private fun startArmedNearHome() {
        if (mode == Mode.ARRIVAL_CHECK) {
            startForeground(NOTIFICATION_ID, buildNotification("Checking location…"))
            return
        }
        mode = Mode.ARMED_NEAR_HOME
        timeoutJob?.cancel()
        timeoutJob = null
        startForeground(NOTIFICATION_ID, buildNotification("Auto-open armed"))
        restartLocationUpdates(ARMED_NEAR_HOME_INTERVAL_MS, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        Log.i(TAG, "Armed near home — low-power GPS until re-entry")
    }

    /** Coarse ENTER: GPS only if armed; otherwise stay disarmed with GPS off. */
    private fun onCoarseEnterCommand() {
        // Satisfy FGS start immediately, then decide whether GPS is needed.
        startForeground(NOTIFICATION_ID, buildNotification("Auto-open ready"))
        scope.launch {
            val settings = settingsRepository.getSettings()
            if (!settings.autoOpenEnabled) {
                stopSelf()
                return@launch
            }
            if (settings.wasInsidePolygon) {
                Log.i(TAG, "Coarse ENTER while disarmed — GPS stays off")
                startDisarmed()
            } else {
                Log.i(TAG, "Coarse ENTER while armed — starting arrival GPS")
                startArrivalCheck()
            }
        }
    }

    /** Brief high-accuracy burst after coarse ENTER while armed. */
    private fun startArrivalCheck() {
        mode = Mode.ARRIVAL_CHECK
        startForeground(NOTIFICATION_ID, buildNotification("Checking location…"))
        restartLocationUpdates(ARRIVAL_INTERVAL_MS, Priority.PRIORITY_HIGH_ACCURACY)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(ARRIVAL_TIMEOUT_MS)
            Log.w(TAG, "Arrival check timed out")
            val settings = settingsRepository.getSettings()
            if (settings.wasInsidePolygon) {
                startDisarmed()
            } else {
                // Still armed and near home — keep low-power watch for driveway entry.
                startArmedNearHome()
            }
        }
    }

    private fun restartLocationUpdates(intervalMs: Long, priority: Int) {
        if (intervalMs == activeIntervalMs && activeIntervalMs > 0L) {
            return
        }
        activeIntervalMs = intervalMs
        fusedClient.removeLocationUpdates(locationCallback)
        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 3)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
            Log.d(TAG, "Location updates: every ${intervalMs}ms priority=$priority")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission", e)
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
        activeIntervalMs = 0L
    }

    private suspend fun handleLocation(location: android.location.Location) {
        // Hard rule: never act on GPS unless we are armed / checking arrival.
        if (mode == Mode.DISARMED) {
            stopLocationUpdates()
            return
        }

        val result = arrivalHandler.processGpsReading(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
        )

        when {
            result.triggered -> {
                Log.i(TAG, "Garage triggered — disarming")
                updateNotification("Opening garage…")
                delay(2_000L)
                startDisarmed()
                return
            }
            result.message.startsWith("Left boundary") -> {
                // Still in neighborhood but left polygon — stay armed with low-power GPS.
                Log.i(TAG, result.message)
                if (mode != Mode.ARMED_NEAR_HOME && mode != Mode.ARRIVAL_CHECK) {
                    startArmedNearHome()
                } else {
                    updateNotification("Auto-open armed")
                }
            }
            result.message.contains("error", ignoreCase = true) ||
                result.message.contains("rejected", ignoreCase = true) ||
                result.message.contains("Network", ignoreCase = true) ||
                result.message.contains("Missing", ignoreCase = true) -> {
                Log.e(TAG, result.message)
                notifyFailure(result.message)
            }
            else -> Log.d(TAG, result.message)
        }

        // Arrived home without needing a trigger (already inside) → disarm, stop GPS.
        if (mode == Mode.ARRIVAL_CHECK && result.nowInside) {
            Log.i(TAG, "Already inside boundary — disarming")
            startDisarmed()
        }
    }

    private fun notifyFailure(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(this, GarageApp.CHANNEL_GARAGE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Garage door")
                .setContentText(message)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, GarageApp.CHANNEL_GARAGE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Garage door")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private enum class Mode {
        DISARMED,
        IDLE_ARMED,
        ARMED_NEAR_HOME,
        ARRIVAL_CHECK,
    }

    companion object {
        private const val TAG = "LocationCheckService"
        private const val NOTIFICATION_ID = 1001

        private const val ARRIVAL_INTERVAL_MS = 4_000L
        private const val ARRIVAL_TIMEOUT_MS = 90_000L
        private const val ARMED_NEAR_HOME_INTERVAL_MS = 15_000L

        const val ACTION_DISARMED = "com.garagedoor.app.action.DISARMED"
        const val ACTION_IDLE_ARMED = "com.garagedoor.app.action.IDLE_ARMED"
        const val ACTION_ARMED_NEAR_HOME = "com.garagedoor.app.action.ARMED_NEAR_HOME"
        const val ACTION_ARRIVAL_CHECK = "com.garagedoor.app.action.ARRIVAL_CHECK"
        const val ACTION_COARSE_ENTER = "com.garagedoor.app.action.COARSE_ENTER"
        const val ACTION_NEAR_HOME = "com.garagedoor.app.action.NEAR_HOME"
        const val ACTION_HOME_WATCH = "com.garagedoor.app.action.HOME_WATCH"
        const val ACTION_STOP = "com.garagedoor.app.action.STOP"

        fun startDisarmed(context: Context) {
            startForeground(context, ACTION_DISARMED)
        }

        fun startIdleArmed(context: Context) {
            startForeground(context, ACTION_IDLE_ARMED)
        }

        fun startArmedNearHome(context: Context) {
            startForeground(context, ACTION_ARMED_NEAR_HOME)
        }

        fun startArrivalCheck(context: Context) {
            startForeground(context, ACTION_ARRIVAL_CHECK)
        }

        fun onCoarseEnter(context: Context) {
            startForeground(context, ACTION_COARSE_ENTER)
        }

        /** @deprecated Prefer [startDisarmed] / [startIdleArmed] / [startArmedNearHome]. */
        fun startHomeWatch(context: Context) {
            startIdleArmed(context)
        }

        fun startNearHomeWatch(context: Context) {
            startArmedNearHome(context)
        }

        private fun startForeground(context: Context, action: String) {
            val intent = Intent(context, LocationCheckService::class.java).apply {
                this.action = action
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                ) {
                    Log.w(
                        TAG,
                        "Foreground service blocked from background; open the app once or enter the geofence",
                        e,
                    )
                } else {
                    Log.e(TAG, "Failed to start foreground service ($action)", e)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationCheckService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
