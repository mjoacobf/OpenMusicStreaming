package me.mjoacobf.openmusicstreaming.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.PlayHistoryRepository
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.download.DownloadManager
import me.mjoacobf.openmusicstreaming.core.model.AppSettings

/** Cada cambio se guarda al instante (no hay botón "Guardar"). */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val streaming: StreamingRepository,
    private val downloads: DownloadManager,
    private val playHistory: PlayHistoryRepository,
) : ViewModel() {

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
