package me.mjoacobf.openmusicstreaming.core.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.SongRecord
import me.mjoacobf.openmusicstreaming.core.extractor.itemId
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** Resultado de analizar la carpeta de descargas. */
data class ScanResult(
    /** Archivos de audio encontrados. */
    val audioFiles: Int,
    /** Canciones recuperadas y añadidas a la biblioteca. */
    val imported: Int,
    /** Ya estaban en la biblioteca (no se tocan). */
    val alreadyKnown: Int,
    /** Audio sin el enlace de origen que guarda la app (música ajena): se ignora. */
    val skipped: Int,
    val failed: Int,
)

/**
 * Reconstruye la biblioteca a partir de los archivos ya descargados en la carpeta del usuario
 * (por ejemplo tras desinstalar y reinstalar la app, que borra la base de datos pero no los audios).
 *
 * Cada archivo descargado por la app guarda en su etiqueta de comentario la URL de origen
 * (`COMMENT` en Opus, `©cmt` en M4A, `COMM` en MP3), de la que se recupera el identificador
 * interno exacto de la canción. Así la canción vuelve a ser la misma de antes (sin duplicados con
 * lo que se vuelva a descargar). El audio sin ese dato no es de la app y se ignora.
 * Las playlists no se recuperan (la app no guarda su enlace en los archivos): las canciones
 * quedan en la pestaña «Canciones».
 */
class LibraryScanner(
    private val context: Context,
    private val library: LibraryRepository,
    private val storage: SafStorage,
) {
    private class Entry(val uri: Uri, val name: String, val size: Long)

    suspend fun scan(treeUri: String, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ScanResult =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(treeUri)
            val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
                ?: error("Carpeta no válida")
            val files = ArrayList<Entry>()
            list(tree, root, 0, files)

            var imported = 0
            var known = 0
            var skipped = 0
            var failed = 0
            files.forEachIndexed { index, file ->
                ensureActive()
                onProgress(index, files.size)
                try {
                    when (importFile(file)) {
                        Outcome.IMPORTED -> imported++
                        Outcome.KNOWN -> known++
                        Outcome.SKIPPED -> skipped++
                    }
                } catch (e: Exception) {
                    failed++
                }
            }
            onProgress(files.size, files.size)
            ScanResult(files.size, imported, known, skipped, failed)
        }

    private enum class Outcome { IMPORTED, KNOWN, SKIPPED }

    private suspend fun importFile(file: Entry): Outcome {
        val url = findSourceUrl(file) ?: return Outcome.SKIPPED
        val id = runCatching { itemId(url) }.getOrNull() ?: return Outcome.SKIPPED
        // Un id de YouTube válido tiene 11 caracteres; otra cosa indica una URL que no es de una pista
        if (MusicService.ofId(id) == MusicService.YOUTUBE && id.length != YOUTUBE_ID_LENGTH) return Outcome.SKIPPED

        val existing = library.getSong(id)
        if (existing?.localUri != null && storage.exists(existing.localUri!!)) return Outcome.KNOWN

        val meta = readMetadata(file.uri)
        val baseName = file.name.substringBeforeLast('.')
        library.upsertSong(
            SongRecord(
                videoId = id,
                title = meta.title?.takeIf { it.isNotBlank() } ?: baseName,
                artist = meta.artist.orEmpty(),
                album = meta.album.orEmpty(),
                durationSec = meta.durationMs / 1000,
                thumbnailUrl = meta.cover?.let { saveCover(id, it) },
                localUri = file.uri.toString(),
                fileName = file.name,
                fileSizeBytes = file.size,
            ),
        )
        return Outcome.IMPORTED
    }

    private class Meta(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
        val cover: ByteArray?,
    )

    private fun readMetadata(uri: Uri): Meta {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return Meta(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                cover = runCatching { retriever.embeddedPicture }.getOrNull(),
            )
        } catch (e: Exception) {
            return Meta(null, null, null, 0, null)
        } finally {
            retriever.release()
        }
    }

    /** Guarda la carátula incrustada en el almacenamiento interno (como al descargar). */
    private fun saveCover(id: String, bytes: ByteArray): String? = runCatching {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "song_${fileKey(id)}.jpg")
        file.writeBytes(bytes)
        Uri.fromFile(file).toString()
    }.getOrNull()

    // region Búsqueda de la URL de origen en las etiquetas

    private fun findSourceUrl(file: Entry): String? {
        val head = readHead(file.uri) ?: return null
        TagUrl.fromOgg(head)?.let { return it }
        TagUrl.fromId3(head)?.let { return it }
        TagUrl.fromMp4(head)?.let { return it }
        // En M4A las etiquetas pueden estar al final del archivo (la caja `moov`)
        if (file.size > head.size) readTail(file.uri, file.size)?.let { tail -> TagUrl.fromMp4(tail)?.let { return it } }
        return null
    }

    private fun readHead(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readUpTo(SCAN_BYTES) }
    }.getOrNull()

    private fun readTail(uri: Uri, size: Long): ByteArray? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { input ->
                val start = (size - SCAN_BYTES).coerceAtLeast(0)
                input.channel.position(start)
                input.readUpTo(SCAN_BYTES)
            }
        }
    }.getOrNull()

    private fun InputStream.readUpTo(max: Int): ByteArray {
        val buffer = ByteArray(max)
        var total = 0
        while (total < max) {
            val n = read(buffer, total, max - total)
            if (n <= 0) break
            total += n
        }
        return buffer.copyOf(total)
    }

    // endregion

    /** Recorre la carpeta (y subcarpetas) con consultas directas al proveedor: es mucho más rápido que DocumentFile. */
    private fun list(tree: Uri, parentId: String, depth: Int, out: MutableList<Entry>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val folders = ArrayList<String>()
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < MAX_DEPTH) folders += docId
                } else if (name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) {
                    out += Entry(DocumentsContract.buildDocumentUriUsingTree(tree, docId), name, cursor.getLong(3))
                }
            }
        }
        folders.forEach { list(tree, it, depth + 1, out) }
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("opus", "ogg", "m4a", "mp3")
        const val MAX_DEPTH = 5
        const val YOUTUBE_ID_LENGTH = 11

        /** Las etiquetas están al principio del archivo; la carátula incrustada puede ocupar ~56 KB. */
        const val SCAN_BYTES = 256 * 1024
    }
}

/** Extrae la URL de origen del comentario de cada formato (ver [LibraryScanner]). */
internal object TagUrl {

    /** Vorbis comment en Ogg: `[longitud LE32]COMMENT=<url>`. */
    fun fromOgg(buf: ByteArray): String? {
        val key = "COMMENT=".toByteArray(Charsets.US_ASCII)
        var from = 0
        while (true) {
            val i = indexOf(buf, key, from, ignoreCase = true)
            if (i < 0) return null
            from = i + 1
            if (i < 4) continue
            val len = (buf[i - 4].toInt() and 0xFF) or ((buf[i - 3].toInt() and 0xFF) shl 8) or
                ((buf[i - 2].toInt() and 0xFF) shl 16) or ((buf[i - 1].toInt() and 0xFF) shl 24)
            if (len < key.size || i + len > buf.size) continue
            asUrl(String(buf, i + key.size, len - key.size, Charsets.UTF_8))?.let { return it }
        }
    }

    /** Caja iTunes `©cmt` → `data`: [tamaño][©cmt][tamaño]['data'][tipo][locale][texto]. */
    fun fromMp4(buf: ByteArray): String? {
        val key = byteArrayOf(0xA9.toByte(), 'c'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte())
        var from = 0
        while (true) {
            val p = indexOf(buf, key, from, ignoreCase = false)
            if (p < 0) return null
            from = p + 1
            if (p + 20 > buf.size) continue
            val size = readInt(buf, p + 4)
            if (size < 17 || p + 4 + size > buf.size) continue
            if (String(buf, p + 8, 4, Charsets.US_ASCII) != "data") continue
            asUrl(String(buf, p + 20, size - 16, Charsets.UTF_8))?.let { return it }
        }
    }

    /** Trama ID3v2.3 `COMM` escrita por la app: codificación 1, "eng", descripción vacía y texto UTF-16LE con BOM. */
    fun fromId3(buf: ByteArray): String? {
        val key = "COMM".toByteArray(Charsets.US_ASCII)
        var from = 0
        while (true) {
            val i = indexOf(buf, key, from, ignoreCase = false)
            if (i < 0) return null
            from = i + 1
            if (i + 10 > buf.size) continue
            val size = readInt(buf, i + 4)
            val start = i + 10
            if (size < 14 || start + size > buf.size || buf[start].toInt() != 1) continue
            // [enc][lang 3][BOM 2][00 00][BOM 2][texto][00 00]
            val textStart = start + 1 + 3 + 2 + 2 + 2
            val textEnd = start + size - 2
            if (textEnd <= textStart) continue
            asUrl(String(buf, textStart, textEnd - textStart, Charsets.UTF_16LE))?.let { return it }
        }
    }

    private fun asUrl(text: String): String? =
        text.trim().trimEnd('\u0000').takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun readInt(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun indexOf(buf: ByteArray, pattern: ByteArray, from: Int, ignoreCase: Boolean): Int {
        outer@ for (i in from..buf.size - pattern.size) {
            for (j in pattern.indices) {
                var a = buf[i + j].toInt()
                var b = pattern[j].toInt()
                if (ignoreCase) {
                    if (a in 'a'.code..'z'.code) a -= 32
                    if (b in 'a'.code..'z'.code) b -= 32
                }
                if (a != b) continue@outer
            }
            return i
        }
        return -1
    }
}
