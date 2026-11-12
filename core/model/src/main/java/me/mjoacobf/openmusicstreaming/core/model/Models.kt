package me.mjoacobf.openmusicstreaming.core.model

/** Tipo de contenido remoto (YouTube / YouTube Music). */
enum class ItemType { SONG, VIDEO, PLAYLIST, ALBUM, CHANNEL }

/** Filtros de búsqueda, equivalentes a los de NewPipe para YouTube. */
enum class SearchFilter {
    MUSIC_SONGS,
    MUSIC_VIDEOS,
    MUSIC_ALBUMS,
    MUSIC_PLAYLISTS,
    VIDEOS,
    PLAYLISTS,
    ALL,
}

/** Elemento de una lista remota (resultado de búsqueda, relacionado, elemento de playlist...). */
data class MediaSummary(
    val url: String,
    val id: String,
    val type: ItemType,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationSec: Long = -1,
    val itemCount: Long = -1,
) {
    val isPlayable: Boolean get() = type == ItemType.SONG || type == ItemType.VIDEO
    val isCollection: Boolean get() = type == ItemType.PLAYLIST || type == ItemType.ALBUM
}

/** Token opaco de continuación para paginar (oculta el tipo Page de NewPipe). */
class PageToken(val value: Any)

data class PagedItems(
    val items: List<MediaSummary>,
    val nextPage: PageToken?,
)

/** Detalle de un video/canción remota con su sección de recomendados. */
data class StreamDetails(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val viewCount: Long,
    val description: String,
    val related: List<MediaSummary>,
)

/** Playlist o álbum remoto. */
data class RemoteCollection(
    val url: String,
    val id: String,
    val type: ItemType,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val streamCount: Long,
    val firstPage: PagedItems,
)

/** Canción descargada en el almacenamiento del dispositivo. */
data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val localUri: String?,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
    val remoteAvailable: Boolean,
) {
    val isDownloaded: Boolean get() = localUri != null
    val url: String get() = youtubeWatchUrl(videoId)
}

/** Playlist/álbum descargado y sincronizable con YouTube. */
data class LocalPlaylist(
    val id: Long,
    val remoteUrl: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val type: ItemType,
    val autoUpdate: Boolean,
    val lastSyncedAt: Long,
    val remoteAvailable: Boolean,
    val songCount: Int,
    val folderName: String,
    val treeUri: String,
    val lastError: String?,
)

data class PlaylistSong(
    val song: Song,
    val position: Int,
    val missingFromRemote: Boolean,
)

data class Bookmark(
    val url: String,
    val id: String,
    val type: ItemType,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val addedAt: Long,
) {
    fun toSummary() = MediaSummary(url, id, type, title, subtitle, thumbnailUrl, durationSec)
}

/** Estado de una tarea de descarga/sincronización visible en la UI. */
data class DownloadStatus(
    val key: String,
    val title: String,
    val state: State,
    val progress: Float,
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
) {
    enum class State { QUEUED, RUNNING, SUCCEEDED, FAILED }
}

fun youtubeWatchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
