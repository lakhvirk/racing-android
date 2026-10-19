package com.lakh.racing.data.importer

import com.lakh.racing.data.model.Checkpoint
import com.lakh.racing.data.model.Poi
import com.lakh.racing.data.model.PoiType
import com.lakh.racing.data.model.Race
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class RaceFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Parses race files in either the app's JSON format or GPX. */
object RaceParser {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(text: String, fallbackName: String = "Imported race"): Race {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        val race = when {
            trimmed.startsWith("{") -> parseJson(trimmed)
            trimmed.startsWith("<") -> parseGpx(trimmed, fallbackName)
            else -> throw RaceFormatException("Unrecognised race file: expected JSON or GPX")
        }
        return validate(race)
    }

    private fun parseJson(text: String): Race = try {
        json.decodeFromString<Race>(text)
    } catch (e: Exception) {
        throw RaceFormatException("Invalid race JSON: ${e.message}", e)
    }

    private fun parseGpx(text: String, fallbackName: String): Race {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                // Race files come from the internet: refuse external entities.
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                isExpandEntityReferences = false
            }
            factory.newDocumentBuilder().parse(text.byteInputStream())
        } catch (e: Exception) {
            throw RaceFormatException("Invalid GPX: ${e.message}", e)
        }

        fun Element.child(tag: String): String? =
            getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        fun Element.latLon() = listOf(getAttribute("lat").toDouble(), getAttribute("lon").toDouble())

        val trackPoints = doc.getElementsByTagName("trkpt").elements()
            .ifEmpty { doc.getElementsByTagName("rtept").elements() }
        val route = trackPoints.map { it.latLon() }

        val checkpoints = mutableListOf<Checkpoint>()
        val pois = mutableListOf<Poi>()
        doc.getElementsByTagName("wpt").elements().forEachIndexed { i, w ->
            val (lat, lon) = w.latLon()
            val name = w.child("name") ?: "Waypoint ${i + 1}"
            val type = w.child("type")?.lowercase().orEmpty()
            if (type == "checkpoint" || type == "cp") {
                checkpoints += Checkpoint(
                    id = "cp${checkpoints.size + 1}", name = name, lat = lat, lon = lon,
                    order = checkpoints.size + 1,
                )
            } else {
                pois += Poi(
                    id = "poi${pois.size + 1}", type = poiType(type), name = name, lat = lat, lon = lon,
                    note = w.child("desc") ?: w.child("cmt").orEmpty(),
                )
            }
        }

        val name = doc.documentElement.let { it.child("name") } ?: fallbackName
        return Race(
            id = slug(name) + "-" + Integer.toHexString(text.hashCode()),
            name = name,
            description = (doc.documentElement.child("desc")).orEmpty(),
            route = route,
            checkpoints = checkpoints,
            pois = pois,
        )
    }

    private fun poiType(type: String) = when {
        "repair" in type || "service" in type || "mechanic" in type -> PoiType.REPAIR
        "fuel" in type || "gas" in type || "petrol" in type -> PoiType.FUEL
        "medic" in type || "hospital" in type || "first aid" in type -> PoiType.MEDICAL
        "shop" in type || "store" in type -> PoiType.SHOP
        else -> PoiType.OTHER
    }

    private fun validate(race: Race): Race {
        if (race.route.size < 2) throw RaceFormatException("Race route needs at least two points")
        val coords = race.route.map { it.getOrNull(0) to it.getOrNull(1) } +
            race.checkpoints.map { it.lat to it.lon } + race.pois.map { it.lat to it.lon }
        for ((lat, lon) in coords) {
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                throw RaceFormatException("Race contains an invalid coordinate: [$lat, $lon]")
            }
        }
        if (race.checkpoints.map { it.id }.toSet().size != race.checkpoints.size) {
            throw RaceFormatException("Checkpoint ids must be unique")
        }
        return race.copy(id = race.id.ifBlank { slug(race.name) })
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "race" }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }
}
