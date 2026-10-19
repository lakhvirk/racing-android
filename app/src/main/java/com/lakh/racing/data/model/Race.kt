package com.lakh.racing.data.model

import com.lakh.racing.nav.LatLon
import com.lakh.racing.nav.NavCheckpoint
import com.lakh.racing.nav.RoadbookNote
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Everything a driver needs for one race. This is also the `race.json` import format. */
@Serializable
data class Race(
    val id: String,
    val name: String,
    val description: String = "",
    /** Ordered track as `[lat, lon]` pairs. */
    val route: List<List<Double>>,
    val checkpoints: List<Checkpoint> = emptyList(),
    val pois: List<Poi> = emptyList(),
    val instructions: List<Instruction> = emptyList(),
) {
    val routePoints: List<LatLon> get() = route.map { LatLon(it[0], it[1]) }

    fun navCheckpoints() = checkpoints.map { NavCheckpoint(it.id, it.name, it.lat, it.lon, it.radiusM, it.order) }

    fun roadbookNotes() = instructions.map { RoadbookNote(it.lat, it.lon, it.text) }
}

@Serializable
data class Checkpoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double = 30.0,
    val order: Int,
)

@Serializable
enum class PoiType {
    @SerialName("repair") REPAIR,
    @SerialName("fuel") FUEL,
    @SerialName("medical") MEDICAL,
    @SerialName("shop") SHOP,
    @SerialName("other") OTHER,
}

@Serializable
data class Poi(
    val id: String,
    val type: PoiType = PoiType.OTHER,
    val name: String,
    val lat: Double,
    val lon: Double,
    val note: String = "",
)

@Serializable
data class Instruction(val lat: Double, val lon: Double, val text: String)
