package com.garagedoor.app.geofence

/**
 * Debounces GPS jitter before treating a location as inside or outside the polygon.
 * Requires consecutive consistent readings before changing state or firing a trigger.
 */
class PolygonArrivalDetector {

    private var consecutiveInside = 0
    private var consecutiveOutside = 0

    fun reset() {
        consecutiveInside = 0
        consecutiveOutside = 0
    }

    fun evaluate(
        inside: Boolean,
        wasInside: Boolean,
        accuracyMeters: Float?,
    ): EvaluateResult {
        if (accuracyMeters != null && accuracyMeters > MAX_ACCURACY_METERS) {
            return EvaluateResult(
                shouldTrigger = false,
                newWasInside = wasInside,
                message = "Ignored fix (accuracy ${accuracyMeters.toInt()} m)",
            )
        }

        if (inside) {
            consecutiveInside++
            consecutiveOutside = 0
        } else {
            consecutiveOutside++
            consecutiveInside = 0
        }

        val confirmedInside = consecutiveInside >= REQUIRED_CONSECUTIVE_INSIDE
        val confirmedOutside = consecutiveOutside >= REQUIRED_CONSECUTIVE_OUTSIDE

        val newWasInside = when {
            confirmedInside -> true
            confirmedOutside -> false
            else -> wasInside
        }

        val shouldTrigger = confirmedInside && !wasInside

        val message = when {
            shouldTrigger -> "Entered boundary (${consecutiveInside} fixes)"
            confirmedOutside && wasInside -> "Left boundary (${consecutiveOutside} fixes)"
            !confirmedInside && !confirmedOutside && inside != wasInside -> "Debouncing…"
            else -> if (inside) "Inside" else "Outside"
        }

        return EvaluateResult(
            shouldTrigger = shouldTrigger,
            newWasInside = newWasInside,
            message = message,
        )
    }

    data class EvaluateResult(
        val shouldTrigger: Boolean,
        val newWasInside: Boolean,
        val message: String,
    )

    companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val REQUIRED_CONSECUTIVE_INSIDE = 2
        const val REQUIRED_CONSECUTIVE_OUTSIDE = 2
    }
}
