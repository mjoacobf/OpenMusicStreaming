package me.mjoacobf.openmusicstreaming.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.database.AppDatabase
import me.mjoacobf.openmusicstreaming.core.datastore.SettingsDataSource
import me.mjoacobf.openmusicstreaming.core.extractor.StreamResolver
import me.mjoacobf.openmusicstreaming.core.model.AppSettings
import java.io.File

/** Limpieza de la caché y de los datos acumulados de la app. */
class MaintenanceRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsDataSource,
    private val resolver: StreamResolver,
) {
    /** Bytes ocupados por la caché (carátulas, letras y archivos temporales). */
    suspend fun cacheSize(): Long = withContext(Dispatchers.IO) { cacheFiles().sumOf(::size) }

    /**
     * Borra la caché de archivos y las URLs de streaming guardadas. Respeta los temporales de las
     * descargas en curso (`dl_*.tmp`). La caché de imágenes en memoria/disco la vacía antes la capa
     * de UI (Coil).
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheFiles().forEach { it.deleteRecursively() }
        resolver.clear()
    }

    /**
     * Restablece la app como recién instalada: borra playlists propias, marcadores, historiales,
     * registro de descargas y ajustes. NO borra los archivos de audio de la carpeta del usuario.
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        settings.replace(AppSettings())
        cacheFiles().forEach { it.deleteRecursively() }
        resolver.clear()
    }

    private fun cacheFiles(): List<File> =
        context.cacheDir.listFiles().orEmpty().filterNot { it.name.startsWith("dl_") && it.name.endsWith(".tmp") }

    private fun size(file: File): Long = if (file.isDirectory) file.listFiles().orEmpty().sumOf(::size) else file.length()
}
