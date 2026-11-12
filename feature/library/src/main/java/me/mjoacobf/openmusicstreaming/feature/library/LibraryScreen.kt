package me.mjoacobf.openmusicstreaming.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mjoacobf.openmusicstreaming.core.designsystem.AddToPlaylistDialog
import me.mjoacobf.openmusicstreaming.core.designsystem.Artwork
import me.mjoacobf.openmusicstreaming.core.designsystem.PlaylistNameDialog
import me.mjoacobf.openmusicstreaming.core.designsystem.EmptyState
import me.mjoacobf.openmusicstreaming.core.designsystem.LoadingBox
import me.mjoacobf.openmusicstreaming.core.designsystem.MediaRow
import me.mjoacobf.openmusicstreaming.core.model.DownloadStatus
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.LocalPlaylist
import me.mjoacobf.openmusicstreaming.core.model.Song
import me.mjoacobf.openmusicstreaming.core.model.UserPlaylist
import me.mjoacobf.openmusicstreaming.core.model.formatDuration
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onOpenUserPlaylist: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onExplore: () -> Unit,
) {
    val full by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    // Búsqueda local: filtra canciones, playlists descargadas y listas propias por texto
    val state = remember(full, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            full
        } else {
            full.copy(
                songs = full.songs.filter {
                    it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
                },
                playlists = full.playlists.filter { it.title.contains(q, true) || it.uploader.contains(q, true) },
                userPlaylists = full.userPlaylists.filter { it.name.contains(q, true) },
            )
        }
    }
    val filtering = query.isNotBlank()
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }
    var toDelete by remember { mutableStateOf<LocalPlaylist?>(null) }
    var creating by remember { mutableStateOf(false) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            placeholder = { Text("Buscar en tu biblioteca") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        )
                    } else {
                        Text("Biblioteca")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) query = ""
                        searching = !searching
                    }) {
                        Icon(
                            if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                            if (searching) "Cerrar búsqueda" else "Buscar en la biblioteca",
                        )
                    }
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Rounded.History, "Historial") }
                    if (state.playlists.isNotEmpty()) {
                        IconButton(onClick = viewModel::syncAll) {
                            Icon(Icons.Rounded.Sync, "Actualizar playlists automáticas")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 1 && state.songs.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.shuffle(state.songs) },
                    icon = { Icon(Icons.Rounded.Shuffle, null) },
                    text = { Text("Aleatorio") },
                )
            } else if (tab == 2) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Nueva playlist") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Playlists (${state.playlists.size})") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Canciones (${state.songs.size})") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Mis listas (${state.userPlaylists.size})") })
            }
            ActiveDownloads(state.downloads.values.toList(), viewModel::dismissDownload, viewModel::cancelDownload)
            val nothingFound = filtering && when (tab) {
                0 -> state.playlists.isEmpty()
                1 -> state.songs.isEmpty()
                else -> state.userPlaylists.isEmpty()
            }
            when {
                state.loading -> LoadingBox()
                nothingFound -> EmptyState(
                    Icons.Rounded.Search,
                    "Sin resultados",
                    "Nada coincide con \"${query.trim()}\" en esta pestaña.",
                )

                tab == 2 && state.userPlaylists.isEmpty() -> EmptyState(
                    Icons.Rounded.PlaylistAdd,
                    "Crea tu primera playlist",
                    "Mezcla canciones de streaming y descargadas en tus propias listas.",
                    actionLabel = "Nueva playlist",
                    onAction = { creating = true },
                )

                tab == 2 -> UserPlaylistList(state.userPlaylists, onOpenUserPlaylist)

                tab == 0 && state.playlists.isEmpty() -> EmptyState(
                    Icons.Rounded.LibraryMusic,
                    "Aún no hay playlists descargadas",
                    "Busca una playlist o un álbum en Explorar y descárgalo para escucharlo sin conexión.",
                    actionLabel = "Explorar",
                    onAction = onExplore,
                )

                tab == 0 -> PlaylistList(
                    playlists = state.playlists,
                    downloads = state.downloads,
                    onOpen = { onOpenPlaylist(it.id) },
                    onAutoUpdate = viewModel::setAutoUpdate,
                    onSync = viewModel::sync,
                    onDelete = { toDelete = it },
                )

                state.songs.isEmpty() -> EmptyState(
                    Icons.Rounded.LibraryMusic,
                    "No hay canciones descargadas",
                    "Las canciones que descargues aparecerán aquí.",
                )

                else -> SongList(
                    songs = state.songs,
                    onPlay = { viewModel.play(state.songs, it) },
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    onDelete = viewModel::deleteSong,
                    onAddToPlaylist = { songForPlaylist = it },
                )
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = "Nueva playlist",
            confirmLabel = "Crear",
            onConfirm = { viewModel.createPlaylist(it); creating = false },
            onDismiss = { creating = false },
        )
    }
    songForPlaylist?.let { song ->
        AddToPlaylistDialog(
            playlists = state.userPlaylists,
            onPick = { viewModel.addSongToPlaylist(it.id, song); songForPlaylist = null },
            onCreate = { viewModel.createPlaylistWith(it, song); songForPlaylist = null },
            onDismiss = { songForPlaylist = null },
        )
    }

    toDelete?.let { playlist ->
        var deleteFiles by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar \"${playlist.title}\"") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { deleteFiles = !deleteFiles }) {
                    Checkbox(deleteFiles, { deleteFiles = it })
                    Text("Borrar también los archivos de audio (las canciones usadas por otras playlists se conservan)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist, deleteFiles)
                    toDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun ActiveDownloads(
    downloads: List<DownloadStatus>,
    onDismiss: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    if (downloads.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            downloads.take(4).forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val detail = when (d.state) {
                            DownloadStatus.State.QUEUED -> "En cola"
                            DownloadStatus.State.RUNNING -> if (d.total > 0) "${d.done}/${d.total}" else "Descargando…"
                            DownloadStatus.State.SUCCEEDED -> "Completado"
                            DownloadStatus.State.FAILED -> d.message ?: "Error"
                        }
                        Text("${d.title} · $detail", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (d.state == DownloadStatus.State.RUNNING || d.state == DownloadStatus.State.QUEUED) {
                            LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                    val running = d.state == DownloadStatus.State.RUNNING || d.state == DownloadStatus.State.QUEUED
                    IconButton(onClick = { if (running) onCancel(d.key) else onDismiss(d.key) }) {
                        Icon(Icons.Rounded.Close, if (running) "Cancelar" else "Descartar")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<LocalPlaylist>,
    downloads: Map<String, DownloadStatus>,
    onOpen: (LocalPlaylist) -> Unit,
    onAutoUpdate: (LocalPlaylist, Boolean) -> Unit,
    onSync: (LocalPlaylist) -> Unit,
    onDelete: (LocalPlaylist) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            val status = downloads[playlist.remoteUrl]
            val syncing = status?.state == DownloadStatus.State.RUNNING || status?.state == DownloadStatus.State.QUEUED
            PlaylistRow(playlist, syncing, onOpen, onAutoUpdate, onSync, onDelete)
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: LocalPlaylist,
    syncing: Boolean,
    onOpen: (LocalPlaylist) -> Unit,
    onAutoUpdate: (LocalPlaylist, Boolean) -> Unit,
    onSync: (LocalPlaylist) -> Unit,
    onDelete: (LocalPlaylist) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(playlist) }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(playlist.thumbnailUrl, size = 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val kind = if (playlist.type == ItemType.ALBUM) "Álbum" else "Playlist"
            Text(
                "$kind · ${playlist.songCount} canciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val extra = when {
                !playlist.remoteAvailable -> "Ya no está en YouTube (copia local)"
                playlist.lastError != null -> playlist.lastError!!
                playlist.lastSyncedAt > 0 -> "Actualizada " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(playlist.lastSyncedAt))
                else -> ""
            }
            if (extra.isNotEmpty()) {
                Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        // Interruptor de actualización automática, visible en cada playlist
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(checked = playlist.autoUpdate, onCheckedChange = { onAutoUpdate(playlist, it) })
            Text("Auto", style = MaterialTheme.typography.labelSmall)
        }
        if (syncing) {
            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { onSync(playlist) }) { Icon(Icons.Rounded.Sync, "Actualizar ahora") }
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, "Más opciones")
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Eliminar") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menu = false; onDelete(playlist) },
                )
            }
        }
    }
}

@Composable
internal fun SongList(
    songs: List<Song>,
    onPlay: (Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    missing: Set<String> = emptySet(),
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        if (header != null) item(key = "header", contentType = "header") { header() }
        itemsIndexed(songs, key = { _, s -> s.videoId }, contentType = { _, _ -> "song" }) { index, song ->
            SongRow(
                song = song,
                missingFromRemote = song.videoId in missing,
                onClick = { onPlay(index) },
                onPlayNext = { onPlayNext(song) },
                onAddToQueue = { onAddToQueue(song) },
                onDelete = { onDelete(song) },
                onAddToPlaylist = onAddToPlaylist?.let { add -> { add(song) } },
            )
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    missingFromRemote: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val subtitle = buildString {
        append(song.artist)
        if (song.durationSec > 0) append(" · ").append(formatDuration(song.durationSec))
        if (!song.isDownloaded) append(" · no descargada")
    }
    MediaRow(
        title = song.title,
        subtitle = subtitle,
        thumbnailUrl = song.thumbnailUrl,
        onClick = onClick,
        onLongClick = { menu = true },
    ) {
        if (missingFromRemote || !song.remoteAvailable) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = "No disponible en YouTube",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).size(18.dp),
            )
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, "Más opciones")
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Reproducir a continuación") }, onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Añadir a la cola") }, onClick = { menu = false; onAddToQueue() })
                if (onAddToPlaylist != null) {
                    DropdownMenuItem(text = { Text("Añadir a playlist…") }, onClick = { menu = false; onAddToPlaylist() })
                }
                DropdownMenuItem(text = { Text("Eliminar descarga") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun UserPlaylistList(playlists: List<UserPlaylist>, onOpen: (Long) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        items(playlists, key = { it.id }, contentType = { "user-playlist" }) { playlist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist.id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(playlist.coverUrl, size = 64.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${playlist.itemCount} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
