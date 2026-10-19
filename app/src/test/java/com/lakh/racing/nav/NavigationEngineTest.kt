package com.lakh.racing.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationEngineTest {

    private val corner = TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 500.0)
    private val end = TestRoutes.L_RIGHT.last()

    private fun engine(checked: Set<String> = emptySet()) = NavigationEngine(
        route = TestRoutes.L_RIGHT,
        checkpoints = listOf(
            NavCheckpoint("cp1", "CP1", corner.lat, corner.lon, radiusM = 30.0, order = 1),
            NavCheckpoint("fin", "Finish", end.lat, end.lon, radiusM = 30.0, order = 2),
        ),
        notes = emptyList(),
        initiallyCheckedIn = checked,
    )

    private fun fix(p: LatLon, t: Long, speed: Float? = null) = Fix(p.lat, p.lon, t, speed)

    @Test
    fun beforeTurn_reportsRightTurnDistance() {
        val s = engine().update(fix(TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 300.0), 0))
        assertEquals(ManeuverType.RIGHT, s.nextManeuver?.type)
        assertEquals(200.0, s.distanceToManeuverM!!, 3.0)
        assertEquals(700.0, s.remainingM, 3.0)
        assertEquals("cp1", s.nextCheckpoint?.id)
        assertEquals(200.0, s.distanceToCheckpointM!!, 3.0)
        assertFalse(s.offRoute)
    }

    @Test
    fun checkpoint_checksInInsideRadius_andAdvances() {
        val e = engine()
        val s = e.update(fix(TestRoutes.offset(corner, 180.0, 10.0), 0))
        assertTrue("cp1" in s.checkedIn)
        assertEquals(listOf("cp1"), s.newlyCheckedIn)
        assertEquals("fin", s.nextCheckpoint?.id)
        // Next update doesn't re-report the event.
        val s2 = e.update(fix(TestRoutes.offset(corner, 90.0, 50.0), 5000))
        assertTrue(s2.newlyCheckedIn.isEmpty())
    }

    @Test
    fun checkpoints_mustBeTakenInOrder() {
        val s = engine().update(fix(end, 0))
        assertFalse("fin" in s.checkedIn)
        assertEquals("cp1", s.nextCheckpoint?.id)
    }

    @Test
    fun restoredProgress_isRespected() {
        val s = engine(setOf("cp1")).update(fix(end, 0))
        assertTrue(s.finished)
        assertNull(s.nextCheckpoint)
    }

    @Test
    fun offRoute_whenFarFromRoad() {
        val p = TestRoutes.offset(TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 200.0), 270.0, 120.0)
        val s = engine().update(fix(p, 0))
        assertTrue(s.offRoute)
    }

    @Test
    fun offRoute_keepsLastProgress_andGivesWayBack() {
        val e = engine()
        e.update(fix(TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 300.0), 0))
        // Wander 2 km east of the first leg, closer to the end of the route than to our progress.
        val far = TestRoutes.offset(TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 300.0), 90.0, 2000.0)
        val s = e.update(fix(far, 60_000))
        assertTrue(s.offRoute)
        assertEquals(300.0, s.distanceAlongM, 3.0)
        assertEquals(ManeuverType.RIGHT, s.nextManeuver?.type)
        // Back on track: progress resumes from where we left it.
        val back = e.update(fix(TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 350.0), 120_000))
        assertFalse(back.offRoute)
        assertEquals(350.0, back.distanceAlongM, 3.0)
    }

    @Test
    fun farFromRoute_beforeStart_progressIsZero() {
        val far = TestRoutes.offset(TestRoutes.ORIGIN, 180.0, 5000.0)
        val s = engine().update(fix(far, 0))
        assertTrue(s.offRoute)
        assertEquals(0.0, s.distanceAlongM, 0.001)
        assertEquals(5000.0, s.offsetM, 10.0)
        assertEquals(0.0, s.bearingToRouteDeg!!, 1.0)
    }

    @Test
    fun speed_usesGpsSpeed_whenPresent() {
        val s = engine().update(fix(TestRoutes.ORIGIN, 0, speed = 25f))
        assertEquals(90.0, s.speedKmh, 0.01)
    }

    @Test
    fun speed_fallsBackToDistanceOverTime() {
        val e = engine()
        e.update(fix(TestRoutes.ORIGIN, 0))
        val s = e.update(fix(TestRoutes.offset(TestRoutes.ORIGIN, 0.0, 100.0), 4000))
        assertEquals(90.0, s.speedKmh, 1.0)
    }
}
