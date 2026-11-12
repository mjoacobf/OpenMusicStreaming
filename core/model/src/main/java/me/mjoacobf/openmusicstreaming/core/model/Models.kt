package me.mjoacobf.openmusicstreaming.core.model

/** Tipo de contenido remoto (YouTube Music, SoundCloud, Bandcamp). */
enum class ItemType { SONG, VIDEO, PLAYLIST, ALBUM, CHANNEL }

/**
 * Filtros de búsqueda musicales (solo música: no se busca "cualquier video" de YouTube).
 * Equivalen a los filtros `music_*` de NewPipe en YouTube y a `tracks`/`playlists` en SoundCloud.
 */
enum class SearchFilter {
    MUSIC_SONGS,
    MUSIC_VIDEOS,
    MUSIC_ALBUMS,
    MUSIC_PLAYLISTS,
}

/**
 * Servicios de música soportados (los mismos que NewPipe). Los identificadores de canciones de
 * YouTube se guardan tal cual (11 caracteres, sin prefijo) para no migrar los datos de la v1.0;
 * los de SoundCloud y Bandcamp llevan el prefijo del servicio seguido de la URL de la pista
 * (`sc:https://soundcloud.com/artista/tema`), con lo que un mismo `id` basta para volver a resolverla.
 */
enum class MusicService(val label: String, val idPrefix: String, val filters: List<SearchFilter>) {
    YOUTUBE(
        "YouTube Music", "",
        listOf(
            SearchFilter.MUSIC_SONGS, SearchFilter.MUSIC_VIDEOS,
            SearchFilter.MUSIC_ALBUMS, SearchFilter.MUSIC_PLAYLISTS,
        ),
    ),
    SOUNDCLOUD("SoundCloud", "sc:", listOf(SearchFilter.MUSIC_SONGS, SearchFilter.MUSIC_PLAYLISTS)),

    /** Bandcamp no ofrece filtros: la búsqueda mezcla pistas y álbumes. */
    BANDCAMP("Bandcamp", "bc:", emptyList()),
    ;

    companion object {
        fun ofId(id: String): MusicService = entries.firstOrNull { it.idPrefix.isNotEmpty() && id.startsWith(it.idPrefix) } ?: YOUTUBE

        fun ofUrl(url: String): MusicService {
            val host = url.substringAfter("://").substringBefore('/').lowercase()
            return when {
                host.endsWith("soundcloud.com") -> SOUNDCLOUD
                host.endsWith("bandcamp.com") -> BANDCAMP
                else -> YOUTUBE
            }
        }
    }
}

/** URL pública de una canción a partir de su identificador interno. */
fun streamUrlOf(id: String): String =
    if (MusicService.ofId(id) == MusicService.YOUTUBE) youtubeWatchUrl(id) else id.substringAfter(':')

/** Identificador interno de una canción dada su URL y, si es de YouTube, el ID de video. */
fun idOf(service: MusicService, url: String, youtubeId: String?): String =
    if (service == MusicService.YOUTUBE) youtubeId ?: url else service.idPrefix + url

/**
 * Pide una versión mayor de una imagen de googleusercontent (`=w544-h544...`), usada por las
 * carátulas de YouTube Music. Otras URLs se devuelven sin cambios.
 */
fun upscaleThumbnail(url: String?, px: Int): String? {
    if (url == null) return null
    val match = SIZE_PARAMS.find(url) ?: return url
    return url.replaceRange(match.range, "=w$px-h$px")
}

private val SIZE_PARAMS = Regex("=w\\d+-h\\d+")

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
    val service: MusicService get() = MusicService.ofUrl(url)
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
    val url: String get() = streamUrlOf(videoId)
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

/** Playlist creada por el usuario: mezcla canciones de streaming y descargadas. */
data class UserPlaylist(
    val id: Long,
    val name: String,
    val itemCount: Int,
    /** Carátula de la primera canción. */
    val coverUrl: String?,
)

/** Elemento de una playlist del usuario; [rowId] identifica la fila (una canción puede repetirse). */
data class UserPlaylistItem(
    val rowId: Long,
    val summary: MediaSummary,
)

/** Reproducción registrada en el historial. */
data class HistoryEntry(
    val id: Long,
    val summary: MediaSummary,
    val playedAt: Long,
)

fun youtubeWatchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
