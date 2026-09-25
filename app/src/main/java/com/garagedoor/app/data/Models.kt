package com.garagedoor.app.data

data class LatLngPoint(val lat: Double, val lng: Double)

data class GarageStatus(
    val name: String?,
    val door: String?,
    val busy: Boolean,
    val cooldown: Boolean,
    val version: String?,
)

data class TriggerResponse(
    val ok: Boolean,
    val message: String?,
)

data class AppSettings(
    val apiBaseUrl: String = DEFAULT_API_URL,
    val apiToken: String = "",
    val polygon: List<LatLngPoint> = emptyList(),
    val geofenceCenterLat: Double = 0.0,
    val geofenceCenterLng: Double = 0.0,
    val geofenceRadiusMeters: Float = 500f,
    val autoOpenEnabled: Boolean = true,
    val setupComplete: Boolean = false,
    val wasInsidePolygon: Boolean = false,
    val lastTriggerEpochMs: Long = 0L,
) {
    val hasPolygon: Boolean get() = polygon.size >= 3
    val hasCredentials: Boolean get() = apiToken.isNotBlank()

    companion object {
        const val DEFAULT_API_URL = "https://garage-api.example.com"
        const val TRIGGER_DEBOUNCE_MS = 2 * 60 * 1000L
    }
}
