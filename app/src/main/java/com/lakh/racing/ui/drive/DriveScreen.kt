package com.lakh.racing.ui.drive

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lakh.racing.map.CameraMode
import com.lakh.racing.map.RaceMap
import com.lakh.racing.nav.Maneuver
import com.lakh.racing.nav.ManeuverType
import com.lakh.racing.nav.NavFormat
import com.lakh.racing.nav.NavState
import com.lakh.racing.ui.appViewModel
import com.lakh.racing.ui.compass
import com.lakh.racing.ui.theme.RaceGreen
import com.lakh.racing.ui.theme.RaceOrange
import kotlin.math.roundToInt

@Composable
fun DriveScreen(raceId: String, onBack: () -> Unit, onPois: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm = appViewModel { DriveViewModel(raceId, it, app) }
    val race by vm.race.collectAsStateWithLifecycle()
    val nav by vm.nav.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val then by vm.thenManeuver.collectAsStateWithLifecycle()
    val heading by vm.heading.collectAsStateWithLifecycle()
    val cameraMode by vm.cameraMode.collectAsStateWithLifecycle()
    val gpsEnabled by vm.gpsEnabled.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val r = race ?: return
    val state = nav
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RaceMap(
            race = r,
            checkedIn = state?.checkedIn ?: emptySet(),
            position = position,
            bearing = heading,
            cameraMode = cameraMode,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.safeDrawingPadding().fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                !gpsEnabled -> AlertBanner("GPS is turned off. Turn on Location to navigate.")
                state == null -> AlertBanner("Waiting for GPS fix…", color = Color(0xFF37474F))
                state.finished -> FinishedBanner()
                state.offRoute -> {
                    val way = "${NavFormat.distance(state.offsetM)} ${compass(state.bearingToRouteDeg ?: 0.0)}"
                    if (state.distanceAlongM == 0.0 && state.checkedIn.isEmpty()) {
                        AlertBanner("Drive to the start line · $way", color = Color(0xFF37474F))
                    } else {
                        AlertBanner("Off route · track is $way")
                    }
                }
                else -> state.nextManeuver?.let { ManeuverBanner(it, state.distanceToManeuverM ?: 0.0, then) }
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallFloatingActionButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave race")
            }
            SmallFloatingActionButton(onClick = vm::cycleCamera) {
                Icon(
                    when (cameraMode) {
                        CameraMode.FOLLOW -> Icons.Filled.Navigation
                        CameraMode.NORTH_UP -> Icons.Filled.Explore
                        CameraMode.OVERVIEW -> Icons.Filled.Map
                    },
                    contentDescription = "Map view: ${cameraMode.name.lowercase()}",
                )
            }
            SmallFloatingActionButton(onClick = onPois) {
                Icon(Icons.Filled.Build, contentDescription = "Service points")
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Speedometer(state?.speedKmh ?: 0.0)
            Spacer(Modifier.weight(1f))
            if (state != null) ProgressPanel(state, r.checkpoints.size)
        }

        // Above the bottom panels, clear of the position marker in the middle of the map.
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 150.dp))
    }
}

@Composable
private fun ManeuverBanner(m: Maneuver, distanceM: Double, then: Maneuver?) {
    Surface(color = Color(0xFF0D47A1), shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(m.type.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        if (distanceM < 15) "Now" else NavFormat.distance(distanceM),
                        color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black,
                    )
                    Text(NavFormat.label(m), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            then?.let {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Then", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Spacer(Modifier.size(6.dp))
                    Icon(it.type.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(NavFormat.label(it), color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun AlertBanner(text: String, color: Color = Color(0xFFC62828)) {
    Surface(color = color, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
            Spacer(Modifier.size(8.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FinishedBanner() {
    Surface(color = RaceGreen, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Flag, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.size(12.dp))
            Text("Stage complete", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun Speedometer(kmh: Double) {
    Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(kmh.roundToInt().toString(), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Black)
            Text("km/h", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProgressPanel(state: NavState, totalCheckpoints: Int) {
    Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
            state.nextCheckpoint?.let { cp ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "${cp.name} · ${NavFormat.distance(state.distanceToCheckpointM ?: 0.0)}",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                "Checkpoints ${state.checkedIn.size}/$totalCheckpoints",
                color = if (state.checkedIn.size == totalCheckpoints) RaceGreen else Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
            )
            Text("${NavFormat.distance(state.remainingM)} to go", color = RaceOrange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val ManeuverType.icon: ImageVector
    get() = when (this) {
        ManeuverType.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
        ManeuverType.LEFT -> Icons.Filled.TurnLeft
        ManeuverType.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
        ManeuverType.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
        ManeuverType.RIGHT -> Icons.Filled.TurnRight
        ManeuverType.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
        ManeuverType.U_TURN -> Icons.Filled.UTurnLeft
        ManeuverType.ARRIVE -> Icons.Filled.Flag
        ManeuverType.CUSTOM -> Icons.Filled.Info
    }
