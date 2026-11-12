package me.mjoacobf.openmusicstreaming.core.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.Song

/**
 * Identificadores de elementos en la cola:
 *  - `local:<videoId>`  canción descargada (se reproduce sin conexión);
 *  - `yt:<videoId>`     audio por streaming (si existe copia local, se usa esa);
 *  - `ytv:<videoId>`    video por streaming.
 */
object MediaIds {
    const val LOCAL = "local:"
    const val STREAM = "yt:"
    const val VIDEO = "ytv:"
    const val EXTRA_DURATION = "oms.duration"
    const val EXTRA_URL = "oms.url"

    fun videoId(mediaId: String): String = mediaId.substringAfter(':')
    fun isStreaming(mediaId: String) = mediaId.startsWith(STREAM) || mediaId.startsWith(VIDEO)
    fun isVideo(mediaId: String) = mediaId.startsWith(VIDEO)
}

private fun metadata(
    title: String,
    artist: String,
    album: String?,
    artwork: String?,
    durationSec: Long,
    url: String,
): MediaMetadata = MediaMetadata.Builder()
    .setTitle(title)
    .setArtist(artist)
    .setAlbumTitle(album)
    .setArtworkUri(artwork?.let(Uri::parse))
    .setIsPlayable(true)
    .setIsBrowsable(false)
    .setExtras(
        Bundle().apply {
            putLong(MediaIds.EXTRA_DURATION, durationSec)
            putString(MediaIds.EXTRA_URL, url)
        },
    )
    .build()

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(MediaIds.LOCAL + videoId)
    .setUri(localUri)
    .setRequestMetadata(
        MediaItem.RequestMetadata.Builder().setMediaUri(localUri?.let(Uri::parse)).build(),
    )
    .setMediaMetadata(metadata(title, artist, album.ifBlank { null }, thumbnailUrl, durationSec, url))
    .build()

fun MediaSummary.toMediaItem(asVideo: Boolean = false): MediaItem = MediaItem.Builder()
    .setMediaId((if (asVideo) MediaIds.VIDEO else MediaIds.STREAM) + id)
    .setMediaMetadata(metadata(title, subtitle, null, thumbnailUrl, durationSec, url))
    .build()

val MediaItem.videoId: String get() = MediaIds.videoId(mediaId)

/** Datos del elemento de la cola como [MediaSummary] (marcadores, playlists, historial). */
fun MediaItem.toSummary(): MediaSummary? {
    val url = mediaMetadata.extras?.getString(MediaIds.EXTRA_URL) ?: return null
    return MediaSummary(
        url = url,
        id = MediaIds.videoId(mediaId),
        type = if (MediaIds.isVideo(mediaId)) ItemType.VIDEO else ItemType.SONG,
        title = mediaMetadata.title?.toString().orEmpty(),
        subtitle = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        durationSec = mediaMetadata.extras?.getLong(MediaIds.EXTRA_DURATION) ?: -1,
    )
}
