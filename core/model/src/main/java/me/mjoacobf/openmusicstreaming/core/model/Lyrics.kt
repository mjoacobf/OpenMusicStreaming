package me.mjoacobf.openmusicstreaming.core.model

/** Línea de letra; [timeMs] es -1 si la letra no está sincronizada. */
data class LyricLine(val timeMs: Long, val text: String)

/** Letra de una canción (de LRCLIB). Si [synced] es falso solo hay texto plano. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** Índice de la línea activa para la posición dada (-1 antes de la primera). */
    fun activeIndex(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    companion object {
        private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

        /** Analiza el formato LRC (`[mm:ss.xx] texto`, admite varias marcas por línea). */
        fun parseLrc(text: String): Lyrics? {
            val lines = ArrayList<LyricLine>()
            for (raw in text.lineSequence()) {
                val tags = TIME_TAG.findAll(raw).toList()
                if (tags.isEmpty()) continue
                val content = raw.substring(tags.last().range.last + 1).trim()
                for (tag in tags) {
                    val (min, sec, frac) = tag.destructured
                    val fractionMs = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    lines += LyricLine(min.toLong() * 60_000 + sec.toLong() * 1_000 + fractionMs, content)
                }
            }
            if (lines.isEmpty()) return null
            return Lyrics(lines.sortedBy { it.timeMs }, synced = true)
        }

        fun parsePlain(text: String): Lyrics? {
            val lines = text.lines().map { LyricLine(-1, it.trim()) }.dropWhile { it.text.isEmpty() }
            return if (lines.isEmpty()) null else Lyrics(lines, synced = false)
        }
    }
}

/** Versión publicada más reciente (GitHub) para el aviso de actualización. */
data class AppRelease(val version: String, val url: String)

/**
 * Compara versiones. Las del proyecto son decimales de dos segmentos (`1.01`, `1.02`, `1.021`): el
 * segundo se compara como fracción (`1.021` es posterior a `1.02` y anterior a `1.03`). Con tres o
 * más segmentos (`1.2.3`) se compara numéricamente por segmentos.
 */
fun isNewerVersion(candidate: String, current: String): Boolean {
    fun clean(v: String) = v.trim().removePrefix("v").removePrefix("V")
    fun parts(v: String) = clean(v).split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val c = clean(candidate).split('.')
    val k = clean(current).split('.')
    if (c.size <= 2 && k.size <= 2) {
        val major = (c[0].toIntOrNull() ?: 0).compareTo(k[0].toIntOrNull() ?: 0)
        if (major != 0) return major > 0
        val ca = c.getOrElse(1) { "0" }.takeWhile(Char::isDigit)
        val ka = k.getOrElse(1) { "0" }.takeWhile(Char::isDigit)
        val width = maxOf(ca.length, ka.length)
        return ca.padEnd(width, '0') > ka.padEnd(width, '0')
    }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
