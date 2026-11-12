package me.mjoacobf.openmusicstreaming.core.extractor

import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.SearchFilter
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Elige la miniatura más cercana al ancho deseado para no descargar imágenes enormes
 * (ahorro de datos y memoria en equipos de gama baja).
 */
fun List<Image>.pickUrl(targetWidth: Int = 480): String? {
    if (isEmpty()) return null
    val known = filter { it.width > 0 }
    if (known.isEmpty()) return first().url
    return (known.filter { it.width >= targetWidth }.minByOrNull { it.width }
        ?: known.maxByOrNull { it.width })?.url
}

fun cleanArtist(name: String?): String =
    name.orEmpty().removeSuffix(" - Topic").trim()

internal fun isAlbumUrl(url: String): Boolean =
    url.contains("list=OLAK5uy_") || url.contains("/browse/MPREb")

internal fun InfoItem.toSummary(filter: SearchFilter? = null): MediaSummary? = when (this) {
    is StreamInfoItem -> {
        if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM) {
            null
        } else {
            val isSong = when (filter) {
                SearchFilter.MUSIC_SONGS -> true
                SearchFilter.MUSIC_VIDEOS, SearchFilter.VIDEOS -> false
                else -> uploaderName.orEmpty().endsWith(" - Topic") ||
                    url.contains("music.youtube.com")
            }
            MediaSummary(
                url = url,
                id = videoIdFromUrl(url) ?: url,
                type = if (isSong) ItemType.SONG else ItemType.VIDEO,
                title = name,
                subtitle = cleanArtist(uploaderName),
                thumbnailUrl = thumbnails.pickUrl(320),
                durationSec = duration,
            )
        }
    }

    is PlaylistInfoItem -> {
        val album = filter == SearchFilter.MUSIC_ALBUMS || isAlbumUrl(url) ||
            playlistType == PlaylistInfo.PlaylistType.NORMAL && isAlbumUrl(url)
        MediaSummary(
            url = url,
            id = url.substringAfter("list=", url).substringBefore('&'),
            type = if (album) ItemType.ALBUM else ItemType.PLAYLIST,
            title = cleanCollectionTitle(name),
            subtitle = cleanArtist(uploaderName),
            thumbnailUrl = thumbnails.pickUrl(320),
            itemCount = streamCount,
        )
    }

    else -> null // canales y otros tipos no se muestran
}

fun videoIdFromUrl(url: String): String? =
    runCatching {
        org.schabi.newpipe.extractor.ServiceList.YouTube.streamLHFactory.getId(url)
    }.getOrNull()

/** YouTube Music antepone el tipo al título del álbum ("Album – X", "EP – X", "Single – X"). */
private val COLLECTION_PREFIX = Regex("^(Album|Álbum|EP|Single|Sencillo)\\s+[–-]\\s+", RegexOption.IGNORE_CASE)

fun cleanCollectionTitle(title: String): String = title.replace(COLLECTION_PREFIX, "").trim()
