package com.lakh.racing.ui.prepare

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lakh.racing.AppContainer
import com.lakh.racing.data.db.OfflineState
import com.lakh.racing.data.model.PoiType
import com.lakh.racing.data.model.Race
import com.lakh.racing.location.LocationService
import com.lakh.racing.map.CameraMode
import com.lakh.racing.map.RaceMap
import com.lakh.racing.nav.NavFormat
import com.lakh.racing.nav.RouteMatcher
import com.lakh.racing.ui.appViewModel
import com.lakh.racing.ui.formatBytes
import com.lakh.racing.ui.theme.RaceGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrepareViewModel(private val raceId: String, private val c: AppContainer) : ViewModel() {
    val race = MutableStateFlow<Race?>(null)
    val summary = c.repository.observeSummary(raceId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val progress = c.downloader.progress.map { it[raceId] }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val checkedIn = c.repository.observeCheckIns(raceId).map { list -> list.map { it.checkpointId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        viewModelScope.launch {
            race.value = c.repository.race(raceId)
            // A download interrupted by the app being killed leaves a stale state; re-check the tiles.
            val s = c.repository.observeSummary(raceId).first() ?: return@launch
            val active = c.downloader.progress.value[raceId]?.let { !it.done && it.error == null } == true
            if (!active && s.offlineState in setOf(OfflineState.READY, OfflineState.DOWNLOADING)) {
                if (!c.downloader.verify(raceId)) c.repository.setOffline(raceId, OfflineState.NONE)
            }
        }
    }

    fun download() = race.value?.let(c.downloader::start)
    fun cancel() = c.downloader.cancel(raceId)
    fun resetProgress() = viewModelScope.launch { c.repository.resetProgress(raceId) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        c.downloader.cancel(raceId)
        c.downloader.deleteRegions(raceId)
        c.repository.delete(raceId)
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareScreen(raceId: String, onBack: () -> Unit, onStart: () -> Unit, onPois: () -> Unit) {
    val vm = appViewModel { PrepareViewModel(raceId, it) }
    val race by vm.race.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val checkedIn by vm.checkedIn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasLocation by remember { mutableStateOf(LocationService.hasPermission(context)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var warnNotReady by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasLocation) onStart()
    }
    val requestAndStart = {
        if (hasLocation) {
            onStart()
        } else {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissions.launch(perms.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.name ?: "Race", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete race") }
                },
            )
        },
    ) { padding ->
        val r = race ?: return@Scaffold
        val s = summary ?: return@Scaffold
        val tilesReady = s.offlineState == OfflineState.READY
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RaceMap(
                race = r, checkedIn = checkedIn, position = null, bearing = null, cameraMode = CameraMode.OVERVIEW,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)),
                interactive = false,
            )
            if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodyMedium)
            Stats(r)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Before you lose signal", style = MaterialTheme.typography.titleMedium)
                    CheckRow(true, "Route, checkpoints and service points saved")
                    CheckRow(tilesReady, if (tilesReady) "Map downloaded (${formatBytes(s.offlineBytes)})" else "Map downloaded for offline use")
                    CheckRow(hasLocation, "Location permission granted")

                    val p = progress
                    when {
                        s.offlineState == OfflineState.DOWNLOADING && p != null -> {
                            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                            Text(
                                "Part ${p.part} of ${p.parts} · ${p.completedResources}/${p.requiredResources} · ${formatBytes(p.bytes)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            p.networkProblem?.let {
                                Text("Network problem, retrying… ($it)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = vm::cancel) { Text("Cancel download") }
                        }
                        else -> {
                            if (s.offlineState == OfflineState.FAILED) {
                                Text(
                                    "Download failed${p?.error?.let { ": $it" } ?: ""}",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            OutlinedButton(onClick = { vm.download() }) {
                                if (tilesReady) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Re-download map")
                                } else {
                                    Text("Download map for offline")
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onPois, modifier = Modifier.weight(1f)) { Text("Service points") }
                if (checkedIn.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.resetProgress() }, modifier = Modifier.weight(1f)) { Text("Reset progress") }
                }
            }

            Button(
                onClick = { if (tilesReady) requestAndStart() else warnNotReady = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    if (checkedIn.isEmpty()) "Start race" else "Continue race (${checkedIn.size}/${r.checkpoints.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    if (warnNotReady) {
        AlertDialog(
            onDismissRequest = { warnNotReady = false },
            title = { Text("Map not downloaded") },
            text = { Text("Without the offline map you'll only see the route line once you lose signal. Navigation, checkpoints and speed still work.") },
            confirmButton = { TextButton(onClick = { warnNotReady = false; requestAndStart() }) { Text("Start anyway") } },
            dismissButton = { TextButton(onClick = { warnNotReady = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete race?") },
            text = { Text("This removes the race, your progress and its downloaded map.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stats(race: Race) {
    val length = remember(race.id) { RouteMatcher(race.routePoints).totalLengthM }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(NavFormat.distance(length), "Distance")
        Stat(race.checkpoints.size.toString(), "Checkpoints")
        Stat(race.pois.count { it.type == PoiType.REPAIR }.toString(), "Repair points")
        Stat(race.pois.size.toString(), "All POIs")
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CheckRow(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (ok) RaceGreen else Color.Gray,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
