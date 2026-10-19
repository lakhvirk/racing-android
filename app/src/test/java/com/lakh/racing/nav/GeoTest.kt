package com.lakh.racing.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {

    @Test
    fun distance_oneDegreeLatitude_isAbout111km() {
        val d = Geo.distanceM(LatLon(0.0, 0.0), LatLon(1.0, 0.0))
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun bearing_cardinalDirections() {
        val o = LatLon(10.0, 10.0)
        assertEquals(0.0, Geo.bearingDeg(o, LatLon(10.1, 10.0)), 0.5)
        assertEquals(90.0, Geo.bearingDeg(o, LatLon(10.0, 10.1)), 0.5)
        assertEquals(180.0, Geo.bearingDeg(o, LatLon(9.9, 10.0)), 0.5)
        assertEquals(270.0, Geo.bearingDeg(o, LatLon(10.0, 9.9)), 0.5)
    }

    @Test
    fun turnAngle_normalizesToPlusMinus180() {
        assertEquals(90.0, Geo.turnAngleDeg(0.0, 90.0), 1e-9)
        assertEquals(-90.0, Geo.turnAngleDeg(0.0, 270.0), 1e-9)
        assertEquals(20.0, Geo.turnAngleDeg(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Geo.turnAngleDeg(10.0, 350.0), 1e-9)
    }

    @Test
    fun projectOnSegment_midpointAndOffset() {
        val a = LatLon(0.0, 0.0)
        val b = LatLon(0.0, 0.01) // ~1112 m east
        val p = LatLon(0.0005, 0.005) // ~55 m north of the midpoint
        val proj = Geo.projectOnSegment(p, a, b)
        assertEquals(0.5, proj.fraction, 0.01)
        assertEquals(55.6, proj.distanceM, 1.0)
    }

    @Test
    fun projectOnSegment_clampsBeyondEnds() {
        val a = LatLon(0.0, 0.0)
        val b = LatLon(0.0, 0.01)
        assertEquals(0.0, Geo.projectOnSegment(LatLon(0.0, -0.01), a, b).fraction, 1e-9)
        assertEquals(1.0, Geo.projectOnSegment(LatLon(0.0, 0.02), a, b).fraction, 1e-9)
    }
}
