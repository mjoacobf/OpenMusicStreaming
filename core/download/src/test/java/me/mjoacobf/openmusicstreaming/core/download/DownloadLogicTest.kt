package me.mjoacobf.openmusicstreaming.core.download

import me.mjoacobf.openmusicstreaming.core.download.mux.Mp4Tags
import me.mjoacobf.openmusicstreaming.core.extractor.cleanCollectionTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class DownloadLogicTest {

    @Test
    fun `canciones Topic usan el canal como artista`() {
        assertEquals(
            "Never Gonna Give You Up" to "Rick Astley",
            TrackNames.from("Never Gonna Give You Up", "Rick Astley - Topic"),
        )
    }

    @Test
    fun `videos oficiales se separan en artista y titulo sin sufijos`() {
        assertEquals(
            "Never Gonna Give You Up" to "Rick Astley",
            TrackNames.from("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", "Rick Astley"),
        )
        assertEquals(
            "Take On Me" to "a-ha",
            TrackNames.from("a-ha - Take On Me (Official Video) [4K]", "a-ha"),
        )
    }

    @Test
    fun `titulos sin guion conservan el canal`() {
        assertEquals("Digital Love" to "Daft Punk", TrackNames.from("Digital Love", "Daft Punk"))
    }

    @Test
    fun `se quita el prefijo de tipo de YouTube Music`() {
        assertEquals("Whenever You Need Somebody", cleanCollectionTitle("Album – Whenever You Need Somebody"))
        assertEquals("Random Access Memories", cleanCollectionTitle("Álbum - Random Access Memories"))
        assertEquals("Mi playlist - favoritos", cleanCollectionTitle("Mi playlist - favoritos"))
    }

    @Test
    fun `nombres de archivo seguros`() {
        assertEquals("AC_DC - Back In Black", sanitizeFileName("AC/DC - Back In Black"))
        assertEquals("Que_", sanitizeFileName("Que?"))
        assertEquals("Sin título", sanitizeFileName("   "))
    }

    @Test
    fun `etiquetas mp4 forman una caja udta valida`() {
        val udta = Mp4Tags.build(
            Mp4Tags.Metadata(
                title = "Título",
                artist = "Artista",
                album = "Álbum",
                albumArtist = null,
                trackNumber = 3,
                year = 2013,
                comment = null,
                coverJpeg = byteArrayOf(1, 2, 3),
            ),
        )
        val buffer = ByteBuffer.wrap(udta)
        assertEquals(udta.size, buffer.int) // el tamaño declarado coincide
        assertEquals("udta", String(udta, 4, 4, Charsets.ISO_8859_1))
        val text = String(udta, Charsets.ISO_8859_1)
        listOf("meta", "hdlr", "mdir", "ilst", "©nam", "©ART", "©alb", "trkn", "covr").forEach {
            assertTrue("falta $it", text.contains(it))
        }
    }
}
