package com.lakh.racing.nav

enum class ManeuverType {
    SLIGHT_LEFT, LEFT, SHARP_LEFT,
    SLIGHT_RIGHT, RIGHT, SHARP_RIGHT,
    U_TURN, ARRIVE, CUSTOM,
}

data class Maneuver(
    val type: ManeuverType,
    val distanceAlongM: Double,
    val lat: Double,
    val lon: Double,
    /** Roadbook text for [ManeuverType.CUSTOM]. */
    val text: String?,
)

/** An organiser-supplied instruction pinned to a spot on the route. */
data class RoadbookNote(val lat: Double, val lon: Double, val text: String)
