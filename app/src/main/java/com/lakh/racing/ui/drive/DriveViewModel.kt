package com.lakh.racing.ui.drive

import android.app.Application
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lakh.racing.AppContainer
import com.lakh.racing.data.model.Race
import com.lakh.racing.location.LocationService
import com.lakh.racing.map.CameraMode
import com.lakh.racing.nav.Fix
import com.lakh.racing.nav.LatLon
import com.lakh.racing.nav.Maneuver
import com.lakh.racing.nav.NavState
import com.lakh.racing.nav.NavigationEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DriveViewModel(
    private val raceId: String,
    private val c: AppContainer,
    private val app: Application,
) : ViewModel() {

    val race = MutableStateFlow<Race?>(null)
    val nav = MutableStateFlow<NavState?>(null)
    /** Marker position: snapped to the track while on it, raw GPS when off route. */
    val position = MutableStateFlow<LatLon?>(null)
    /** Instruction right after the next one, shown as "then …" when it comes up quickly. */
    val thenManeuver = MutableStateFlow<Maneuver?>(null)
    val heading = MutableStateFlow<Float?>(null)
    val cameraMode = MutableStateFlow(CameraMode.FOLLOW)
    val gpsEnabled = c.locationHub.gpsEnabled
    val gpsAccuracyM = MutableStateFlow<Float?>(null)

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Tracking lives with the ViewModel so it survives rotation but stops when leaving the race.
        LocationService.start(app)
        viewModelScope.launch {
            val r = c.repository.race(raceId) ?: return@launch
            race.value = r
            val engine = NavigationEngine(r.routePoints, r.navCheckpoints(), r.roadbookNotes(), c.repository.checkedIn(raceId))
            c.locationHub.location.filterNotNull().collect { loc -> onLocation(r, engine, loc) }
        }
    }

    private suspend fun onLocation(race: Race, engine: NavigationEngine, loc: Location) {
        val state = engine.update(Fix(loc.latitude, loc.longitude, loc.time, if (loc.hasSpeed()) loc.speed else null))
        nav.value = state
        position.value = if (state.offRoute) LatLon(loc.latitude, loc.longitude) else state.snapped
        gpsAccuracyM.value = if (loc.hasAccuracy()) loc.accuracy else null
        // GPS bearing is noise when nearly stationary; keep the last good one.
        if (loc.hasBearing() && loc.speed > 2f) heading.value = loc.bearing

        val next = state.nextManeuver
        thenManeuver.value = next?.let { n ->
            engine.maneuvers.firstOrNull { it.distanceAlongM > n.distanceAlongM }
                ?.takeIf { it.distanceAlongM - n.distanceAlongM < THEN_WINDOW_M }
        }

        for (id in state.newlyCheckedIn) {
            c.repository.checkIn(raceId, id)
            val cp = race.checkpoints.first { it.id == id }
            _events.send("✓ ${cp.name} checked in (${state.checkedIn.size}/${race.checkpoints.size})")
        }
        if (state.finished && state.newlyCheckedIn.isNotEmpty()) _events.send("🏁 Stage complete!")
    }

    fun cycleCamera() {
        cameraMode.value = CameraMode.entries[(cameraMode.value.ordinal + 1) % CameraMode.entries.size]
    }

    override fun onCleared() {
        LocationService.stop(app)
    }

    private companion object {
        const val THEN_WINDOW_M = 150.0
    }
}
