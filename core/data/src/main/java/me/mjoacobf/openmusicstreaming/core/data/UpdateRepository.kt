package me.mjoacobf.openmusicstreaming.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.model.AppRelease
import me.mjoacobf.openmusicstreaming.core.model.isNewerVersion
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Aviso de nuevas versiones. Igual que NewPipe, la app no se actualiza sola ni descarga nada:
 * consulta cuál es la última versión publicada y, si es más nueva que la instalada, ofrece abrir
 * su página de GitHub (la actualización es opcional). La consulta usa la API pública de releases
 * de GitHub; no envía ningún dato del usuario.
 */
class UpdateRepository(
    private val context: Context,
    private val client: OkHttpClient,
) {
    /** Versión instalada (p. ej. "1.02"). */
    val installedVersion: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
    }

    /** La última versión publicada si es posterior a la instalada; null si no hay una nueva. */
    suspend fun checkForUpdate(): Result<AppRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenMusicStreaming")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub respondió ${response.code}")
                val json = JSONObject(response.body.string())
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@use null
                val tag = json.getString("tag_name")
                val url = json.optString("html_url").ifBlank { "$REPO_URL/releases/tag/$tag" }
                if (isNewerVersion(tag, installedVersion)) AppRelease(tag.removePrefix("v"), url) else null
            }
        }
    }

    companion object {
        const val REPO_URL = "https://github.com/mjoacobf/OpenMusicStreaming"
        private const val LATEST_API = "https://api.github.com/repos/mjoacobf/OpenMusicStreaming/releases/latest"
    }
}
