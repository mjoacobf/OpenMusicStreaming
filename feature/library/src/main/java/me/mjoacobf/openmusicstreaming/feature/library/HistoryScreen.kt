package me.mjoacobf.openmusicstreaming.feature.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.PlayHistoryRepository
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.designsystem.EmptyState
import me.mjoacobf.openmusicstreaming.core.designsystem.MediaRow
import me.mjoacobf.openmusicstreaming.core.designsystem.SectionHeader
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.media.toMediaItem
import me.mjoacobf.openmusicstreaming.core.model.HistoryEntry
import me.mjoacobf.openmusicstreaming.core.model.formatDuration
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class HistoryViewModel(
    private val repository: PlayHistoryRepository,
    settings: SettingsRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    data class UiState(val entries: List<HistoryEntry> = emptyList(), val enabled: Boolean = true, val loaded: Boolean = false)

    val state: StateFlow<UiState> = combine(repository.observe(), settings.settings) { entries, s ->
        UiState(entries, s.savePlayHistory, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun play(entry: HistoryEntry) = player.playItems(listOf(entry.summary.toMediaItem()), 0, shuffle = null)

    fun playNext(entry: HistoryEntry) = player.playNext(entry.summary.toMediaItem())

    fun addToQueue(entry: HistoryEntry) = player.addToQueue(entry.summary.toMediaItem())

    fun remove(entry: HistoryEntry) = viewModelScope.launch { repository.delete(entry.id) }

    fun clear() = viewModelScope.launch { repository.clear() }
}

/** "Hoy", "Ayer" o la fecha, según el día en que se reprodujo. */
private fun dayLabel(timeMs: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timeMs }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(now, then)) return "Hoy"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(now, then)) return "Ayer"
    return DateFormat.getDateInstance(DateFormat.LONG).format(Date(timeMs))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Borrar historial") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.enabled) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "El historial está desactivado: no se registran nuevas reproducciones. Puedes activarlo en Ajustes.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (state.loaded && state.entries.isEmpty()) {
                EmptyState(Icons.Rounded.History, "Sin historial", "Las canciones que escuches aparecerán aquí.")
            } else {
                // Día de cada entrada calculado una vez por cambio de datos (la lista llega ordenada)
                val rows = remember(state.entries) {
                    val out = ArrayList<Any>(state.entries.size + 8)
                    var last: String? = null
                    state.entries.forEach { entry ->
                        val label = dayLabel(entry.playedAt)
                        if (label != last) {
                            out += label
                            last = label
                        }
                        out += entry
                    }
                    out
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(
                        rows,
                        key = { if (it is HistoryEntry) "e${it.id}" else "d$it" },
                        contentType = { if (it is HistoryEntry) "entry" else "day" },
                    ) { row ->
                        if (row is HistoryEntry) {
                            HistoryRow(row, viewModel)
                        } else {
                            SectionHeader(row as String)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Borrar historial") },
            text = { Text("Se eliminarán todas las reproducciones registradas.") },
            confirmButton = { TextButton(onClick = { viewModel.clear(); confirmClear = false }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, viewModel: HistoryViewModel) {
    var menu by remember { mutableStateOf(false) }
    val summary = entry.summary
    MediaRow(
        title = summary.title,
        subtitle = buildString {
            append(summary.subtitle)
            if (summary.durationSec > 0) append(" · ").append(formatDuration(summary.durationSec))
        },
        thumbnailUrl = summary.thumbnailUrl,
        onClick = { viewModel.play(entry) },
        onLongClick = { menu = true },
    ) {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, "Más opciones")
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Reproducir a continuación") }, onClick = { menu = false; viewModel.playNext(entry) })
                DropdownMenuItem(text = { Text("Añadir a la cola") }, onClick = { menu = false; viewModel.addToQueue(entry) })
                DropdownMenuItem(text = { Text("Quitar del historial") }, onClick = { menu = false; viewModel.remove(entry) })
            }
        }
    }
}
