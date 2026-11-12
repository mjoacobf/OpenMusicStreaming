package me.mjoacobf.openmusicstreaming.core.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.mjoacobf.openmusicstreaming.core.model.DownloadStatus

/** Estado en memoria de las descargas activas (para mostrar progreso en la UI). */
class DownloadTracker {
    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()

    fun set(status: DownloadStatus) = _statuses.update { it + (status.key to status) }

    fun update(key: String, transform: (DownloadStatus) -> DownloadStatus) = _statuses.update { map ->
        val current = map[key] ?: return@update map
        map + (key to transform(current))
    }

    fun remove(key: String) = _statuses.update { it - key }
}
