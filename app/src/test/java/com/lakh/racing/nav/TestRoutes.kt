package com.lakh.racing.nav

/** Builds synthetic routes by walking fixed distances along compass headings. */
object TestRoutes {
    private const val M_PER_DEG_LAT = 111_195.0

    fun walk(start: LatLon, vararg legs: Pair<Double, Double>, stepM: Double = 25.0): List<LatLon> {
        val pts = mutableListOf(start)
        var cur = start
        for ((headingDeg, lengthM) in legs) {
            var done = 0.0
            while (done < lengthM - 1e-6) {
                val step = minOf(stepM, lengthM - done)
                cur = offset(cur, headingDeg, step)
                pts += cur
                done += step
            }
        }
        return pts
    }

    fun offset(p: LatLon, headingDeg: Double, distM: Double): LatLon {
        val h = Math.toRadians(headingDeg)
        val dLat = distM * Math.cos(h) / M_PER_DEG_LAT
        val dLon = distM * Math.sin(h) / (M_PER_DEG_LAT * Math.cos(Math.toRadians(p.lat)))
        return LatLon(p.lat + dLat, p.lon + dLon)
    }

    val ORIGIN = LatLon(30.0, 76.0)

    /** 500 m north, then 500 m east: one right turn at 500 m. */
    val L_RIGHT = walk(ORIGIN, 0.0 to 500.0, 90.0 to 500.0)
}
