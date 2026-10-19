package com.lakh.racing.nav

data class RouteMatch(
    val segmentIndex: Int,
    val distanceAlongM: Double,
    val offsetM: Double,
    val snapped: LatLon,
)

/** Snaps positions onto an ordered route polyline. */
class RouteMatcher(val route: List<LatLon>) {

    init {
        require(route.size >= 2) { "Route needs at least two points" }
    }

    /** cumulative[i] = distance along the route at vertex i. */
    val cumulative: DoubleArray = DoubleArray(route.size).also { c ->
        for (i in 1 until route.size) c[i] = c[i - 1] + Geo.distanceM(route[i - 1], route[i])
    }

    val totalLengthM: Double get() = cumulative.last()

    /**
     * Finds the closest point on the route. With a [hintSegment] (the last matched segment)
     * only a window around it is searched first, so routes that double back on themselves
     * keep progressing forward instead of jumping to an earlier leg.
     */
    fun match(p: LatLon, hintSegment: Int? = null): RouteMatch {
        val lastSeg = route.size - 2
        if (hintSegment != null) {
            val from = (hintSegment - WINDOW_BACK).coerceIn(0, lastSeg)
            val to = (hintSegment + WINDOW_AHEAD).coerceIn(0, lastSeg)
            val local = bestIn(p, from, to)
            if (local.offsetM <= REACQUIRE_M) return local
        }
        return bestIn(p, 0, lastSeg)
    }

    /** Point on the route at [distanceM] from the start (clamped to the route ends). */
    fun pointAt(distanceM: Double): LatLon {
        val d = distanceM.coerceIn(0.0, totalLengthM)
        var i = cumulative.binarySearch(d).let { if (it >= 0) it else -it - 2 }
        i = i.coerceIn(0, route.size - 2)
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen == 0.0) 0.0 else (d - cumulative[i]) / segLen
        return Geo.interpolate(route[i], route[i + 1], t)
    }

    private fun bestIn(p: LatLon, from: Int, to: Int): RouteMatch {
        var best: RouteMatch? = null
        for (i in from..to) {
            val proj = Geo.projectOnSegment(p, route[i], route[i + 1])
            if (best == null || proj.distanceM < best.offsetM) {
                val along = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * proj.fraction
                best = RouteMatch(i, along, proj.distanceM, proj.point)
            }
        }
        return best!!
    }

    private companion object {
        const val WINDOW_BACK = 5
        const val WINDOW_AHEAD = 40
        const val REACQUIRE_M = 100.0
    }
}
