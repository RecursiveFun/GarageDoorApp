package com.garagedoor.app.data

import android.content.Context
import com.garagedoor.app.network.GarageApiClient

class GarageRepository(context: Context) {

    private val settingsRepository = SettingsRepository(context)

    suspend fun fetchStatus(): Result<GarageStatus> = runCatching {
        val settings = settingsRepository.getSettings()
        require(settings.hasCredentials) { "Missing API credentials" }
        val api = GarageApiClient.create(settings.apiBaseUrl, settings.apiToken)
        api.status()
    }

    suspend fun trigger(source: String = "android"): Result<TriggerResponse> = runCatching {
        val settings = settingsRepository.getSettings()
        require(settings.hasCredentials) { "Missing API credentials" }

        val now = System.currentTimeMillis()
        if (now - settings.lastTriggerEpochMs < AppSettings.TRIGGER_DEBOUNCE_MS) {
            return Result.success(TriggerResponse(ok = false, message = "Debounced"))
        }

        val api = GarageApiClient.create(settings.apiBaseUrl, settings.apiToken)
        val response = api.trigger()
        if (response.ok) {
            settingsRepository.recordTrigger(now)
        }
        response
    }
}
