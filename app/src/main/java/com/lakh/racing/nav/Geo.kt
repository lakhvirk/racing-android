package com.lakh.racing.nav

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Result of projecting a point onto a segment: where along it (0..1) and how far from it. */
data class SegmentProjection(val fraction: Double, val distanceM: Double, val point: LatLon)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial bearing from [a] to [b], degrees clockwise from north in [0, 360). */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Signed change of heading in (-180, 180]; positive means turning right. */
    fun turnAngleDeg(inBearing: Double, outBearing: Double): Double {
        var d = (outBearing - inBearing) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    /**
     * Projects [p] onto segment [a]-[b] using a local equirectangular plane.
     * Accurate for segment lengths of a few km, which is all a race track needs.
     */
    fun projectOnSegment(p: LatLon, a: LatLon, b: LatLon): SegmentProjection {
        val kx = cos(Math.toRadians(a.lat)) * M_PER_DEG
        val ky = M_PER_DEG
        val bx = (b.lon - a.lon) * kx
        val by = (b.lat - a.lat) * ky
        val px = (p.lon - a.lon) * kx
        val py = (p.lat - a.lat) * ky
        val len2 = bx * bx + by * by
        val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dist = hypot(px - t * bx, py - t * by)
        return SegmentProjection(t, dist, interpolate(a, b, t))
    }

    fun interpolate(a: LatLon, b: LatLon, t: Double) =
        LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    internal const val M_PER_DEG = 111_195.0
}
