package com.lakh.racing.nav

/** A GPS fix; [speedMps] is null when the receiver didn't report speed. */
data class Fix(val lat: Double, val lon: Double, val timeMs: Long, val speedMps: Float? = null)

data class NavCheckpoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
    val order: Int,
)

data class NavState(
    val snapped: LatLon,
    val distanceAlongM: Double,
    val remainingM: Double,
    val offsetM: Double,
    val offRoute: Boolean,
    /** When off route: direction from the driver to the nearest point on the track. */
    val bearingToRouteDeg: Double?,
    val nextManeuver: Maneuver?,
    val distanceToManeuverM: Double?,
    val nextCheckpoint: NavCheckpoint?,
    val distanceToCheckpointM: Double?,
    val checkedIn: Set<String>,
    /** Checkpoints reached by this update only, so the UI can notify once. */
    val newlyCheckedIn: List<String>,
    val speedKmh: Double,
    val finished: Boolean,
)

/** Turns GPS fixes into everything the driving screen shows. Fully offline. */
class NavigationEngine(
    route: List<LatLon>,
    checkpoints: List<NavCheckpoint>,
    notes: List<RoadbookNote>,
    initiallyCheckedIn: Set<String> = emptySet(),
) {
    private val matcher = RouteMatcher(route)
    val maneuvers: List<Maneuver> = ManeuverExtractor.extract(route, matcher, notes)
    private val checkpoints = checkpoints.sortedBy { it.order }
    private val checkpointAlong = this.checkpoints.associate { it.id to matcher.match(LatLon(it.lat, it.lon)).distanceAlongM }
    private val checkedIn = initiallyCheckedIn.toMutableSet()
    private var lastSegment: Int? = null
    private var lastAlong: Double? = null
    private var lastFix: Fix? = null

    val totalLengthM: Double get() = matcher.totalLengthM

    fun update(fix: Fix): NavState {
        val here = LatLon(fix.lat, fix.lon)
        val match = matcher.match(here, lastSegment)
        val offRoute = match.offsetM > OFF_ROUTE_M
        // While off route, the nearest track point can be anywhere (even the finish), so
        // progress is frozen at the last on-route position until the driver rejoins.
        val along: Double
        val snapped: LatLon
        if (offRoute) {
            along = lastAlong ?: 0.0
            snapped = matcher.pointAt(along)
        } else {
            lastSegment = match.segmentIndex
            lastAlong = match.distanceAlongM
            along = match.distanceAlongM
            snapped = match.snapped
        }

        val newly = mutableListOf<String>()
        var next = nextCheckpoint()
        while (next != null && Geo.distanceM(here, LatLon(next.lat, next.lon)) <= next.radiusM) {
            checkedIn += next.id
            newly += next.id
            next = nextCheckpoint()
        }
        val toCheckpoint = next?.let {
            val cpAlong = checkpointAlong.getValue(it.id)
            if (!offRoute && cpAlong > along) cpAlong - along else Geo.distanceM(here, LatLon(it.lat, it.lon))
        }

        val maneuver = maneuvers.firstOrNull { it.distanceAlongM >= along - PASSED_TOLERANCE_M }
        val remaining = (matcher.totalLengthM - along).coerceAtLeast(0.0)

        val speedKmh = speedKmh(fix)
        lastFix = fix

        return NavState(
            snapped = snapped,
            distanceAlongM = along,
            remainingM = remaining,
            offsetM = match.offsetM,
            offRoute = offRoute,
            bearingToRouteDeg = if (offRoute) Geo.bearingDeg(here, match.snapped) else null,
            nextManeuver = maneuver,
            distanceToManeuverM = maneuver?.let { (it.distanceAlongM - along).coerceAtLeast(0.0) },
            nextCheckpoint = next,
            distanceToCheckpointM = toCheckpoint,
            checkedIn = checkedIn.toSet(),
            newlyCheckedIn = newly,
            speedKmh = speedKmh,
            finished = if (checkpoints.isEmpty()) remaining < FINISH_M else next == null,
        )
    }

    private fun nextCheckpoint() = checkpoints.firstOrNull { it.id !in checkedIn }

    private fun speedKmh(fix: Fix): Double {
        fix.speedMps?.let { return it * 3.6 }
        val prev = lastFix ?: return 0.0
        val dt = (fix.timeMs - prev.timeMs) / 1000.0
        if (dt <= 0) return 0.0
        return Geo.distanceM(LatLon(prev.lat, prev.lon), LatLon(fix.lat, fix.lon)) / dt * 3.6
    }

    private companion object {
        const val OFF_ROUTE_M = 50.0
        const val PASSED_TOLERANCE_M = 5.0
        const val FINISH_M = 30.0
    }
}
