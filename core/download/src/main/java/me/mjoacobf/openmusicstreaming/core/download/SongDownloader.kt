package me.mjoacobf.openmusicstreaming.core.download

import android.content.Context
import android.util.Pair
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.data.SongRecord
import me.mjoacobf.openmusicstreaming.core.download.mux.FileInputSharpStream
import me.mjoacobf.openmusicstreaming.core.download.mux.FileOutputSharpStream
import me.mjoacobf.openmusicstreaming.core.download.mux.Id3Tags
import me.mjoacobf.openmusicstreaming.core.download.mux.Mp4FromDashWriter
import me.mjoacobf.openmusicstreaming.core.download.mux.Mp4Tags
import me.mjoacobf.openmusicstreaming.core.download.mux.OggFromWebMWriter
import me.mjoacobf.openmusicstreaming.core.download.mux.OutputSharpStream
import me.mjoacobf.openmusicstreaming.core.extractor.StreamResolver
import me.mjoacobf.openmusicstreaming.core.extractor.YoutubeDataSource
import me.mjoacobf.openmusicstreaming.core.extractor.cleanArtist
import me.mjoacobf.openmusicstreaming.core.model.AudioQuality
import me.mjoacobf.openmusicstreaming.core.model.DownloadFormat
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileOutputStream

/** Parámetros de etiquetado de una pista dentro de una playlist/álbum. */
data class TrackContext(
    val album: String,
    val albumArtist: String,
    val trackNumber: Int?,
    val isAlbum: Boolean,
)

/**
 * Descarga una canción tal como lo hace NewPipe: toma el stream de audio de YouTube,
 * lo baja por bloques y lo remuxea sin recodificar (sin pérdida de calidad) al formato elegido:
 * Ogg/Opus (menor tamaño) o M4A/AAC (máxima compatibilidad), con etiquetas y carátula.
 * SoundCloud y Bandcamp entregan MP3 progresivo: se guarda tal cual, con etiquetas ID3 y carátula.
 */
@OptIn(UnstableApi::class)
class SongDownloader(
    private val context: Context,
    private val client: OkHttpClient,
    private val resolver: StreamResolver,
    private val storage: SafStorage,
) {
    private val dataSourceFactory = YoutubeDataSource.Factory(client, resolver)
    private val coverArt = CoverArt(client)

    suspend fun download(
        videoId: String,
        track: TrackContext,
        folder: DocumentFile,
        existingNames: MutableSet<String>,
        quality: AudioQuality,
        format: DownloadFormat,
        embedCover: Boolean,
        onProgress: (Float) -> Unit = {},
    ): SongRecord = withContext(Dispatchers.IO) {
        val info = resolver.streamInfo(videoId)
        if (MusicService.ofId(videoId) != MusicService.YOUTUBE) {
            return@withContext downloadDirect(info, videoId, track, folder, existingNames, quality, embedCover, onProgress)
        }
        val (title, artist) = TrackNames.from(info.name, info.uploaderName)
        val wantM4a = format == DownloadFormat.M4A
        val stream = (if (wantM4a) StreamResolver.selectM4a(info, quality) else null)
            ?: StreamResolver.selectAudio(info, quality)
        val isOpus = stream.format == MediaFormat.WEBMA_OPUS
        val extension = if (isOpus) "opus" else "m4a"

        val key = fileKey(videoId)
        val temp = File(context.cacheDir, "dl_$key.tmp")
        val muxed = File(context.cacheDir, "dl_$key.m4a")
        try {
            // 1) Descarga al almacenamiento interno temporal (por bloques, con reintento si caduca)
            fetchToFile(StreamResolver.audioUri(videoId, quality, m4a = !isOpus), temp, onProgress)
            ensureActive()

            val year = runCatching { info.uploadDate?.offsetDateTime()?.year }.getOrNull()
            // La carátula se guarda también en local para mostrarla sin conexión
            val covers = Covers.from(coverArt, info.thumbnails)
            val localCover = covers?.saved?.let { saveCover("song_$key", it) }
            val coverJpeg = if (embedCover) covers?.embedded else null

            // 2) M4A: el archivo de YouTube es MP4 fragmentado (DASH); se convierte a M4A normal
            //    con el muxer de NewPipe y se añaden las etiquetas iTunes.
            if (!isOpus) {
                Mp4FromDashWriter(FileInputSharpStream(temp)).apply {
                    setMainBrand(0x4D344120) // "M4A "
                    setUserData(
                        Mp4Tags.build(
                            Mp4Tags.Metadata(
                                title = title,
                                artist = artist,
                                album = track.album,
                                albumArtist = albumArtist(track, artist),
                                trackNumber = track.trackNumber,
                                year = year,
                                comment = info.url,
                                coverJpeg = coverJpeg,
                            ),
                        ),
                    )
                    parseSources()
                    selectTracks(0)
                    val out = FileOutputSharpStream(muxed)
                    try {
                        build(out)
                    } finally {
                        out.close()
                        close()
                    }
                }
            }
            ensureActive()

            // 3) Archivo de destino en la carpeta elegida por el usuario
            val baseName = sanitizeFileName(if (artist.isNotBlank()) "$artist - $title" else title)
            var fileName = "$baseName.$extension"
            if (fileName in existingNames) fileName = "$baseName [${key.takeLast(8)}].$extension"
            existingNames += fileName
            val document = storage.createFile(folder, fileName)

            try {
                val output = context.contentResolver.openOutputStream(document.uri, "w")
                    ?: error("No se pudo escribir $fileName")
                if (isOpus) {
                    val tags = buildTags(
                        title = title,
                        artist = artist,
                        track = track,
                        year = year,
                        url = info.url,
                        cover = coverJpeg?.let { coverArt.pictureBlockBase64(it, COVER_SIZE) },
                    )
                    OggFromWebMWriter(FileInputSharpStream(temp), OutputSharpStream(output), tags).use { writer ->
                        writer.parseSource()
                        writer.selectTrack(0)
                        writer.build()
                    }
                } else {
                    output.use { out -> muxed.inputStream().use { it.copyTo(out) } }
                }
            } catch (e: Throwable) {
                storage.delete(document.uri)
                existingNames -= fileName
                throw e
            }

            SongRecord(
                videoId = videoId,
                title = title,
                artist = artist,
                album = track.album,
                durationSec = info.duration,
                thumbnailUrl = localCover ?: info.thumbnails.minByOrNull { kotlin.math.abs(it.width - 320) }?.url,
                localUri = document.uri.toString(),
                fileName = fileName,
                fileSizeBytes = runCatching { DocumentFile.fromSingleUri(context, document.uri)?.length() ?: 0 }
                    .getOrDefault(0),
            )
        } finally {
            temp.delete()
            muxed.delete()
        }
    }

    /** Baja el stream (por bloques en YouTube, directo en el resto) al archivo temporal. */
    private suspend fun fetchToFile(uri: android.net.Uri, temp: File, onProgress: (Float) -> Unit) {
        val source = dataSourceFactory.createDataSource()
        try {
            val length = source.open(DataSpec(uri))
            FileOutputStream(temp).use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    out.write(buffer, 0, read)
                    total += read
                    if (length > 0) onProgress(total.toFloat() / length)
                }
            }
        } finally {
            source.close()
        }
    }

    /**
     * Descarga una lista HLS concatenando sus segmentos. Sirve para MP3 y AAC (ADTS), que son
     * reproducibles tal cual; si hay `EXT-X-MAP` (fMP4/AAC) se antepone el inicio y luego se remuxea a M4A.
     */
    private suspend fun fetchHls(playlistUrl: String, temp: File, onProgress: (Float) -> Unit) {
        fun get(url: String) = client.newCall(okhttp3.Request.Builder().url(url).build()).execute()
        val base = playlistUrl.toHttpUrl()
        val text = get(playlistUrl).use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body.string()
        }
        // fMP4 (SoundCloud AAC): primero el segmento de inicialización, luego los fragmentos
        val init = Regex("#EXT-X-MAP:URI=\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        val segments = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { base.resolve(it)?.toString() ?: it }.toList()
        if (segments.isEmpty()) error("Lista HLS vacía")
        FileOutputStream(temp).use { out ->
            if (init != null) {
                get(init).use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    r.body.byteStream().copyTo(out)
                }
            }
            segments.forEachIndexed { index, url ->
                currentCoroutineContext().ensureActive()
                get(url).use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    r.body.byteStream().copyTo(out)
                }
                onProgress((index + 1f) / segments.size)
            }
        }
    }

    /** SoundCloud / Bandcamp: archivo progresivo (normalmente MP3) guardado sin recodificar. */
    private suspend fun downloadDirect(
        info: StreamInfo,
        id: String,
        track: TrackContext,
        folder: DocumentFile,
        existingNames: MutableSet<String>,
        quality: AudioQuality,
        embedCover: Boolean,
        onProgress: (Float) -> Unit,
    ): SongRecord {
        val (title, artist) = TrackNames.from(info.name, info.uploaderName)
        val progressive = StreamResolver.hasProgressiveAudio(info)
        val stream = if (progressive) {
            StreamResolver.selectAudio(info, quality)
        } else {
            StreamResolver.selectHls(info, quality) ?: error("No hay audio descargable")
        }
        val extension = when {
            stream.format == MediaFormat.MP3 -> "mp3"
            !progressive -> "m4a" // HLS de SoundCloud: AAC en fMP4 → M4A
            else -> stream.format?.suffix?.takeIf { it.isNotBlank() } ?: "mp3"
        }
        val key = fileKey(id)
        val temp = File(context.cacheDir, "dl_$key.tmp")
        try {
            if (progressive) {
                fetchToFile(StreamResolver.audioUri(id, quality), temp, onProgress)
            } else {
                fetchHls(stream.content, temp, onProgress)
            }
            currentCoroutineContext().ensureActive()

            val year = runCatching { info.uploadDate?.offsetDateTime()?.year }.getOrNull()
            val covers = Covers.from(coverArt, info.thumbnails)
            val localCover = covers?.saved?.let { saveCover("song_$key", it) }

            val baseName = sanitizeFileName(if (artist.isNotBlank()) "$artist - $title" else title)
            var fileName = "$baseName.$extension"
            if (fileName in existingNames) fileName = "$baseName [${key.takeLast(8)}].$extension"
            existingNames += fileName
            val document = storage.createFile(folder, fileName)
            try {
                val output = context.contentResolver.openOutputStream(document.uri, "w")
                    ?: error("No se pudo escribir $fileName")
                output.use { out ->
                    var skip = 0L
                    if (extension == "mp3") {
                        out.write(
                            Id3Tags.build(
                                Id3Tags.Metadata(
                                    title = title,
                                    artist = artist,
                                    album = track.album,
                                    albumArtist = albumArtist(track, artist),
                                    trackNumber = track.trackNumber,
                                    year = year,
                                    comment = info.url,
                                    coverJpeg = if (embedCover) covers?.embedded else null,
                                ),
                            ),
                        )
                        // Si el MP3 original ya traía una etiqueta ID3, se descarta para no duplicarla
                        skip = temp.inputStream().use { input ->
                            val head = ByteArray(10)
                            val n = input.read(head)
                            if (n == 10) Id3Tags.existingTagSize(head).toLong() else 0L
                        }
                    }
                    val source = if (extension == "m4a" && !progressive) {
                        remuxToM4a(temp, File(context.cacheDir, "dl_$key.m4a"), title, artist, track, year, info.url,
                            if (embedCover) covers?.embedded else null)
                    } else {
                        temp
                    }
                    source.inputStream().use { input ->
                        input.skip(skip)
                        input.copyTo(out)
                    }
                    if (source !== temp) source.delete()
                }
            } catch (e: Throwable) {
                storage.delete(document.uri)
                existingNames -= fileName
                throw e
            }

            return SongRecord(
                videoId = id,
                title = title,
                artist = artist,
                album = track.album,
                durationSec = info.duration,
                thumbnailUrl = localCover ?: info.thumbnails.maxByOrNull { it.width }?.url,
                localUri = document.uri.toString(),
                fileName = fileName,
                fileSizeBytes = runCatching { DocumentFile.fromSingleUri(context, document.uri)?.length() ?: 0 }
                    .getOrDefault(0),
            )
        } finally {
            temp.delete()
        }
    }

    /**
     * fMP4 (HLS) → M4A normal con etiquetas iTunes y carátula, con el muxer de NewPipe.
     * Si el muxer no puede con el archivo, se devuelve el original (también reproducible).
     */
    private fun remuxToM4a(
        input: File,
        output: File,
        title: String,
        artist: String,
        track: TrackContext,
        year: Int?,
        url: String,
        cover: ByteArray?,
    ): File = try {
        Mp4FromDashWriter(FileInputSharpStream(input)).apply {
            setMainBrand(0x4D344120) // "M4A "
            setUserData(
                Mp4Tags.build(
                    Mp4Tags.Metadata(title, artist, track.album, albumArtist(track, artist), track.trackNumber, year, url, cover),
                ),
            )
            parseSources()
            selectTracks(0)
            val out = FileOutputSharpStream(output)
            try {
                build(out)
            } finally {
                out.close()
                close()
            }
        }
        output
    } catch (e: Exception) {
        android.util.Log.w("SongDownloader", "Remux M4A falló; se guarda el fMP4 tal cual", e)
        output.delete()
        input
    }

    /** Carátula en dos versiones: una nítida para mostrar y otra más ligera para incrustar. */
    private class Covers(val saved: ByteArray, val embedded: ByteArray) {
        companion object {
            fun from(art: CoverArt, images: List<org.schabi.newpipe.extractor.Image>): Covers? {
                val url = art.pickThumbnail(images) ?: return null
                val bytes = art.download(url) ?: return null
                val saved = art.squareJpeg(bytes, SAVED_COVER_SIZE, 85) ?: return null
                val embedded = art.squareJpegFitting(bytes, COVER_SIZE, MAX_EMBEDDED_BYTES) ?: saved
                return Covers(saved, embedded)
            }
        }
    }

    /** Guarda una carátula en el almacenamiento interno y devuelve su URI file://. */
    private fun saveCover(name: String, jpeg: ByteArray): String? = runCatching {
        val dir = File(context.filesDir, COVERS_DIR).apply { mkdirs() }
        val file = File(dir, "$name.jpg")
        file.writeBytes(jpeg)
        android.net.Uri.fromFile(file).toString()
    }.getOrNull()

    /** Descarga y guarda la portada de una playlist/álbum para verla sin conexión. */
    suspend fun cachePlaylistCover(id: String, url: String?): String? = withContext(Dispatchers.IO) {
        val bytes = url?.let(coverArt::download) ?: return@withContext null
        val jpeg = coverArt.squareJpeg(bytes, SAVED_COVER_SIZE, 85) ?: return@withContext null
        saveCover("playlist_" + sanitizeFileName(id), jpeg)
    }

    private fun albumArtist(track: TrackContext, artist: String) =
        if (track.isAlbum) track.albumArtist else track.albumArtist.ifBlank { artist }

    private fun buildTags(
        title: String,
        artist: String,
        track: TrackContext,
        year: Int?,
        url: String,
        cover: String?,
    ): List<Pair<String, String>> = buildList {
        add(Pair.create("TITLE", title))
        add(Pair.create("ARTIST", artist))
        if (track.album.isNotBlank()) add(Pair.create("ALBUM", track.album))
        add(Pair.create("ALBUMARTIST", albumArtist(track, artist)))
        track.trackNumber?.let { add(Pair.create("TRACKNUMBER", it.toString())) }
        year?.let { add(Pair.create("DATE", it.toString())) }
        add(Pair.create("COMMENT", url))
        cover?.let { add(Pair.create("METADATA_BLOCK_PICTURE", it)) }
    }

    private companion object {
        /** Carátula guardada para la app (nítida en el reproductor a pantalla completa). */
        const val SAVED_COVER_SIZE = 600

        /** Carátula incrustada: 544 px y como máximo 42 000 bytes (cabe en OpusTags, < 65 025 tras base64). */
        const val COVER_SIZE = 544
        const val MAX_EMBEDDED_BYTES = 42_000
        const val COVERS_DIR = "covers"
    }
}

/** Heurísticas para obtener título y artista a partir de los datos de YouTube. */
object TrackNames {
    private val noise = Regex(
        "\\s*[(\\[](official|oficial|lyric|letra|audio|video|videoclip|music video|visualizer|hd|4k)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )

    fun from(rawTitle: String, uploader: String?): kotlin.Pair<String, String> {
        val topic = uploader.orEmpty().endsWith(" - Topic")
        val channel = cleanArtist(uploader)
        if (topic) return rawTitle.trim() to channel
        val cleaned = rawTitle.replace(noise, "").trim()
        val dash = cleaned.indexOf(" - ")
        return if (dash > 0) {
            cleaned.substring(dash + 3).trim() to cleaned.substring(0, dash).trim()
        } else {
            cleaned to channel
        }
    }
}

/** Clave corta y segura para nombres de archivo a partir de un id interno (que puede ser una URL). */
internal fun fileKey(id: String): String {
    val clean = id.filter { it.isLetterOrDigit() }.takeLast(24)
    return if (id.length <= 16 && id == clean) id else clean + Integer.toHexString(id.hashCode())
}
