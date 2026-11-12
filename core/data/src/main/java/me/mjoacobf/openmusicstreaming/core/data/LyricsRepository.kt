package me.mjoacobf.openmusicstreaming.core.data

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.model.Lyrics
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.abs

/** Resultado de buscar la letra de una canción. */
sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics) : LyricsResult
    data object NotFound : LyricsResult
    data class Failed(val message: String) : LyricsResult
}

/**
 * Letras desde LRCLIB (https://lrclib.net): API pública y gratuita, sin clave, que ofrece letras
 * sincronizadas en formato LRC. Se prefiere la versión sincronizada y, si no existe, el texto plano.
 * Las letras encontradas se guardan en la caché de la app (se borran con "Limpiar caché").
 * Es una función experimental: la coincidencia se hace por título, artista y duración.
 */
class LyricsRepository(
    context: Context,
    private val client: OkHttpClient,
) {
    private val dir = File(context.cacheDir, DIR_NAME)
    private val memory = LruCache<String, LyricsResult>(32)

    suspend fun lyrics(title: String, artist: String, album: String?, durationSec: Long): LyricsResult =
        withContext(Dispatchers.IO) {
            val cleanTitle = cleanTitle(title)
            val firstArtist = artist.split(", ", " & ", " x ").first().trim()
            if (cleanTitle.isBlank()) return@withContext LyricsResult.NotFound
            val key = sha("$cleanTitle|$firstArtist|$durationSec")
            memory.get(key)?.let { return@withContext it }
            readDisk(key)?.let { return@withContext LyricsResult.Found(it).also { r -> memory.put(key, r) } }
            try {
                val found = fetchExact(cleanTitle, firstArtist, album, durationSec)
                    ?: fetchSearch(cleanTitle, firstArtist, durationSec)
                val result = found?.let { LyricsResult.Found(it) } ?: LyricsResult.NotFound
                memory.put(key, result)
                if (found != null) writeDisk(key, found)
                result
            } catch (e: IOException) {
                LyricsResult.Failed("Sin conexión o LRCLIB no responde")
            } catch (e: Exception) {
                LyricsResult.Failed(e.message ?: "Error desconocido")
            }
        }

    private fun fetchExact(title: String, artist: String, album: String?, durationSec: Long): Lyrics? {
        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply {
                if (!album.isNullOrBlank()) addQueryParameter("album_name", album)
                if (durationSec > 0) addQueryParameter("duration", durationSec.toString())
            }
            .build()
        val body = call(url.toString()) ?: return null
        return parse(JSONObject(body))
    }

    /** Búsqueda aproximada: se elige el resultado con duración parecida (±4 s) y letra sincronizada si hay. */
    private fun fetchSearch(title: String, artist: String, durationSec: Long): Lyrics? {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val array = JSONArray(call(url.toString()) ?: return null)
        val candidates = (0 until array.length()).map { array.getJSONObject(it) }
            .filter { durationSec <= 0 || abs(it.optDouble("duration", -100.0) - durationSec) <= 4 }
        val ordered = candidates.sortedByDescending { !it.isNull("syncedLyrics") && it.optString("syncedLyrics").isNotBlank() }
        return ordered.firstNotNullOfOrNull { parse(it) }
    }

    /** Cuerpo de la respuesta, o null si no hay resultado (404). */
    private fun call(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("LRCLIB respondió ${response.code}")
            return response.body.string()
        }
    }

    private fun parse(json: JSONObject): Lyrics? {
        if (json.optBoolean("instrumental")) return null
        val synced = if (json.isNull("syncedLyrics")) "" else json.optString("syncedLyrics")
        if (synced.isNotBlank()) Lyrics.parseLrc(synced)?.let { return it }
        val plain = if (json.isNull("plainLyrics")) "" else json.optString("plainLyrics")
        return if (plain.isNotBlank()) Lyrics.parsePlain(plain) else null
    }

    private fun readDisk(key: String): Lyrics? = runCatching {
        val file = File(dir, key)
        if (!file.exists()) return null
        val text = file.readText()
        if (text.startsWith("S\n")) Lyrics.parseLrc(text.drop(2)) else Lyrics.parsePlain(text.drop(2))
    }.getOrNull()

    private fun writeDisk(key: String, lyrics: Lyrics) {
        runCatching {
            dir.mkdirs()
            val text = if (lyrics.synced) {
                "S\n" + lyrics.lines.joinToString("\n") { l ->
                    val m = l.timeMs / 60_000
                    val s = (l.timeMs % 60_000) / 1000
                    val cs = (l.timeMs % 1000) / 10
                    "[%02d:%02d.%02d]%s".format(m, s, cs, l.text)
                }
            } else {
                "P\n" + lyrics.lines.joinToString("\n") { it.text }
            }
            File(dir, key).writeText(text)
        }
    }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val DIR_NAME = "lyrics"
        private const val BASE = "https://lrclib.net/api"
        private const val USER_AGENT = "OpenMusicStreaming (https://github.com/mjoacobf/OpenMusicStreaming)"

        private val NOISE = Regex(
            "\\s*[(\\[][^)\\]]*(official|video|lyric|audio|visuali[sz]er|hd|4k|remaster|letra|oficial)[^)\\]]*[)\\]]",
            RegexOption.IGNORE_CASE,
        )

        /** Quita sufijos como "(Official Video)" que impiden coincidir con LRCLIB. */
        internal fun cleanTitle(title: String): String = title.replace(NOISE, "").trim()
    }
}
