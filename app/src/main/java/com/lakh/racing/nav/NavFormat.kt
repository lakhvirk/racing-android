package com.lakh.racing.nav

import java.util.Locale
import kotlin.math.roundToInt

object NavFormat {
    private const val NOW_M = 15.0

    fun distance(m: Double): String = when {
        m < 1000 -> "${((m / 10).roundToInt() * 10)} m"
        m < 10_000 -> String.format(Locale.US, "%.1f km", m / 1000)
        else -> "${(m / 1000).roundToInt()} km"
    }

    fun label(m: Maneuver): String = when (m.type) {
        ManeuverType.SLIGHT_LEFT -> "Keep slight left"
        ManeuverType.LEFT -> "Turn left"
        ManeuverType.SHARP_LEFT -> "Sharp left"
        ManeuverType.SLIGHT_RIGHT -> "Keep slight right"
        ManeuverType.RIGHT -> "Turn right"
        ManeuverType.SHARP_RIGHT -> "Sharp right"
        ManeuverType.U_TURN -> "Make a U-turn"
        ManeuverType.ARRIVE -> "Finish"
        ManeuverType.CUSTOM -> m.text.orEmpty()
    }

    fun instruction(m: Maneuver, distanceM: Double): String =
        if (distanceM < NOW_M) "${label(m)} now" else "${label(m)} in ${distance(distanceM)}"
}
