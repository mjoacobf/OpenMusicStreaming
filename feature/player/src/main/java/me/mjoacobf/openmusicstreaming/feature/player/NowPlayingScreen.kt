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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import me.mjoacobf.openmusicstreaming.core.designsystem.MediaRow
import me.mjoacobf.openmusicstreaming.core.media.MediaIds
import me.mjoacobf.openmusicstreaming.core.media.PlayerState
import me.mjoacobf.openmusicstreaming.core.model.formatDuration
import me.mjoacobf.openmusicstreaming.feature.player.PlayerViewModel.Companion.toSummary

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(viewModel: PlayerViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarkedUrls.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    val item = state.currentItem

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
                    IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Cola") }
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
                if (state.hasVideo && MediaIds.isVideo(item.mediaId)) {
                    VideoSurface(viewModel.connection.player)
                } else {
                    Artwork(
                        item.mediaMetadata.artworkUri?.toString(),
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

@Composable
private fun QueueList(state: PlayerState, viewModel: PlayerViewModel) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Cola (${state.queue.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = viewModel::clearQueue) { Text("Vaciar") }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.8f)) {
        itemsIndexed(state.queue, key = { i, it -> "$i-${it.mediaId}" }, contentType = { _, _ -> "queue" }) { index, item ->
            MediaRow(
                title = item.mediaMetadata.title?.toString().orEmpty(),
                subtitle = item.mediaMetadata.artist?.toString().orEmpty(),
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                highlighted = index == state.currentIndex,
                leadingSize = 44.dp,
                onClick = { viewModel.skipTo(index) },
            ) {
                IconButton(onClick = { viewModel.move(index, index - 1) }, enabled = index > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Subir")
                }
                IconButton(onClick = { viewModel.move(index, index + 1) }, enabled = index < state.queue.size - 1) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Bajar")
                }
                IconButton(onClick = { viewModel.removeAt(index) }) { Icon(Icons.Rounded.Close, "Quitar") }
            }
        }
    }
}
