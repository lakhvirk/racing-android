package com.lakh.racing.nav

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Derives turn-by-turn instructions from the race route geometry alone, so they work
 * with no routing server. Roadbook notes from the organiser replace nearby computed turns.
 */
object ManeuverExtractor {
    private const val SIMPLIFY_TOLERANCE_M = 8.0
    private const val BEARING_SPAN_M = 30.0
    private const val MIN_TURN_DEG = 30.0
    private const val MIN_GAP_M = 25.0
    private const val NOTE_MERGE_M = 40.0

    fun extract(route: List<LatLon>, matcher: RouteMatcher, notes: List<RoadbookNote>): List<Maneuver> {
        val computed = mutableListOf<Maneuver>()
        val keep = simplify(route)
        for (k in 1 until keep.size - 1) {
            val i = keep[k]
            val d = matcher.cumulative[i]
            val before = matcher.pointAt(d - BEARING_SPAN_M)
            val after = matcher.pointAt(d + BEARING_SPAN_M)
            val angle = Geo.turnAngleDeg(Geo.bearingDeg(before, route[i]), Geo.bearingDeg(route[i], after))
            val type = classify(angle) ?: continue
            if (computed.isNotEmpty() && d - computed.last().distanceAlongM < MIN_GAP_M) continue
            computed += Maneuver(type, d, route[i].lat, route[i].lon, null)
        }

        val result = computed.toMutableList()
        for (note in notes) {
            val m = matcher.match(LatLon(note.lat, note.lon))
            result.removeAll { abs(it.distanceAlongM - m.distanceAlongM) <= NOTE_MERGE_M }
            result += Maneuver(ManeuverType.CUSTOM, m.distanceAlongM, note.lat, note.lon, note.text)
        }
        val end = route.last()
        result += Maneuver(ManeuverType.ARRIVE, matcher.totalLengthM, end.lat, end.lon, null)
        return result.sortedBy { it.distanceAlongM }
    }

    internal fun classify(angle: Double): ManeuverType? {
        val a = abs(angle)
        val right = angle > 0
        return when {
            a < MIN_TURN_DEG -> null
            a < 60 -> if (right) ManeuverType.SLIGHT_RIGHT else ManeuverType.SLIGHT_LEFT
            a < 120 -> if (right) ManeuverType.RIGHT else ManeuverType.LEFT
            a < 160 -> if (right) ManeuverType.SHARP_RIGHT else ManeuverType.SHARP_LEFT
            else -> ManeuverType.U_TURN
        }
    }

    /** Douglas–Peucker on a local metric plane; returns indices of kept vertices. */
    internal fun simplify(route: List<LatLon>): List<Int> {
        val kx = cos(Math.toRadians(route.first().lat)) * Geo.M_PER_DEG
        val xs = DoubleArray(route.size) { route[it].lon * kx }
        val ys = DoubleArray(route.size) { route[it].lat * Geo.M_PER_DEG }
        val keep = BooleanArray(route.size)
        keep[0] = true
        keep[route.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to route.size - 1) }
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var maxD = 0.0
            var idx = -1
            for (i in s + 1 until e) {
                val d = perpendicular(xs[i], ys[i], xs[s], ys[s], xs[e], ys[e])
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > SIMPLIFY_TOLERANCE_M) {
                keep[idx] = true
                stack.add(s to idx)
                stack.add(idx to e)
            }
        }
        return keep.indices.filter { keep[it] }
    }

    private fun perpendicular(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
