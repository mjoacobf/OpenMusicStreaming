package me.mjoacobf.openmusicstreaming.core.download

import me.mjoacobf.openmusicstreaming.core.model.Lyrics
import me.mjoacobf.openmusicstreaming.core.model.isNewerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la v1.02: letras LRC y comparación de versiones del aviso de actualización. */
class V102Test {

    @Test
    fun `se analiza una letra LRC sincronizada`() {
        val lyrics = Lyrics.parseLrc("[00:12.50]Hola\n[01:02.5]Mundo\n[00:30.123]Medio\nsin marca")!!
        assertTrue(lyrics.synced)
        assertEquals(listOf("Hola", "Medio", "Mundo"), lyrics.lines.map { it.text })
        assertEquals(listOf(12_500L, 30_123L, 62_500L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun `una linea con varias marcas se repite`() {
        val lyrics = Lyrics.parseLrc("[00:10.00][00:20.00]Estribillo")!!
        assertEquals(listOf(10_000L, 20_000L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun `la linea activa sigue la posicion`() {
        val lyrics = Lyrics.parseLrc("[00:10.00]a\n[00:20.00]b\n[00:30.00]c")!!
        assertEquals(-1, lyrics.activeIndex(5_000))
        assertEquals(0, lyrics.activeIndex(10_000))
        assertEquals(1, lyrics.activeIndex(29_999))
        assertEquals(2, lyrics.activeIndex(999_999))
    }

    @Test
    fun `sin marcas de tiempo no es LRC y el texto plano no se sincroniza`() {
        assertNull(Lyrics.parseLrc("solo texto"))
        val plain = Lyrics.parsePlain("\nuno\ndos")!!
        assertFalse(plain.synced)
        assertEquals(-1, plain.activeIndex(1_000))
        assertEquals(listOf("uno", "dos"), plain.lines.map { it.text })
    }

    @Test
    fun `se compara la version publicada con la instalada`() {
        assertTrue(isNewerVersion("v1.02", "1.01"))
        assertTrue(isNewerVersion("1.10", "1.02"))
        assertTrue(isNewerVersion("2.0", "1.99"))
        assertFalse(isNewerVersion("v1.02", "1.02"))
        assertFalse(isNewerVersion("1.01", "1.02"))
    }
}
