package me.mjoacobf.openmusicstreaming.core.download

import me.mjoacobf.openmusicstreaming.core.download.mux.Id3Tags
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import me.mjoacobf.openmusicstreaming.core.model.idOf
import me.mjoacobf.openmusicstreaming.core.model.streamUrlOf
import me.mjoacobf.openmusicstreaming.core.model.upscaleThumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la v1.01: identificadores por servicio, ID3, miniaturas y claves de archivo. */
class MultiServiceTest {

    @Test
    fun `los ids de YouTube no llevan prefijo y los de otros servicios si`() {
        assertEquals("dQw4w9WgXcQ", idOf(MusicService.YOUTUBE, "https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ"))
        val sc = idOf(MusicService.SOUNDCLOUD, "https://soundcloud.com/a/b", null)
        assertEquals("sc:https://soundcloud.com/a/b", sc)
        assertEquals(MusicService.SOUNDCLOUD, MusicService.ofId(sc))
        assertEquals(MusicService.YOUTUBE, MusicService.ofId("dQw4w9WgXcQ"))
    }

    @Test
    fun `la URL de la pista se recupera desde el id`() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", streamUrlOf("dQw4w9WgXcQ"))
        assertEquals("https://x.bandcamp.com/track/t", streamUrlOf("bc:https://x.bandcamp.com/track/t"))
    }

    @Test
    fun `se detecta el servicio por el host`() {
        assertEquals(MusicService.SOUNDCLOUD, MusicService.ofUrl("https://on.soundcloud.com/abc"))
        assertEquals(MusicService.BANDCAMP, MusicService.ofUrl("https://artista.bandcamp.com/album/x"))
        assertEquals(MusicService.YOUTUBE, MusicService.ofUrl("https://music.youtube.com/watch?v=1"))
    }

    @Test
    fun `solo hay filtros musicales`() {
        assertEquals(4, MusicService.YOUTUBE.filters.size)
        assertTrue(MusicService.BANDCAMP.filters.isEmpty())
    }

    @Test
    fun `se pide mayor resolucion en carátulas de googleusercontent`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj",
            upscaleThumbnail("https://lh3.googleusercontent.com/abc=w226-h226-l90-rj", 1080),
        )
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", upscaleThumbnail("https://i.ytimg.com/vi/x/hqdefault.jpg", 1080))
        assertNull(upscaleThumbnail(null, 600))
    }

    @Test
    fun `claves de archivo seguras para ids con URL`() {
        assertEquals("dQw4w9WgXcQ", fileKey("dQw4w9WgXcQ"))
        val key = fileKey("sc:https://soundcloud.com/artista/tema")
        assertTrue(key.all { it.isLetterOrDigit() })
        assertTrue(fileKey("sc:https://soundcloud.com/a/b") != fileKey("sc:https://soundcloud.com/a/c"))
    }

    @Test
    fun `etiqueta ID3 v2_3 con cabecera y tamanio syncsafe correctos`() {
        val tag = Id3Tags.build(
            Id3Tags.Metadata(title = "Título", artist = "Artista", album = "Álbum", year = 2020, coverJpeg = byteArrayOf(1, 2, 3)),
        )
        assertEquals("ID3", String(tag, 0, 3, Charsets.ISO_8859_1))
        assertEquals(3, tag[3].toInt())
        assertEquals(tag.size, Id3Tags.existingTagSize(tag.copyOf(10)))
        val text = String(tag, Charsets.ISO_8859_1)
        listOf("TIT2", "TPE1", "TALB", "TYER", "APIC").forEach { assertTrue("falta $it", text.contains(it)) }
    }

    @Test
    fun `sin etiqueta ID3 no se salta nada`() {
        assertEquals(0, Id3Tags.existingTagSize(ByteArray(10) { 0xFF.toByte() }))
    }
}
