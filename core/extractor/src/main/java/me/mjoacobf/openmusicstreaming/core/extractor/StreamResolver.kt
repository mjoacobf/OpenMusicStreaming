package me.mjoacobf.openmusicstreaming.core.extractor

import android.net.Uri
import android.util.LruCache
import me.mjoacobf.openmusicstreaming.core.model.AudioQuality
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import me.mjoacobf.openmusicstreaming.core.model.streamUrlOf
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

/**
 * URL real de un stream y su tamaño (si se conoce). [chunked] es true solo para googlevideo
 * (YouTube), que exige pedirlo por bloques; SoundCloud y Bandcamp sirven un archivo normal.
 */
data class ResolvedStream(val url: String, val contentLength: Long, val chunked: Boolean = true)

/**
 * Obtiene y cachea [StreamInfo] (las URLs de YouTube caducan a las ~6 h) y elige
 * el stream de audio/video adecuado según la calidad solicitada.
 *
 * Las URIs internas tienen la forma (el id va codificado: en SoundCloud/Bandcamp es una URL):
 *  - `ytstream://audio/<id>?q=HIGH`
 *  - `ytstream://video/<id>?h=480`  (solo video, se combina con el audio; solo YouTube)
 *  - `ytstream://muxed/<id>?h=480`  (audio+video en un archivo, respaldo)
 */
class StreamResolver {

    private class Entry(val info: StreamInfo, val fetchedAt: Long)

    private val cache = LruCache<String, Entry>(48)
    private val locks = HashMap<String, Any>()

    /** Bloqueante: llamar solo desde hilos de E/S. */
    fun streamInfo(videoId: String, forceRefresh: Boolean = false): StreamInfo {
        val lock = synchronized(locks) { locks.getOrPut(videoId) { Any() } }
        synchronized(lock) {
            val cached = cache.get(videoId)
            if (!forceRefresh && cached != null &&
                System.currentTimeMillis() - cached.fetchedAt < MAX_AGE_MS
            ) {
                return cached.info
            }
            val service = MusicService.ofId(videoId).newPipe
            val info = StreamInfo.getInfo(service, streamUrlOf(videoId))
            cache.put(videoId, Entry(info, System.currentTimeMillis()))
            return info
        }
    }

    fun cached(videoId: String): StreamInfo? = cache.get(videoId)?.info

    /** Olvida todas las URLs resueltas (Limpiar caché). */
    fun clear() = cache.evictAll()

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    fun resolve(uri: Uri, forceRefresh: Boolean): ResolvedStream {
        val kind = uri.host ?: error("Invalid stream uri $uri")
        val videoId = uri.lastPathSegment ?: error("Invalid stream uri $uri")
        val info = streamInfo(videoId, forceRefresh)
        val chunked = MusicService.ofId(videoId) == MusicService.YOUTUBE
        return when (kind) {
            KIND_AUDIO -> {
                val quality = uri.getQueryParameter("q")
                    ?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
                    ?: AudioQuality.HIGH
                val stream = if (uri.getQueryParameter("f") == FORMAT_M4A) {
                    selectM4a(info, quality) ?: selectAudio(info, quality)
                } else {
                    selectAudio(info, quality)
                }
                ResolvedStream(stream.content, stream.itagItem?.contentLength ?: -1, chunked)
            }

            KIND_VIDEO -> {
                val height = uri.getQueryParameter("h")?.toIntOrNull() ?: 480
                val stream = selectVideoOnly(info, height)
                    ?: error("No video-only stream available")
                ResolvedStream(stream.content, stream.itagItem?.contentLength ?: -1, chunked)
            }

            KIND_MUXED -> {
                val height = uri.getQueryParameter("h")?.toIntOrNull() ?: 480
                val stream = selectMuxed(info, height) ?: error("No muxed stream available")
                ResolvedStream(stream.content, stream.itagItem?.contentLength ?: -1, chunked)
            }

            else -> error("Unknown stream kind $kind")
        }
    }

    companion object {
        const val SCHEME = "ytstream"
        const val KIND_AUDIO = "audio"
        const val KIND_VIDEO = "video"
        const val KIND_MUXED = "muxed"
        private const val MAX_AGE_MS = 4L * 60 * 60 * 1000

        const val FORMAT_M4A = "m4a"

        /** @param m4a fuerza AAC/M4A (para descargas en ese formato). */
        fun audioUri(videoId: String, quality: AudioQuality, m4a: Boolean = false): Uri =
            Uri.parse("$SCHEME://$KIND_AUDIO/${Uri.encode(videoId)}?q=${quality.name}" + if (m4a) "&f=$FORMAT_M4A" else "")

        fun videoUri(videoId: String, height: Int): Uri =
            Uri.parse("$SCHEME://$KIND_VIDEO/${Uri.encode(videoId)}?h=$height")

        fun muxedUri(videoId: String, height: Int): Uri =
            Uri.parse("$SCHEME://$KIND_MUXED/${Uri.encode(videoId)}?h=$height")

        private fun AudioStream.isUsable() =
            isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                (audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL)

        /** Prefiere Opus (mejor calidad por byte); si no hay, M4A/AAC. */
        fun selectAudio(info: StreamInfo, quality: AudioQuality): AudioStream {
            val usable = info.audioStreams.filter { it.isUsable() }
                .ifEmpty { info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } }
            require(usable.isNotEmpty()) { "No audio streams available" }
            val opus = usable.filter { it.format == MediaFormat.WEBMA_OPUS }
            val candidates = opus.ifEmpty { usable }
            return pickByQuality(candidates, quality)
        }

        /** ¿Tiene audio progresivo (HTTP directo)? Si no (SoundCloud actual), hay que usar HLS. */
        fun hasProgressiveAudio(info: StreamInfo) =
            info.audioStreams.any { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }

        /** Audio HLS (SoundCloud): prefiere MP3, cuyos segmentos se pueden concatenar sin recodificar. */
        fun selectHls(info: StreamInfo, quality: AudioQuality): AudioStream? {
            val hls = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.HLS }
            val mp3 = hls.filter { it.format == MediaFormat.MP3 }
            val list = mp3.ifEmpty { hls }
            return if (list.isEmpty()) null else pickByQuality(list, quality)
        }

        /** Stream AAC/M4A (para descargas en ese formato). */
        fun selectM4a(info: StreamInfo, quality: AudioQuality): AudioStream? {
            val m4a = info.audioStreams.filter { it.isUsable() && it.format == MediaFormat.M4A }
            return if (m4a.isEmpty()) null else pickByQuality(m4a, quality)
        }

        /** Para descargas en Opus: solo streams WebM/Opus (se remuxean a .opus con metadatos). */
        fun selectOpusForDownload(info: StreamInfo, quality: AudioQuality): AudioStream? {
            val opus = info.audioStreams.filter { it.isUsable() && it.format == MediaFormat.WEBMA_OPUS }
            return if (opus.isEmpty()) null else pickByQuality(opus, quality)
        }

        private fun pickByQuality(list: List<AudioStream>, quality: AudioQuality): AudioStream {
            val sorted = list.sortedBy { it.averageBitrate }
            return when (quality) {
                AudioQuality.HIGH -> sorted.last()
                AudioQuality.LOW -> sorted.first()
                AudioQuality.MEDIUM -> sorted.minBy { abs(it.averageBitrate - 70) }
            }
        }

        /** Solo video MP4/H.264 (decodificación por hardware en equipos modestos). */
        fun selectVideoOnly(info: StreamInfo, maxHeight: Int): VideoStream? {
            val usable = info.videoOnlyStreams.filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }
            val mp4 = usable.filter { it.format == MediaFormat.MPEG_4 }.ifEmpty { usable }
            return mp4.filter { it.height in 1..maxHeight }.maxByOrNull { it.height }
                ?: mp4.minByOrNull { it.height }
        }

        fun selectMuxed(info: StreamInfo, maxHeight: Int): VideoStream? {
            val usable = info.videoStreams.filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }
            return usable.filter { it.height in 1..maxHeight }.maxByOrNull { it.height }
                ?: usable.minByOrNull { it.height }
        }
    }
}
