package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mjoacobf.openmusicstreaming.core.designsystem.Artwork
import me.mjoacobf.openmusicstreaming.core.designsystem.ErrorBox
import me.mjoacobf.openmusicstreaming.core.designsystem.LoadingBox
import me.mjoacobf.openmusicstreaming.core.designsystem.SectionHeader
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamDetailScreen(
    viewModel: StreamDetailViewModel,
    onBack: () -> Unit,
    onOpenStream: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var downloadTarget by remember { mutableStateOf<MediaSummary?>(null) }
    var playlistTarget by remember { mutableStateOf<MediaSummary?>(null) }
    val scope = rememberCoroutineScope()
    val actions = viewModel.actions

    val callbacks = remember(viewModel) {
        RemoteItemCallbacks(
            onOpen = { if (it.isCollection) onOpenCollection(it.url) else onOpenStream(it.url) },
            onPlay = { actions.play(it) },
            onPlayVideo = { actions.play(it, asVideo = true); onOpenPlayer() },
            onPlayNext = actions::playNext,
            onAddToQueue = actions::addToQueue,
            onDownload = { downloadTarget = it },
            onToggleBookmark = { item, b -> actions.toggleBookmark(scope, item, b) },
            onAddToPlaylist = { playlistTarget = it },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.details?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
            )
        },
    ) { padding ->
        val details = state.details
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            details == null -> ErrorBox(state.error ?: "Error", viewModel::load, Modifier.padding(padding))
            else -> {
                val summary = viewModel.summary()!!
                val isBookmarked = details.url in bookmarked
                val isDownloaded = details.id in downloaded
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "header", contentType = "header") {
                        Column(Modifier.padding(16.dp)) {
                            Artwork(
                                details.thumbnailUrl,
                                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                size = androidx.compose.ui.unit.Dp.Unspecified,
                                shape = RoundedCornerShape(12.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(details.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                buildString {
                                    append(details.uploader)
                                    if (details.durationSec > 0) append(" · ").append(formatDuration(details.durationSec))
                                    if (details.viewCount > 0) append(" · ").append("%,d".format(details.viewCount)).append(" vistas")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(onClick = { actions.play(summary); onOpenPlayer() }) {
                                    Icon(Icons.Rounded.Headphones, null); Text(" Escuchar")
                                }
                                FilledTonalButton(onClick = { actions.play(summary, asVideo = true); onOpenPlayer() }) {
                                    Icon(Icons.Rounded.OndemandVideo, null); Text(" Ver video")
                                }
                                OutlinedButton(onClick = { actions.addToQueue(summary) }) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null); Text(" Cola")
                                }
                                OutlinedButton(onClick = { downloadTarget = summary }, enabled = !isDownloaded) {
                                    Icon(if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download, null)
                                    Text(if (isDownloaded) " Descargada" else " Descargar")
                                }
                                OutlinedButton(onClick = { actions.toggleBookmark(scope, summary, isBookmarked) }) {
                                    Icon(if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null)
                                    Text(if (isBookmarked) " Guardado" else " Marcador")
                                }
                            }
                            if (details.description.isNotBlank()) {
                                var expanded by remember { mutableStateOf(false) }
                                Text(
                                    details.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .animateContentSize()
                                        .clickable { expanded = !expanded },
                                )
                            }
                        }
                    }
                    if (details.related.isNotEmpty()) {
                        item(key = "related-header", contentType = "section") { SectionHeader("Recomendados") }
                        items(details.related, key = { "r-" + it.url }, contentType = { it.type }) { item ->
                            RemoteItemRow(item, item.url in bookmarked, item.id in downloaded, callbacks)
                        }
                    }
                }
            }
        }
    }

    AddToPlaylistHost(actions, playlistTarget) { playlistTarget = null }

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
