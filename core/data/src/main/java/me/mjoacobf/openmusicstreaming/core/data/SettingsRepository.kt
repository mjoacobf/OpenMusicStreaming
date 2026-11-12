package me.mjoacobf.openmusicstreaming.core.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.datastore.SettingsDataSource
import me.mjoacobf.openmusicstreaming.core.model.AppSettings

/** Configuración con guardado automático e importación/exportación en JSON. */
class SettingsRepository(
    private val context: Context,
    private val dataSource: SettingsDataSource,
) {
    val settings: Flow<AppSettings> = dataSource.settings

    suspend fun current(): AppSettings = dataSource.current()

    suspend fun update(transform: (AppSettings) -> AppSettings) = dataSource.update(transform)

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val json = current().toJson()
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
            ?: error("No se pudo abrir el archivo de destino")
    }

    /** Importa y valida; los campos desconocidos o inválidos se ignoran. */
    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("No se pudo leer el archivo")
        val imported = AppSettings.fromJson(text)
        dataSource.replace(imported)
    }
}
