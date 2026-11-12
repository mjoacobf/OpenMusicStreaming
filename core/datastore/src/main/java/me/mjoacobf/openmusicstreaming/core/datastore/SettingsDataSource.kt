package me.mjoacobf.openmusicstreaming.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.mjoacobf.openmusicstreaming.core.model.AppSettings

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persiste [AppSettings] como un único documento JSON. Cada cambio se guarda de inmediato,
 * y el mismo formato se usa para exportar/importar configuraciones.
 */
class SettingsDataSource(context: Context) {
    private val store = context.applicationContext.settingsStore
    private val key = stringPreferencesKey("app_settings_json")

    val settings: Flow<AppSettings> = store.data
        .map { prefs -> prefs[key]?.let(::decodeOrDefault) ?: AppSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = prefs[key]?.let(::decodeOrDefault) ?: AppSettings()
            prefs[key] = transform(current).toJson()
        }
    }

    suspend fun replace(settings: AppSettings) {
        store.edit { it[key] = settings.toJson() }
    }

    private fun decodeOrDefault(text: String): AppSettings =
        runCatching { AppSettings.fromJson(text) }.getOrDefault(AppSettings())
}
