package com.lakh.racing.nav

import com.lakh.racing.data.importer.RaceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Drives the bundled sample stage end to end, one fix every 10 m, as the emulator would. */
class SampleDriveTest {

    @Test
    fun drivingTheWholeStage_checksInEverythingAndFinishes() {
        val race = RaceParser.parse(File("src/main/assets/sample_race.json").readText())
        val engine = NavigationEngine(race.routePoints, race.navCheckpoints(), race.roadbookNotes())
        val matcher = RouteMatcher(race.routePoints)

        val order = mutableListOf<String>()
        var last: NavState? = null
        var t = 0L
        var d = 0.0
        while (d <= matcher.totalLengthM) {
            val p = matcher.pointAt(d)
            val s = engine.update(Fix(p.lat, p.lon, t))
            assertFalse("off route at $d m", s.offRoute)
            order += s.newlyCheckedIn
            last = s
            d += 10.0
            t += 720 // 50 km/h
        }
        assertEquals(50.0, last!!.speedKmh, 2.0)
        val end = race.routePoints.last()
        last = engine.update(Fix(end.lat, end.lon, t))
        order += last.newlyCheckedIn

        assertEquals(listOf("cp1", "cp2", "cp3", "finish"), order)
        assertTrue(last.finished)
        // A winding hill road should yield a meaningful number of instructions.
        assertTrue("maneuvers: ${engine.maneuvers.size}", engine.maneuvers.size in 15..400)
        assertTrue(engine.maneuvers.any { it.type == ManeuverType.CUSTOM })
    }
}
