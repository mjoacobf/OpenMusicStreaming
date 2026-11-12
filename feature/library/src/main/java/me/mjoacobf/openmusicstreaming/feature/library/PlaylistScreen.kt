package me.mjoacobf.openmusicstreaming.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mjoacobf.openmusicstreaming.core.designsystem.Artwork
import me.mjoacobf.openmusicstreaming.core.designsystem.EmptyState
import me.mjoacobf.openmusicstreaming.core.designsystem.LoadingBox
import me.mjoacobf.openmusicstreaming.core.model.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(viewModel: PlaylistViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlist = state.playlist

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingBox(Modifier.padding(padding))
            playlist == null -> EmptyState(Icons.Rounded.Sync, "Playlist no encontrada", "", Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                val songs = state.songs.map { it.song }
                SongList(
                    songs = songs,
                    missing = state.songs.filter { it.missingFromRemote }.mapTo(HashSet()) { it.song.videoId },
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    onDelete = viewModel::deleteSong,
                    header = {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Artwork(playlist.thumbnailUrl, size = 112.dp)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(playlist.uploader, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${songs.size} canciones · ${formatSize(songs.totalSize())}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Carpeta: ${playlist.folderName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Actualización automática", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Descarga canciones nuevas y reordena según YouTube",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(playlist.autoUpdate, viewModel::setAutoUpdate)
                            }
                            playlist.lastError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!playlist.remoteAvailable) {
                                Text(
                                    "La playlist ya no está disponible en YouTube; tu copia local sigue funcionando.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::playAll, enabled = songs.isNotEmpty()) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Text("Reproducir")
                                }
                                FilledTonalButton(onClick = viewModel::shuffle, enabled = songs.isNotEmpty()) {
                                    Icon(Icons.Rounded.Shuffle, null)
                                    Text("Aleatorio")
                                }
                                val syncing = state.syncing?.state == DownloadStatus.State.RUNNING ||
                                    state.syncing?.state == DownloadStatus.State.QUEUED
                                FilledTonalIconButton(onClick = viewModel::sync, enabled = !syncing) {
                                    if (syncing) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Rounded.Sync, "Actualizar ahora")
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
