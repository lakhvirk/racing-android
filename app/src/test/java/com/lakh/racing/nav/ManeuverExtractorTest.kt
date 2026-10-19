package com.lakh.racing.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverExtractorTest {

    private fun extract(route: List<LatLon>, notes: List<RoadbookNote> = emptyList()) =
        ManeuverExtractor.extract(route, RouteMatcher(route), notes)

    @Test
    fun straightRoute_onlyArrive() {
        val route = TestRoutes.walk(TestRoutes.ORIGIN, 0.0 to 1000.0)
        val ms = extract(route)
        assertEquals(listOf(ManeuverType.ARRIVE), ms.map { it.type })
    }

    @Test
    fun lShape_rightTurnAt500m() {
        val ms = extract(TestRoutes.L_RIGHT)
        assertEquals(listOf(ManeuverType.RIGHT, ManeuverType.ARRIVE), ms.map { it.type })
        assertEquals(500.0, ms[0].distanceAlongM, 3.0)
    }

    @Test
    fun classifiesSlightSharpAndUTurn() {
        val route = TestRoutes.walk(
            TestRoutes.ORIGIN,
            0.0 to 300.0,
            315.0 to 300.0,   // -45: slight left
            175.0 to 300.0,   // -140: sharp left
            355.0 to 300.0,   // +180: U-turn
        )
        val types = extract(route).map { it.type }
        assertEquals(
            listOf(ManeuverType.SLIGHT_LEFT, ManeuverType.SHARP_LEFT, ManeuverType.U_TURN, ManeuverType.ARRIVE),
            types,
        )
    }

    @Test
    fun gentleCurveNoise_isIgnored() {
        val route = TestRoutes.walk(TestRoutes.ORIGIN, 0.0 to 300.0, 10.0 to 300.0, 0.0 to 300.0)
        assertEquals(listOf(ManeuverType.ARRIVE), extract(route).map { it.type })
    }

    @Test
    fun roadbookNote_overridesNearbyComputedManeuver() {
        val corner = TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 505.0)
        val ms = extract(TestRoutes.L_RIGHT, listOf(RoadbookNote(corner.lat, corner.lon, "Right at the water tower")))
        assertEquals(2, ms.size)
        assertEquals(ManeuverType.CUSTOM, ms[0].type)
        assertEquals("Right at the water tower", ms[0].text)
    }

    @Test
    fun roadbookNote_awayFromTurns_isAdded() {
        val p = TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 200.0)
        val ms = extract(TestRoutes.L_RIGHT, listOf(RoadbookNote(p.lat, p.lon, "Caution: water crossing")))
        assertEquals(listOf(ManeuverType.CUSTOM, ManeuverType.RIGHT, ManeuverType.ARRIVE), ms.map { it.type })
    }
}
