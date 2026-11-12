package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import me.mjoacobf.openmusicstreaming.core.designsystem.Artwork
import me.mjoacobf.openmusicstreaming.core.designsystem.ErrorBox
import me.mjoacobf.openmusicstreaming.core.designsystem.LoadingBox
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onBack: () -> Unit,
    onOpenStream: (String) -> Unit,
    onOpenLocalPlaylist: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var downloadTarget by remember { mutableStateOf<MediaSummary?>(null) }
    val scope = rememberCoroutineScope()
    val actions = viewModel.actions
    val listState = rememberLazyListState()

    val callbacks = remember(viewModel) {
        RemoteItemCallbacks(
            onOpen = { onOpenStream(it.url) },
            onPlay = { item ->
                val items = viewModel.state.value.items
                actions.playAll(items, items.indexOf(item).coerceAtLeast(0))
            },
            onPlayVideo = { actions.play(it, asVideo = true); onOpenPlayer() },
            onPlayNext = actions::playNext,
            onAddToQueue = actions::addToQueue,
            onDownload = { downloadTarget = it },
            onToggleBookmark = { item, b -> actions.toggleBookmark(scope, item, b) },
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }.distinctUntilChanged().collect { if (it) viewModel.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.collection?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
            )
        },
    ) { padding ->
        val collection = state.collection
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            collection == null -> ErrorBox(state.error ?: "Error", viewModel::load, Modifier.padding(padding))
            else -> {
                val summary = viewModel.summary()!!
                val isBookmarked = collection.url in bookmarked
                LazyColumn(Modifier.padding(padding), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "header", contentType = "header") {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Artwork(collection.thumbnailUrl, size = 112.dp)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(collection.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text(collection.uploader, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val kind = if (collection.type == ItemType.ALBUM) "Álbum" else "Playlist"
                                    Text(
                                        if (collection.streamCount > 0) "$kind · ${collection.streamCount} elementos" else kind,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { actions.playAll(state.items, 0); onOpenPlayer() }, enabled = state.items.isNotEmpty()) {
                                    Icon(Icons.Rounded.PlayArrow, null); Text(" Reproducir")
                                }
                                FilledTonalButton(
                                    onClick = { actions.playAll(state.items.shuffled(), 0); onOpenPlayer() },
                                    enabled = state.items.isNotEmpty(),
                                ) { Icon(Icons.Rounded.Shuffle, null); Text(" Aleatorio") }
                                if (state.localPlaylistId != null) {
                                    OutlinedButton(onClick = { onOpenLocalPlaylist(state.localPlaylistId!!) }) {
                                        Icon(Icons.Rounded.LibraryMusic, null); Text(" En biblioteca")
                                    }
                                } else {
                                    OutlinedButton(onClick = { downloadTarget = summary }) {
                                        Icon(Icons.Rounded.Download, null); Text(" Descargar todo")
                                    }
                                }
                                OutlinedButton(onClick = { actions.toggleBookmark(scope, summary, isBookmarked) }) {
                                    Icon(if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null)
                                    Text(if (isBookmarked) " Guardado" else " Marcador")
                                }
                            }
                        }
                    }
                    itemsIndexed(state.items, key = { _, it -> it.url }, contentType = { _, it -> it.type }) { _, item ->
                        RemoteItemRow(item, item.url in bookmarked, item.id in downloaded, callbacks)
                    }
                    if (state.loadingMore) {
                        item(key = "loading", contentType = "loading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }

    downloadTarget?.let { item ->
        DownloadDialog(
            title = item.title,
            isCollection = item.isCollection,
            defaultTreeUri = settings.downloadTreeUri,
            defaultFormat = settings.downloadFormat,
            defaultQuality = settings.downloadAudioQuality,
            onDismiss = { downloadTarget = null },
            onConfirm = { choice -> actions.download(scope, item, choice); downloadTarget = null },
        )
    }
}
