package com.lakh.racing.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun distance() {
        assertEquals("40 m", NavFormat.distance(43.0))
        assertEquals("200 m", NavFormat.distance(204.0))
        assertEquals("1.2 km", NavFormat.distance(1240.0))
        assertEquals("15 km", NavFormat.distance(15_400.0))
    }

    @Test
    fun instruction() {
        val m = Maneuver(ManeuverType.LEFT, 0.0, 0.0, 0.0, null)
        assertEquals("Turn left in 200 m", NavFormat.instruction(m, 200.0))
        assertEquals("Turn left now", NavFormat.instruction(m, 8.0))
    }
}
