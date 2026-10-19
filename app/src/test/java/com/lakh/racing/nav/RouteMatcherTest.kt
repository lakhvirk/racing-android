package com.lakh.racing.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteMatcherTest {

    private val matcher = RouteMatcher(TestRoutes.L_RIGHT)

    @Test
    fun totalLength() {
        assertEquals(1000.0, matcher.totalLengthM, 2.0)
    }

    @Test
    fun match_onFirstLeg_givesDistanceAlong() {
        val p = TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 200.0)
        val m = matcher.match(p)
        assertEquals(200.0, m.distanceAlongM, 2.0)
        assertEquals(0.0, m.offsetM, 1.0)
    }

    @Test
    fun match_offsetFromSecondLeg() {
        val corner = TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 500.0)
        val onLeg = TestRoutes.offset(corner, 90.0, 300.0)
        val p = TestRoutes.offset(onLeg, 0.0, 40.0) // 40 m north of the road
        val m = matcher.match(p)
        assertEquals(800.0, m.distanceAlongM, 3.0)
        assertEquals(40.0, m.offsetM, 2.0)
    }

    @Test
    fun match_withHint_staysForwardOnSelfCrossingRoute() {
        // Out 300 m north and back 300 m south on the same road. Without a hint the
        // first leg wins; with a hint near the end the return leg must be chosen.
        val route = TestRoutes.walk(TestRoutes.ORIGIN, 0.0 to 300.0, 180.0 to 300.0)
        val m = RouteMatcher(route)
        val p = TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 100.0)
        val hinted = m.match(p, hintSegment = route.size - 6)
        assertEquals(500.0, hinted.distanceAlongM, 3.0)
    }
}
