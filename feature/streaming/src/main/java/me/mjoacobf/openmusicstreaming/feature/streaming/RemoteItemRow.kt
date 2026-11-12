package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mjoacobf.openmusicstreaming.core.designsystem.AddToPlaylistDialog
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.mjoacobf.openmusicstreaming.core.designsystem.MediaRow
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import me.mjoacobf.openmusicstreaming.core.model.formatDuration

/** Callbacks de una fila de resultado remoto. */
class RemoteItemCallbacks(
    val onOpen: (MediaSummary) -> Unit,
    val onPlay: (MediaSummary) -> Unit,
    val onPlayVideo: (MediaSummary) -> Unit,
    val onPlayNext: (MediaSummary) -> Unit,
    val onAddToQueue: (MediaSummary) -> Unit,
    val onDownload: (MediaSummary) -> Unit,
    val onToggleBookmark: (MediaSummary, Boolean) -> Unit,
    val onAddToPlaylist: (MediaSummary) -> Unit,
)

fun MediaSummary.describe(): String {
    val kind = when (type) {
        ItemType.SONG -> "Canción"
        ItemType.VIDEO -> "Video"
        ItemType.PLAYLIST -> "Playlist"
        ItemType.ALBUM -> "Álbum"
        ItemType.CHANNEL -> "Canal"
    }
    return buildString {
        append(kind)
        if (service != MusicService.YOUTUBE) append(" · ").append(service.label)
        if (subtitle.isNotBlank()) append(" · ").append(subtitle)
        if (durationSec > 0) append(" · ").append(formatDuration(durationSec))
        if (itemCount > 0) append(" · ").append(itemCount).append(" elementos")
    }
}

@Composable
fun RemoteItemRow(
    item: MediaSummary,
    bookmarked: Boolean,
    downloaded: Boolean,
    callbacks: RemoteItemCallbacks,
    highlighted: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    MediaRow(
        title = item.title,
        subtitle = item.describe(),
        thumbnailUrl = item.thumbnailUrl,
        highlighted = highlighted,
        onClick = { if (item.type == ItemType.SONG) callbacks.onPlay(item) else callbacks.onOpen(item) },
        onLongClick = { menu = true },
    ) {
        if (downloaded) {
            Icon(
                Icons.Rounded.DownloadDone, "Descargada",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp).size(18.dp),
            )
        }
        if (bookmarked) {
            Icon(
                Icons.Rounded.Bookmark, "En marcadores",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp).size(18.dp),
            )
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, "Más opciones")
            DropdownMenu(menu, { menu = false }) {
                if (item.isPlayable) {
                    DropdownMenuItem(text = { Text("Reproducir audio") }, onClick = { menu = false; callbacks.onPlay(item) })
                    if (item.service == MusicService.YOUTUBE) {
                        DropdownMenuItem(text = { Text("Ver video") }, onClick = { menu = false; callbacks.onPlayVideo(item) })
                    }
                    DropdownMenuItem(text = { Text("Detalles y recomendados") }, onClick = { menu = false; callbacks.onOpen(item) })
                    DropdownMenuItem(text = { Text("Reproducir a continuación") }, onClick = { menu = false; callbacks.onPlayNext(item) })
                    DropdownMenuItem(text = { Text("Añadir a la cola") }, onClick = { menu = false; callbacks.onAddToQueue(item) })
                    DropdownMenuItem(text = { Text("Añadir a playlist…") }, onClick = { menu = false; callbacks.onAddToPlaylist(item) })
                } else {
                    DropdownMenuItem(text = { Text("Abrir") }, onClick = { menu = false; callbacks.onOpen(item) })
                }
                DropdownMenuItem(
                    text = { Text(if (item.isCollection) "Descargar todo" else "Descargar") },
                    onClick = { menu = false; callbacks.onDownload(item) },
                )
                DropdownMenuItem(
                    text = { Text(if (bookmarked) "Quitar de marcadores" else "Añadir a marcadores") },
                    leadingIcon = { Icon(if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null) },
                    onClick = { menu = false; callbacks.onToggleBookmark(item, bookmarked) },
                )
            }
        }
    }
}


/** Diálogo "Añadir a playlist" para el elemento indicado (nulo = oculto). */
@Composable
fun AddToPlaylistHost(actions: RemoteActions, target: MediaSummary?, onDismiss: () -> Unit) {
    val item = target ?: return
    val playlists by actions.playlistsFlow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    AddToPlaylistDialog(
        playlists = playlists,
        onPick = { actions.addToPlaylist(scope, it.id, item); onDismiss() },
        onCreate = { actions.createPlaylistWith(scope, it, item); onDismiss() },
        onDismiss = onDismiss,
    )
}
