package me.mjoacobf.openmusicstreaming.core.download

import me.mjoacobf.openmusicstreaming.core.download.mux.Id3Tags
import me.mjoacobf.openmusicstreaming.core.download.mux.Mp4Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Pruebas de la v1.021: recuperar la URL de origen guardada en las etiquetas de los archivos descargados. */
class V1021Test {
    private val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test
    fun `se lee la URL del comentario Vorbis de un Opus`() {
        val out = ByteArrayOutputStream()
        fun comment(text: String) {
            val b = text.toByteArray()
            out.write(byteArrayOf(b.size.toByte(), (b.size shr 8).toByte(), 0, 0))
            out.write(b)
        }
        out.write("OpusTags".toByteArray())
        comment("TITLE=Canción")
        comment("COMMENT=$url")
        comment("METADATA_BLOCK_PICTURE=AAAA")
        assertEquals(url, TagUrl.fromOgg(out.toByteArray()))
    }

    @Test
    fun `se lee la URL de las etiquetas M4A escritas por la app`() {
        val tags = Mp4Tags.build(Mp4Tags.Metadata("T", "A", "Al", "A", 1, 2020, url, null))
        assertEquals(url, TagUrl.fromMp4(ByteArray(100) + tags + ByteArray(50)))
    }

    @Test
    fun `se lee la URL de la etiqueta ID3 escrita por la app`() {
        val tags = Id3Tags.build(Id3Tags.Metadata(title = "T", artist = "A", comment = "https://soundcloud.com/a/b"))
        assertEquals("https://soundcloud.com/a/b", TagUrl.fromId3(tags))
    }

    @Test
    fun `sin comentario no se devuelve nada`() {
        val tags = Id3Tags.build(Id3Tags.Metadata(title = "T", artist = "A"))
        assertNull(TagUrl.fromId3(tags))
        assertNull(TagUrl.fromOgg("OpusTags".toByteArray()))
        assertNull(TagUrl.fromMp4(ByteArray(64)))
    }
}
