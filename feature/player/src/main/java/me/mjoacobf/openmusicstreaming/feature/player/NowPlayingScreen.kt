package me.mjoacobf.openmusicstreaming.feature.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import me.mjoacobf.openmusicstreaming.core.designsystem.Artwork
import me.mjoacobf.openmusicstreaming.core.designsystem.EmptyState
import me.mjoacobf.openmusicstreaming.core.designsystem.AddToPlaylistDialog
import me.mjoacobf.openmusicstreaming.core.designsystem.MediaRow
import me.mjoacobf.openmusicstreaming.core.designsystem.reorderable
import me.mjoacobf.openmusicstreaming.core.designsystem.rememberReorderableState
import me.mjoacobf.openmusicstreaming.core.media.MediaIds
import me.mjoacobf.openmusicstreaming.core.media.PlayerState
import me.mjoacobf.openmusicstreaming.core.model.formatDuration
import me.mjoacobf.openmusicstreaming.core.media.toSummary
import me.mjoacobf.openmusicstreaming.core.model.upscaleThumbnail

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarkedUrls.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val item = state.currentItem

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerViewModel.Event.OpenArtist -> onOpenArtist(event.url)
                is PlayerViewModel.Event.OpenAlbum -> onOpenAlbum(event.url)
                is PlayerViewModel.Event.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    // La letra se busca al abrir el panel y al cambiar de canción mientras está visible
    val currentItem = state.currentItem
    LaunchedEffect(currentItem?.mediaId, showLyrics) {
        if (showLyrics && currentItem != null) viewModel.loadLyrics(currentItem)
    }

    // En segundo plano se desactiva la pista de video para ahorrar datos y batería
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setVideoEnabled(true)
                Lifecycle.Event.ON_STOP -> viewModel.setVideoEnabled(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Reproduciendo") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar") } },
                actions = {
                    item?.toSummary()?.let { summary ->
                        val isBookmarked = summary.url in bookmarked
                        IconButton(onClick = { viewModel.toggleBookmark(item) }) {
                            Icon(
                                if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                if (isBookmarked) "Quitar de marcadores" else "Añadir a marcadores",
                            )
                        }
                    }
                    if (item != null) {
                        IconButton(onClick = { showLyrics = !showLyrics }) {
                            Icon(
                                Icons.Rounded.Lyrics,
                                if (showLyrics) "Ocultar letra" else "Mostrar letra",
                                tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Cola") }
                    if (item != null) {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, "Más opciones")
                            DropdownMenu(menu, { menu = false }) {
                                if (item.toSummary() != null) {
                                    DropdownMenuItem(
                                        text = { Text("Añadir a playlist…") },
                                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                                        onClick = { menu = false; showAddToPlaylist = true },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Ver artista") },
                                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                    onClick = { menu = false; viewModel.openArtist(item) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Ver álbum") },
                                    leadingIcon = { Icon(Icons.Rounded.Album, null) },
                                    onClick = { menu = false; viewModel.openAlbum(item) },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) {
            EmptyState(
                Icons.AutoMirrored.Rounded.QueueMusic,
                "Nada en reproducción",
                "Elige una canción de tu biblioteca o de Explorar.",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showLyrics) {
                    LyricsPanel(lyricsState, viewModel, onRetry = { viewModel.retryLyrics(item) })
                } else if (state.hasVideo && MediaIds.isVideo(item.mediaId)) {
                    VideoSurface(viewModel.connection.player)
                } else {
                    Artwork(
                        upscaleThumbnail(item.mediaMetadata.artworkUri?.toString(), 1080),
                        Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f),
                        size = Dp.Unspecified,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                item.mediaMetadata.title?.toString().orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                item.mediaMetadata.artist?.toString().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            SeekBar(viewModel, state.durationMs)
            Controls(state, viewModel)
            if (MediaIds.isStreaming(item.mediaId)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reproducción automática", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Continúa con canciones recomendadas al terminar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(settings.autoplayRelated, viewModel::setAutoplay)
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddToPlaylist && item != null) {
        val dismiss = { showAddToPlaylist = false }
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { viewModel.addToPlaylist(it.id, item); dismiss() },
            onCreate = { viewModel.createPlaylistWith(it, item); dismiss() },
            onDismiss = dismiss,
        )
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueList(state, viewModel)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: Player?) {
    if (player == null) return
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
        PlayerSurface(player = player, surfaceType = SURFACE_TYPE_SURFACE_VIEW, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SeekBar(viewModel: PlayerViewModel, durationMs: Long) {
    val position by viewModel.position.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else position.toFloat()
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown.coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                viewModel.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
            enabled = durationMs > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shown.toLong() / 1000), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(durationMs / 1000), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Controls(state: PlayerState, viewModel: PlayerViewModel) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::toggleShuffle) {
            Icon(Icons.Rounded.Shuffle, "Aleatorio", tint = if (state.shuffle) active else inactive)
        }
        IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Anterior", Modifier.size(36.dp))
        }
        FilledIconButton(
            onClick = viewModel::togglePlay,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            if (state.isBuffering) {
                CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pausar" else "Reproducir",
                    Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = viewModel::next, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Siguiente", Modifier.size(36.dp))
        }
        IconButton(onClick = viewModel::cycleRepeat) {
            when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> Icon(Icons.Rounded.RepeatOne, "Repetir una", tint = active)
                Player.REPEAT_MODE_ALL -> Icon(Icons.Rounded.Repeat, "Repetir todo", tint = active)
                else -> Icon(Icons.Rounded.Repeat, "Repetición desactivada", tint = inactive)
            }
        }
    }
}

/** Elemento de la cola con una clave única aunque la misma canción esté repetida. */
private class QueueEntry(val key: String, val item: androidx.media3.common.MediaItem)

/**
 * Cola de reproducción: un toque reproduce ese elemento y, manteniendo presionado y arrastrando,
 * se reordena (el gesto lo gestiona [reorderable]); la X quita el elemento.
 */
@Composable
private fun QueueList(state: PlayerState, viewModel: PlayerViewModel) {
    val entries = remember(state.queue) {
        val seen = HashMap<String, Int>()
        state.queue.map { item ->
            val n = seen.merge(item.mediaId, 1, Int::plus)!!
            QueueEntry("${item.mediaId}#$n", item)
        }
    }
    val currentKey = entries.getOrNull(state.currentIndex)?.key
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))
    val reorder = rememberReorderableState(
        source = entries,
        key = { it.key },
        listState = listState,
        onCommit = { from, to -> viewModel.move(from, to) },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Cola (${state.queue.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = viewModel::clearQueue) { Text("Vaciar") }
    }
    Text(
        "Mantén presionado y arrastra para reordenar",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.8f)) {
        items(reorder.items, key = { it.key }, contentType = { "queue" }) { entry ->
            MediaRow(
                title = entry.item.mediaMetadata.title?.toString().orEmpty(),
                subtitle = entry.item.mediaMetadata.artist?.toString().orEmpty(),
                thumbnailUrl = entry.item.mediaMetadata.artworkUri?.toString(),
                modifier = reorderable(reorder, entry.key),
                highlighted = entry.key == currentKey,
                leadingSize = 44.dp,
                onClick = { viewModel.skipTo(reorder.items.indexOf(entry)) },
            ) {
                Icon(
                    Icons.Rounded.DragHandle, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(onClick = { viewModel.removeAt(reorder.items.indexOf(entry)) }) {
                    Icon(Icons.Rounded.Close, "Quitar")
                }
            }
        }
    }
}
