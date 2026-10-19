package com.lakh.racing.ui.races

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lakh.racing.AppContainer
import com.lakh.racing.data.RaceSummary
import com.lakh.racing.data.db.OfflineState
import com.lakh.racing.data.model.Race
import com.lakh.racing.data.offline.DownloadProgress
import com.lakh.racing.ui.appViewModel
import com.lakh.racing.ui.formatBytes
import com.lakh.racing.ui.theme.RaceGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RaceListViewModel(private val c: AppContainer) : ViewModel() {
    val races: StateFlow<List<RaceSummary>?> =
        c.repository.races.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val progress = c.downloader.progress

    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun importUrl(url: String) = runImport { c.repository.importFromUrl(url.trim()) }
    fun importUri(uri: Uri) = runImport { c.repository.importFromUri(uri) }
    fun addSample() = runImport { c.repository.importSample() }

    private fun runImport(block: suspend () -> Race) {
        viewModelScope.launch {
            busy.value = true
            message.value = try {
                "Imported \"${block().name}\". Open it to download the map."
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            } finally {
                busy.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaceListScreen(onOpenRace: (String) -> Unit) {
    val vm = appViewModel { RaceListViewModel(it) }
    val races by vm.races.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importUri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Racing") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { menuOpen = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add race") },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Import from URL") }, onClick = { menuOpen = false; urlDialog = true })
                    DropdownMenuItem(
                        text = { Text("Import file (GPX / JSON)") },
                        onClick = { menuOpen = false; pickFile.launch(arrayOf("*/*")) },
                    )
                    DropdownMenuItem(text = { Text("Add sample race") }, onClick = { menuOpen = false; vm.addSample() })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val list = races
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(onSample = vm::addSample)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { race ->
                        RaceCard(race, progress[race.id]) { onOpenRace(race.id) }
                    }
                }
            }
        }
    }

    if (urlDialog) {
        var url by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("Import race from URL") },
            text = {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text("Link to .gpx or .json") },
                )
            },
            confirmButton = {
                TextButton(onClick = { urlDialog = false; vm.importUrl(url) }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyState(onSample: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Flag, contentDescription = null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("No races yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import the race file from your organiser while you still have internet at the start.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onSample) { Text("Try the sample race") }
    }
}

@Composable
private fun RaceCard(race: RaceSummary, progress: DownloadProgress?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(race.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (race.description.isNotBlank()) {
                Text(
                    race.description, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, text, tint) = when (race.offlineState) {
                    OfflineState.READY -> Triple(
                        Icons.Filled.CloudDone, "Offline ready · ${formatBytes(race.offlineBytes)}", RaceGreen,
                    )
                    OfflineState.DOWNLOADING -> Triple(
                        Icons.Filled.CloudDownload,
                        "Downloading map… ${((progress?.fraction ?: 0f) * 100).toInt()}%",
                        MaterialTheme.colorScheme.secondary,
                    )
                    OfflineState.FAILED -> Triple(Icons.Filled.ErrorOutline, "Map download failed", MaterialTheme.colorScheme.error)
                    OfflineState.NONE -> Triple(
                        Icons.Filled.CloudDownload, "Map not downloaded", MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
            }
        }
    }
}
