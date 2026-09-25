package com.garagedoor.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.garagedoor.app.network.ApiUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "garage_settings")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "garage_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val legacyPassword = encryptedPrefs.getString(KEY_PASSWORD_LEGACY, "") ?: ""
        AppSettings(
            apiBaseUrl = ApiUrl.normalize(prefs[KEY_API_URL] ?: AppSettings.DEFAULT_API_URL),
            apiToken = encryptedPrefs.getString(KEY_API_TOKEN, "")?.takeIf { it.isNotBlank() }
                ?: legacyPassword,
            polygon = decodePolygon(prefs[KEY_POLYGON] ?: "[]"),
            geofenceCenterLat = prefs[KEY_CENTER_LAT]?.toDoubleOrNull() ?: 0.0,
            geofenceCenterLng = prefs[KEY_CENTER_LNG]?.toDoubleOrNull() ?: 0.0,
            geofenceRadiusMeters = prefs[KEY_RADIUS] ?: 500f,
            autoOpenEnabled = prefs[KEY_AUTO_OPEN] ?: true,
            setupComplete = prefs[KEY_SETUP_COMPLETE] ?: false,
            wasInsidePolygon = prefs[KEY_WAS_INSIDE] ?: false,
            lastTriggerEpochMs = prefs[KEY_LAST_TRIGGER] ?: 0L,
        )
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun saveApiCredentials(baseUrl: String, apiToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_URL] = ApiUrl.normalize(baseUrl)
        }
        encryptedPrefs.edit()
            .putString(KEY_API_TOKEN, apiToken.trim())
            .remove(KEY_PASSWORD_LEGACY)
            .apply()
    }

    suspend fun savePolygon(points: List<LatLngPoint>, currentlyInside: Boolean? = null) {
        val bounds = computeBounds(points)
        context.dataStore.edit { prefs ->
            prefs[KEY_POLYGON] = encodePolygon(points)
            prefs[KEY_CENTER_LAT] = bounds.centerLat.toString()
            prefs[KEY_CENTER_LNG] = bounds.centerLng.toString()
            prefs[KEY_RADIUS] = bounds.radiusMeters
            if (currentlyInside != null) {
                prefs[KEY_WAS_INSIDE] = currentlyInside
            }
        }
    }

    suspend fun setAutoOpenEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_OPEN] = enabled }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_COMPLETE] = complete }
    }

    suspend fun setWasInsidePolygon(inside: Boolean) {
        context.dataStore.edit { it[KEY_WAS_INSIDE] = inside }
    }

    suspend fun recordTrigger(nowMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[KEY_LAST_TRIGGER] = nowMs }
    }

    private fun encodePolygon(points: List<LatLngPoint>): String = gson.toJson(points)

    private fun decodePolygon(json: String): List<LatLngPoint> {
        val type = object : TypeToken<List<LatLngPoint>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private data class PolygonBounds(
        val centerLat: Double,
        val centerLng: Double,
        val radiusMeters: Float,
    )

    private fun computeBounds(points: List<LatLngPoint>): PolygonBounds {
        require(points.size >= 3)
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLng = points.minOf { it.lng }
        val maxLng = points.maxOf { it.lng }
        val centerLat = (minLat + maxLat) / 2.0
        val centerLng = (minLng + maxLng) / 2.0

        var maxDistance = 0.0
        for (point in points) {
            val d = haversineMeters(centerLat, centerLng, point.lat, point.lng)
            if (d > maxDistance) maxDistance = d
        }
        val radius = (maxDistance * 1.35 + 150.0).toFloat()
        return PolygonBounds(centerLat, centerLng, radius)
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    companion object {
        private val KEY_API_URL = stringPreferencesKey("api_url")
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_PASSWORD_LEGACY = "password"
        private val KEY_POLYGON = stringPreferencesKey("polygon")
        private val KEY_CENTER_LAT = stringPreferencesKey("center_lat")
        private val KEY_CENTER_LNG = stringPreferencesKey("center_lng")
        private val KEY_RADIUS = floatPreferencesKey("radius")
        private val KEY_AUTO_OPEN = booleanPreferencesKey("auto_open")
        private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val KEY_WAS_INSIDE = booleanPreferencesKey("was_inside")
        private val KEY_LAST_TRIGGER = longPreferencesKey("last_trigger")
    }
}
