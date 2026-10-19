package com.lakh.racing.ui.pois

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lakh.racing.AppContainer
import com.lakh.racing.data.model.Poi
import com.lakh.racing.data.model.PoiType
import com.lakh.racing.data.model.Race
import com.lakh.racing.nav.Geo
import com.lakh.racing.nav.LatLon
import com.lakh.racing.nav.NavFormat
import com.lakh.racing.ui.appViewModel
import com.lakh.racing.ui.color
import com.lakh.racing.ui.compass
import com.lakh.racing.ui.icon
import com.lakh.racing.ui.label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PoiViewModel(raceId: String, c: AppContainer) : ViewModel() {
    val race = MutableStateFlow<Race?>(null)
    val location = c.locationHub.location

    init {
        viewModelScope.launch { race.value = c.repository.race(raceId) }
    }
}

/** Nearest repair, fuel and medical points, computed entirely on the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiScreen(raceId: String, onBack: () -> Unit) {
    val vm = appViewModel { PoiViewModel(raceId, it) }
    val race by vm.race.collectAsStateWithLifecycle()
    val location by vm.location.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<PoiType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service points") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val r = race ?: return@Scaffold
        val here = location?.let { LatLon(it.latitude, it.longitude) }
        val origin = here ?: r.routePoints.first()
        val pois = r.pois
            .filter { filter == null || it.type == filter }
            .map { it to Geo.distanceM(origin, LatLon(it.lat, it.lon)) }
            .sortedBy { it.second }

        Column(Modifier.padding(padding)) {
            Text(
                if (here != null) "Distances from your position" else "No GPS fix: distances from the start line",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") }) }
                items(PoiType.entries.filter { t -> r.pois.any { it.type == t } }) { t ->
                    FilterChip(
                        selected = filter == t, onClick = { filter = if (filter == t) null else t },
                        label = { Text(t.label) },
                        leadingIcon = { Icon(t.icon, contentDescription = null, tint = t.color, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pois.isEmpty()) item { Text("No points of this type in the race file.") }
                items(pois, key = { it.first.id }) { (poi, dist) -> PoiRow(poi, dist, Geo.bearingDeg(origin, LatLon(poi.lat, poi.lon))) }
            }
        }
    }
}

@Composable
private fun PoiRow(poi: Poi, distanceM: Double, bearing: Double) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(poi.type.icon, contentDescription = poi.type.label, tint = poi.type.color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(poi.name, style = MaterialTheme.typography.titleMedium)
                if (poi.note.isNotBlank()) Text(poi.note, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(NavFormat.distance(distanceM), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(compass(bearing), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
