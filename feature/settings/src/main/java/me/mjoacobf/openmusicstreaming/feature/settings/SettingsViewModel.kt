package me.mjoacobf.openmusicstreaming.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.MaintenanceRepository
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.UpdateRepository
import me.mjoacobf.openmusicstreaming.core.data.PlayHistoryRepository
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.download.DownloadManager
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.model.AppRelease
import me.mjoacobf.openmusicstreaming.core.model.AppSettings

/** Cada cambio se guarda al instante (no hay botón "Guardar"). */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val streaming: StreamingRepository,
    private val downloads: DownloadManager,
    private val playHistory: PlayHistoryRepository,
    private val maintenance: MaintenanceRepository,
    private val updates: UpdateRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    /** Versión instalada de la app. */
    val installedVersion: String get() = updates.installedVersion

    private val _cacheSize = MutableStateFlow(0L)
    val cacheSize: StateFlow<Long> = _cacheSize.asStateFlow()

    /** Versión nueva encontrada al buscar actualizaciones manualmente (nulo = ninguna). */
    private val _availableUpdate = MutableStateFlow<AppRelease?>(null)
    val availableUpdate: StateFlow<AppRelease?> = _availableUpdate.asStateFlow()

    fun refreshCacheSize() = viewModelScope.launch { _cacheSize.value = maintenance.cacheSize() }

    /** [clearImages] vacía la caché de carátulas (Coil, capa de UI); se ejecuta en un hilo de E/S. */
    fun clearCache(clearImages: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { clearImages() }
        maintenance.clearCache()
        _cacheSize.value = maintenance.cacheSize()
        _messages.tryEmit("Caché limpiada")
    }

    /** Deja la app como recién instalada (no borra los archivos de audio descargados). */
    fun clearAllData(clearImages: () -> Unit) = viewModelScope.launch {
        downloads.cancelAll()
        player.clearQueue()
        withContext(Dispatchers.IO) { clearImages() }
        maintenance.clearAllData()
        val s = repository.current()
        downloads.scheduleAutoUpdate(s.autoUpdateIntervalHours, s.autoUpdateWifiOnly)
        streaming.setContentCountry(s.contentCountry)
        _cacheSize.value = maintenance.cacheSize()
        _messages.tryEmit("Datos de la app borrados")
    }

    fun checkUpdatesNow() = viewModelScope.launch {
        _messages.tryEmit("Buscando actualizaciones…")
        updates.checkForUpdate()
            .onSuccess { release ->
                if (release != null) {
                    _availableUpdate.value = release
                } else {
                    _messages.tryEmit("Tienes la última versión (${updates.installedVersion})")
                }
            }
            .onFailure { _messages.tryEmit("No se pudo buscar actualizaciones") }
    }

    fun dismissUpdate() {
        val release = _availableUpdate.value ?: return
        _availableUpdate.value = null
        viewModelScope.launch { repository.update { it.copy(dismissedUpdateVersion = release.version) } }
    }

    fun closeUpdateDialog() {
        _availableUpdate.value = null
    }

    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        val before = settings.value
        repository.update(transform)
        val after = repository.current()
        if (before.autoUpdateIntervalHours != after.autoUpdateIntervalHours ||
            before.autoUpdateWifiOnly != after.autoUpdateWifiOnly
        ) {
            downloads.scheduleAutoUpdate(after.autoUpdateIntervalHours, after.autoUpdateWifiOnly)
        }
        if (before.contentCountry != after.contentCountry) streaming.setContentCountry(after.contentCountry)
    }

    fun clearHistory() = viewModelScope.launch {
        streaming.clearHistory()
        _messages.tryEmit("Historial de búsqueda borrado")
    }

    fun clearPlayHistory() = viewModelScope.launch {
        playHistory.clear()
        _messages.tryEmit("Historial de reproducción borrado")
    }

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching { repository.exportTo(uri) }
            .onSuccess { _messages.tryEmit("Configuración exportada") }
            .onFailure { _messages.tryEmit("No se pudo exportar: ${it.message}") }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        runCatching { repository.importFrom(uri) }
            .onSuccess {
                val s = repository.current()
                downloads.scheduleAutoUpdate(s.autoUpdateIntervalHours, s.autoUpdateWifiOnly)
                streaming.setContentCountry(s.contentCountry)
                _messages.tryEmit("Configuración importada")
            }
            .onFailure { _messages.tryEmit("Archivo no válido: ${it.message}") }
    }

    fun syncNow() = viewModelScope.launch {
        downloads.syncAllNow()
        _messages.tryEmit("Actualizando playlists en segundo plano")
    }
}
