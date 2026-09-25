package com.garagedoor.app.geofence

import com.garagedoor.app.data.AppSettings
import com.garagedoor.app.data.GarageRepository
import com.garagedoor.app.data.SettingsRepository

class PolygonArrivalHandler(
    private val settingsRepository: SettingsRepository,
    private val garageRepository: GarageRepository,
    private val detector: PolygonArrivalDetector = PolygonArrivalDetector(),
) {

    suspend fun processGpsReading(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
    ): ArrivalActionResult {
        val settings = settingsRepository.getSettings()
        if (!settings.autoOpenEnabled || !settings.hasPolygon) {
            return ArrivalActionResult(message = "Auto-open off or no boundary")
        }

        val inside = PolygonUtil.contains(latitude, longitude, settings.polygon)
        return applyTransition(settings, inside, accuracyMeters)
    }

    private suspend fun applyTransition(
        settings: AppSettings,
        inside: Boolean,
        accuracyMeters: Float?,
    ): ArrivalActionResult {
        val evaluation = detector.evaluate(
            inside = inside,
            wasInside = settings.wasInsidePolygon,
            accuracyMeters = accuracyMeters,
        )

        settingsRepository.setWasInsidePolygon(evaluation.newWasInside)

        if (!evaluation.shouldTrigger) {
            return ArrivalActionResult(
                triggered = false,
                nowInside = evaluation.newWasInside,
                message = evaluation.message,
            )
        }

        if (!settings.hasCredentials) {
            return ArrivalActionResult(
                nowInside = evaluation.newWasInside,
                message = "Missing API credentials",
            )
        }

        val now = System.currentTimeMillis()
        if (now - settings.lastTriggerEpochMs < AppSettings.TRIGGER_DEBOUNCE_MS) {
            return ArrivalActionResult(
                nowInside = evaluation.newWasInside,
                message = "Debounced — triggered recently",
            )
        }

        return garageRepository.trigger("geofence")
            .fold(
                onSuccess = { response ->
                    if (response.ok) {
                        ArrivalActionResult(
                            triggered = true,
                            nowInside = evaluation.newWasInside,
                            message = "Garage triggered",
                        )
                    } else {
                        ArrivalActionResult(
                            nowInside = evaluation.newWasInside,
                            message = response.message ?: "Trigger rejected",
                        )
                    }
                },
                onFailure = { error ->
                    ArrivalActionResult(
                        nowInside = evaluation.newWasInside,
                        message = error.message ?: "Network error",
                    )
                },
            )
    }

    data class ArrivalActionResult(
        val triggered: Boolean = false,
        val nowInside: Boolean = false,
        val message: String,
    )
}
