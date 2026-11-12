package me.mjoacobf.openmusicstreaming.core.extractor

import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import me.mjoacobf.openmusicstreaming.core.model.SearchFilter
import me.mjoacobf.openmusicstreaming.core.model.idOf
import me.mjoacobf.openmusicstreaming.core.model.upscaleThumbnail
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Servicio de NewPipe que corresponde a cada [MusicService]. */
internal val MusicService.newPipe: StreamingService
    get() = when (this) {
        MusicService.YOUTUBE -> ServiceList.YouTube
        MusicService.SOUNDCLOUD -> ServiceList.SoundCloud
        MusicService.BANDCAMP -> ServiceList.Bandcamp
    }

/**
 * Elige la miniatura más cercana al ancho deseado. En las carátulas de YouTube Music
 * (googleusercontent) el tamaño va en la propia URL, así que se pide exactamente el ancho
 * deseado aunque la lista de NewPipe solo traiga tamaños pequeños.
 */
fun List<Image>.pickUrl(targetWidth: Int = 480): String? {
    if (isEmpty()) return null
    val known = filter { it.width > 0 }
    val best = if (known.isEmpty()) {
        first().url
    } else {
        (known.filter { it.width >= targetWidth }.minByOrNull { it.width }
            ?: known.maxByOrNull { it.width })?.url
    }
    return upscaleThumbnail(best, targetWidth)
}

fun cleanArtist(name: String?): String =
    name.orEmpty().removeSuffix(" - Topic").trim()

internal fun isAlbumUrl(url: String): Boolean =
    url.contains("list=OLAK5uy_") || url.contains("/browse/MPREb") ||
        (MusicService.ofUrl(url) == MusicService.BANDCAMP && url.contains("/album/"))

internal fun InfoItem.toSummary(filter: SearchFilter? = null): MediaSummary? = when (this) {
    is StreamInfoItem -> {
        if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM) {
            null
        } else {
            val service = MusicService.ofUrl(url)
            val isSong = when {
                service != MusicService.YOUTUBE -> true
                filter == SearchFilter.MUSIC_SONGS -> true
                filter == SearchFilter.MUSIC_VIDEOS -> false
                else -> uploaderName.orEmpty().endsWith(" - Topic") || url.contains("music.youtube.com")
            }
            MediaSummary(
                url = url,
                id = itemId(url),
                type = if (isSong) ItemType.SONG else ItemType.VIDEO,
                title = name,
                subtitle = cleanArtist(uploaderName),
                thumbnailUrl = thumbnails.pickUrl(THUMB_LIST),
                durationSec = duration,
                artistUrl = uploaderUrl?.takeIf { it.isNotBlank() },
            )
        }
    }

    is PlaylistInfoItem -> {
        val album = filter == SearchFilter.MUSIC_ALBUMS || isAlbumUrl(url)
        MediaSummary(
            url = url,
            id = if (MusicService.ofUrl(url) == MusicService.YOUTUBE) {
                url.substringAfter("list=", url).substringBefore('&')
            } else {
                url
            },
            type = if (album) ItemType.ALBUM else ItemType.PLAYLIST,
            title = cleanCollectionTitle(name),
            subtitle = cleanArtist(uploaderName),
            thumbnailUrl = thumbnails.pickUrl(THUMB_LIST),
            itemCount = streamCount,
            artistUrl = uploaderUrl?.takeIf { it.isNotBlank() },
        )
    }

    // Los artistas solo se muestran al buscarlos explícitamente
    is ChannelInfoItem -> if (filter == SearchFilter.MUSIC_ARTISTS) {
        MediaSummary(
            url = url,
            id = url,
            type = ItemType.CHANNEL,
            title = cleanArtist(name),
            subtitle = subscriberLabel(subscriberCount),
            thumbnailUrl = thumbnails.pickUrl(THUMB_LIST),
            artistUrl = url,
        )
    } else {
        null
    }

    else -> null // otros tipos no se muestran
}

/** "1,2 M suscriptores" (vacío si el servicio no informa la cifra). */
internal fun subscriberLabel(count: Long): String = when {
    count < 0 -> ""
    count >= 1_000_000 -> "%.1f M suscriptores".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1f K suscriptores".format(count / 1_000.0)
    else -> "$count suscriptores"
}

/** Ancho de las miniaturas de lista; Coil las reduce al tamaño real de la vista. */
internal const val THUMB_LIST = 544

/** Identificador interno de una pista según su servicio (ver [MusicService]). */
fun itemId(url: String): String {
    val service = MusicService.ofUrl(url)
    val youtubeId = if (service == MusicService.YOUTUBE) videoIdFromUrl(url) else null
    return idOf(service, url, youtubeId)
}

fun videoIdFromUrl(url: String): String? =
    runCatching { ServiceList.YouTube.streamLHFactory.getId(url) }.getOrNull()

/**
 * Deja solo contenido musical: canciones (canal "Topic" / YouTube Music) y, si hay pocas,
 * videos de duración normal de canción. Evita las recomendaciones de videos sin relación.
 */
fun List<MediaSummary>.musicOnly(): List<MediaSummary> {
    val songs = filter { it.type == ItemType.SONG }
    if (songs.size >= 3) return songs
    val videos = filter { it.type == ItemType.VIDEO && it.durationSec in 60..540 }
    return songs + videos
}

/** YouTube Music antepone el tipo al título del álbum ("Album – X", "EP – X", "Single – X"). */
private val COLLECTION_PREFIX = Regex("^(Album|Álbum|EP|Single|Sencillo)\\s+[–-]\\s+", RegexOption.IGNORE_CASE)

fun cleanCollectionTitle(title: String): String = title.replace(COLLECTION_PREFIX, "").trim()
