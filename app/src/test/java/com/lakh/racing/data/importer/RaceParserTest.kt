package com.lakh.racing.data.importer

import com.lakh.racing.data.model.PoiType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class RaceParserTest {

    @Test
    fun parsesJson() {
        val race = RaceParser.parse(
            """
            { "id": "r1", "name": "Desert Run",
              "route": [[30.0, 76.0], [30.01, 76.0], [30.01, 76.01]],
              "checkpoints": [{"id": "c1", "name": "CP1", "lat": 30.01, "lon": 76.0, "order": 1}],
              "pois": [{"id": "p1", "type": "repair", "name": "Service Park", "lat": 30.0, "lon": 76.0}],
              "instructions": [{"lat": 30.01, "lon": 76.0, "text": "Right after the bridge"}],
              "someFutureField": true }
            """.trimIndent(),
        )
        assertEquals("r1", race.id)
        assertEquals(3, race.route.size)
        assertEquals(30.0, race.checkpoints[0].radiusM, 0.0)
        assertEquals(PoiType.REPAIR, race.pois[0].type)
        assertEquals("Right after the bridge", race.instructions[0].text)
    }

    @Test
    fun parsesGpx() {
        val race = RaceParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Hill Climb</name></metadata>
              <wpt lat="30.01" lon="76.0"><name>CP 1</name><type>checkpoint</type></wpt>
              <wpt lat="30.0" lon="76.0"><name>Fuel stop</name><type>Fuel</type><desc>Open 24h</desc></wpt>
              <trk><trkseg>
                <trkpt lat="30.0" lon="76.0"/><trkpt lat="30.01" lon="76.0"/><trkpt lat="30.01" lon="76.01"/>
              </trkseg></trk>
            </gpx>
            """.trimIndent(),
        )
        assertEquals("Hill Climb", race.name)
        assertEquals(3, race.route.size)
        assertEquals(listOf("CP 1"), race.checkpoints.map { it.name })
        assertEquals(PoiType.FUEL, race.pois.single().type)
        assertEquals("Open 24h", race.pois.single().note)
    }

    @Test
    fun rejectsTooShortRoute() {
        assertThrows(RaceFormatException::class.java) {
            RaceParser.parse("""{"id":"x","name":"x","route":[[1.0,2.0]]}""")
        }
    }

    @Test
    fun rejectsBadCoordinates() {
        assertThrows(RaceFormatException::class.java) {
            RaceParser.parse("""{"id":"x","name":"x","route":[[1.0,2.0],[95.0,2.0]]}""")
        }
    }

    @Test
    fun rejectsGarbage() {
        assertThrows(RaceFormatException::class.java) { RaceParser.parse("hello") }
    }

    @Test
    fun bundledSampleRace_isValid() {
        val race = RaceParser.parse(File("src/main/assets/sample_race.json").readText())
        assert(race.route.size > 10)
        assert(race.checkpoints.isNotEmpty())
        assert(race.pois.any { it.type == PoiType.REPAIR })
    }
}
