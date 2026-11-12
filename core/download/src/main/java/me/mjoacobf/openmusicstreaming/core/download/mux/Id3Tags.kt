package me.mjoacobf.openmusicstreaming.core.download.mux

import java.io.ByteArrayOutputStream

/**
 * Cabecera ID3v2.3 mínima para archivos MP3 (SoundCloud y Bandcamp entregan MP3 sin recodificar):
 * título, artista, álbum, artista del álbum, pista, año, comentario (URL) y carátula.
 */
object Id3Tags {

    data class Metadata(
        val title: String,
        val artist: String,
        val album: String? = null,
        val albumArtist: String? = null,
        val trackNumber: Int? = null,
        val year: Int? = null,
        val comment: String? = null,
        val coverJpeg: ByteArray? = null,
    )

    /** Devuelve cuántos bytes ocupa una etiqueta ID3v2 al inicio de [head] (0 si no hay). */
    fun existingTagSize(head: ByteArray): Int {
        if (head.size < 10 || head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) {
            return 0
        }
        val size = (head[6].toInt() and 0x7F shl 21) or (head[7].toInt() and 0x7F shl 14) or
            (head[8].toInt() and 0x7F shl 7) or (head[9].toInt() and 0x7F)
        return 10 + size
    }

    fun build(meta: Metadata): ByteArray {
        val frames = ByteArrayOutputStream()
        textFrame(frames, "TIT2", meta.title)
        textFrame(frames, "TPE1", meta.artist)
        meta.album?.takeIf { it.isNotBlank() }?.let { textFrame(frames, "TALB", it) }
        meta.albumArtist?.takeIf { it.isNotBlank() }?.let { textFrame(frames, "TPE2", it) }
        meta.trackNumber?.let { textFrame(frames, "TRCK", it.toString()) }
        meta.year?.let { textFrame(frames, "TYER", it.toString()) }
        meta.comment?.takeIf { it.isNotBlank() }?.let { commentFrame(frames, it) }
        meta.coverJpeg?.let { pictureFrame(frames, it) }

        val body = frames.toByteArray()
        val out = ByteArrayOutputStream(body.size + 10)
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        out.write(syncSafe(body.size))
        out.write(body)
        return out.toByteArray()
    }

    private fun syncSafe(value: Int) = byteArrayOf(
        (value shr 21 and 0x7F).toByte(), (value shr 14 and 0x7F).toByte(),
        (value shr 7 and 0x7F).toByte(), (value and 0x7F).toByte(),
    )

    /** Texto UTF-16 con BOM y terminador (codificación 1 de ID3v2.3). */
    private fun utf16(text: String): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)

    private fun frame(out: ByteArrayOutputStream, id: String, data: ByteArray) {
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(
            byteArrayOf(
                (data.size shr 24).toByte(), (data.size shr 16).toByte(),
                (data.size shr 8).toByte(), data.size.toByte(),
            ),
        )
        out.write(byteArrayOf(0, 0))
        out.write(data)
    }

    private fun textFrame(out: ByteArrayOutputStream, id: String, text: String) {
        frame(out, id, byteArrayOf(1) + utf16(text) + byteArrayOf(0, 0))
    }

    private fun commentFrame(out: ByteArrayOutputStream, text: String) {
        val data = byteArrayOf(1) + "eng".toByteArray(Charsets.US_ASCII) +
            utf16("") + byteArrayOf(0, 0) + utf16(text) + byteArrayOf(0, 0)
        frame(out, "COMM", data)
    }

    private fun pictureFrame(out: ByteArrayOutputStream, jpeg: ByteArray) {
        val data = byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 3, 0) + jpeg
        frame(out, "APIC", data)
    }
}
