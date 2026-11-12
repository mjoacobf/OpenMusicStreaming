package me.mjoacobf.openmusicstreaming.core.download.mux

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Construye la caja `udta/meta/ilst` con etiquetas estilo iTunes para archivos M4A,
 * que leen Android, la mayoría de reproductores y los gestores de música.
 */
object Mp4Tags {

    class Metadata(
        val title: String,
        val artist: String,
        val album: String?,
        val albumArtist: String?,
        val trackNumber: Int?,
        val year: Int?,
        val comment: String?,
        val coverJpeg: ByteArray?,
    )

    fun build(m: Metadata): ByteArray {
        val items = ByteArrayOutputStream()
        textItem(items, "©nam", m.title)
        textItem(items, "©ART", m.artist)
        m.album?.takeIf { it.isNotBlank() }?.let { textItem(items, "©alb", it) }
        m.albumArtist?.takeIf { it.isNotBlank() }?.let { textItem(items, "aART", it) }
        m.year?.let { textItem(items, "©day", it.toString()) }
        m.comment?.takeIf { it.isNotBlank() }?.let { textItem(items, "©cmt", it) }
        textItem(items, "©too", "Open Music Streaming")
        m.trackNumber?.let { track ->
            val payload = byteArrayOf(0, 0, (track shr 8).toByte(), track.toByte(), 0, 0, 0, 0)
            items.write(item("trkn", data(0, payload)))
        }
        m.coverJpeg?.let { items.write(item("covr", data(13, it))) }

        val ilst = box("ilst", items.toByteArray())
        // hdlr obligatorio dentro de meta: tipo "mdir", fabricante "appl"
        val hdlr = box(
            "hdlr",
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0) + "mdirappl".toByteArray(Charsets.US_ASCII) +
                ByteArray(9),
        )
        val meta = box("meta", ByteArray(4) + hdlr + ilst) // full box: versión/flags = 0
        return box("udta", meta)
    }

    private fun textItem(out: ByteArrayOutputStream, name: String, value: String) {
        if (value.isBlank()) return
        out.write(item(name, data(1, value.toByteArray(Charsets.UTF_8))))
    }

    private fun item(name: String, data: ByteArray) = box(name, data)

    /** Caja `data`: tipo (1 = UTF-8, 13 = JPEG, 0 = binario) + locale + contenido. */
    private fun data(type: Int, payload: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).apply {
            writeInt(type)
            writeInt(0)
            write(payload)
        }
        return box("data", bos.toByteArray())
    }

    private fun box(type: String, content: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(content.size + 8)
        DataOutputStream(bos).apply {
            writeInt(content.size + 8)
            // "©" debe escribirse como el byte 0xA9 (Latin-1), no en UTF-8
            write(type.toByteArray(Charsets.ISO_8859_1))
            write(content)
        }
        return bos.toByteArray()
    }
}
