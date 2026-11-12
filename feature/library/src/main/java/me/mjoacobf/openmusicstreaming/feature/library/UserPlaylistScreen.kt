package me.mjoacobf.openmusicstreaming.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.UserPlaylistRepository
import me.mjoacobf.openmusicstreaming.core.designsystem.EmptyState
import me.mjoacobf.openmusicstreaming.core.designsystem.MediaRow
import me.mjoacobf.openmusicstreaming.core.designsystem.PlaylistNameDialog
import me.mjoacobf.openmusicstreaming.core.designsystem.rememberReorderableState
import me.mjoacobf.openmusicstreaming.core.designsystem.reorderable
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.media.toMediaItem
import me.mjoacobf.openmusicstreaming.core.model.UserPlaylist
import me.mjoacobf.openmusicstreaming.core.model.UserPlaylistItem
import me.mjoacobf.openmusicstreaming.core.model.formatDuration

class UserPlaylistViewModel(
    private val playlistId: Long,
    private val repository: UserPlaylistRepository,
    library: LibraryRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    data class UiState(
        val playlist: UserPlaylist? = null,
        val items: List<UserPlaylistItem> = emptyList(),
        val downloaded: Set<String> = emptySet(),
        val loaded: Boolean = false,
    )

    val state: StateFlow<UiState> = combine(
        repository.observe(playlistId),
        repository.observeItems(playlistId),
        library.observeDownloadedIds(),
    ) { playlist, items, downloaded -> UiState(playlist, items, downloaded, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun mediaItems() = state.value.items.map { it.summary.toMediaItem() }

    fun play(index: Int) = player.playItems(mediaItems(), index, shuffle = null)

    fun shuffle() = player.playItems(mediaItems(), 0, shuffle = true)

    fun playNext(item: UserPlaylistItem) = player.playNext(item.summary.toMediaItem())

    fun addToQueue(item: UserPlaylistItem) = player.addToQueue(item.summary.toMediaItem())

    fun remove(item: UserPlaylistItem) = viewModelScope.launch { repository.removeItem(item.rowId) }

    fun rename(name: String) = viewModelScope.launch { repository.rename(playlistId, name) }

    fun delete() = viewModelScope.launch { repository.delete(playlistId) }

    /** Guarda el orden tras arrastrar un elemento de [from] a [to]. */
    fun move(from: Int, to: Int) = viewModelScope.launch {
        val ids = state.value.items.map { it.rowId }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return@launch
        ids.add(to, ids.removeAt(from))
        repository.saveOrder(playlistId, ids)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPlaylistScreen(viewModel: UserPlaylistViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlist = state.playlist
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
                actions = {
                    if (playlist != null) {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, "Más opciones")
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Cambiar nombre") }, onClick = { menu = false; renaming = true })
                                DropdownMenuItem(text = { Text("Eliminar playlist") }, onClick = { menu = false; deleting = true })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> Unit
            playlist == null -> EmptyState(Icons.Rounded.PlaylistPlay, "Playlist no encontrada", "", Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                Icons.Rounded.PlaylistPlay,
                "Playlist vacía",
                "Añade canciones desde Explorar, la biblioteca o el reproductor con «Añadir a playlist».",
                Modifier.padding(padding),
            )

            else -> {
                val reorder = rememberReorderableState(
                    source = state.items,
                    key = { it.rowId },
                    headerCount = 1,
                    onCommit = { from, to -> viewModel.move(from, to) },
                )
                LazyColumn(Modifier.padding(padding).fillMaxSize(), state = reorder.listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "header", contentType = "header") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "${playlist.itemCount} canciones · mantén presionado y arrastra para reordenar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.play(0) }) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Text("Reproducir", Modifier.padding(start = 8.dp))
                                }
                                OutlinedButton(onClick = viewModel::shuffle) {
                                    Icon(Icons.Rounded.Shuffle, null)
                                    Text("Aleatorio", Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    items(reorder.items, key = { it.rowId }, contentType = { "item" }) { item ->
                        UserPlaylistRow(
                            item = item,
                            downloaded = item.summary.id in state.downloaded,
                            modifier = reorderable(reorder, item.rowId),
                            onClick = { viewModel.play(reorder.items.indexOf(item)) },
                            onPlayNext = { viewModel.playNext(item) },
                            onAddToQueue = { viewModel.addToQueue(item) },
                            onRemove = { viewModel.remove(item) },
                        )
                    }
                }
            }
        }
    }

    if (renaming && playlist != null) {
        PlaylistNameDialog(
            title = "Cambiar nombre",
            initial = playlist.name,
            onConfirm = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deleting && playlist != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Eliminar \"${playlist.name}\"") },
            text = { Text("Solo se borra la lista; las canciones descargadas se conservan.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(); deleting = false; onBack() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun UserPlaylistRow(
    item: UserPlaylistItem,
    downloaded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val summary = item.summary
    MediaRow(
        title = summary.title,
        subtitle = buildString {
            append(summary.subtitle)
            if (summary.durationSec > 0) append(" · ").append(formatDuration(summary.durationSec))
        },
        thumbnailUrl = summary.thumbnailUrl,
        modifier = modifier,
        onClick = onClick,
        onLongClick = null,
    ) {
        if (downloaded) {
            Icon(
                Icons.Rounded.DownloadDone, "Descargada",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp).size(18.dp),
            )
        }
        Icon(Icons.Rounded.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, "Más opciones")
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Reproducir a continuación") }, onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Añadir a la cola") }, onClick = { menu = false; onAddToQueue() })
                DropdownMenuItem(
                    text = { Text("Quitar de la playlist") },
                    leadingIcon = { Icon(Icons.Rounded.Close, null) },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}
