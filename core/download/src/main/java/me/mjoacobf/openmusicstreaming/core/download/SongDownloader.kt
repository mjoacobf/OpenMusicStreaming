package me.mjoacobf.openmusicstreaming.core.download

import android.content.Context
import android.util.Pair
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.data.SongRecord
import me.mjoacobf.openmusicstreaming.core.download.mux.FileInputSharpStream
import me.mjoacobf.openmusicstreaming.core.download.mux.FileOutputSharpStream
import me.mjoacobf.openmusicstreaming.core.download.mux.Mp4FromDashWriter
import me.mjoacobf.openmusicstreaming.core.download.mux.Mp4Tags
import me.mjoacobf.openmusicstreaming.core.download.mux.OggFromWebMWriter
import me.mjoacobf.openmusicstreaming.core.download.mux.OutputSharpStream
import me.mjoacobf.openmusicstreaming.core.extractor.StreamResolver
import me.mjoacobf.openmusicstreaming.core.extractor.YoutubeDataSource
import me.mjoacobf.openmusicstreaming.core.extractor.cleanArtist
import me.mjoacobf.openmusicstreaming.core.model.AudioQuality
import me.mjoacobf.openmusicstreaming.core.model.DownloadFormat
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
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
 */
@OptIn(UnstableApi::class)
class SongDownloader(
    private val context: Context,
    client: OkHttpClient,
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
        val (title, artist) = TrackNames.from(info.name, info.uploaderName)
        val wantM4a = format == DownloadFormat.M4A
        val stream = (if (wantM4a) StreamResolver.selectM4a(info, quality) else null)
            ?: StreamResolver.selectAudio(info, quality)
        val isOpus = stream.format == MediaFormat.WEBMA_OPUS
        val extension = if (isOpus) "opus" else "m4a"

        val temp = File(context.cacheDir, "dl_$videoId.tmp")
        val muxed = File(context.cacheDir, "dl_$videoId.m4a")
        try {
            // 1) Descarga al almacenamiento interno temporal (por bloques, con reintento si caduca)
            val source = dataSourceFactory.createDataSource()
            try {
                val length = source.open(DataSpec(StreamResolver.audioUri(videoId, quality, m4a = !isOpus)))
                FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        ensureActive()
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
            ensureActive()

            val year = runCatching { info.uploadDate?.offsetDateTime()?.year }.getOrNull()
            // La carátula se guarda también en local para mostrarla sin conexión
            val cover = coverJpeg(info.thumbnails)
            val localCover = cover?.let { saveCover("song_$videoId", it) }
            val coverJpeg = if (embedCover) cover else null

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
            if (fileName in existingNames) fileName = "$baseName [$videoId].$extension"
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
        val jpeg = coverArt.squareJpeg(bytes, 480) ?: return@withContext null
        saveCover("playlist_" + sanitizeFileName(id), jpeg)
    }

    private fun coverJpeg(images: List<org.schabi.newpipe.extractor.Image>): ByteArray? {
        val url = coverArt.pickThumbnail(images) ?: return null
        val bytes = coverArt.download(url) ?: return null
        return coverArt.squareJpeg(bytes, COVER_SIZE)
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
        const val COVER_SIZE = 360
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
