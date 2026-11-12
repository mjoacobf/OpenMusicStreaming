package me.mjoacobf.openmusicstreaming.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.UserPlaylistRepository
import me.mjoacobf.openmusicstreaming.core.download.LibraryScanner
import me.mjoacobf.openmusicstreaming.core.download.ScanResult
import me.mjoacobf.openmusicstreaming.core.download.DownloadManager
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.media.toMediaItem
import me.mjoacobf.openmusicstreaming.core.model.DownloadStatus
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.LocalPlaylist
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.Song
import me.mjoacobf.openmusicstreaming.core.model.UserPlaylist

data class LibraryUiState(
    val loading: Boolean = true,
    val playlists: List<LocalPlaylist> = emptyList(),
    val songs: List<Song> = emptyList(),
    val downloads: Map<String, DownloadStatus> = emptyMap(),
    val userPlaylists: List<UserPlaylist> = emptyList(),
)

class LibraryViewModel(
    private val library: LibraryRepository,
    private val downloads: DownloadManager,
    private val player: PlayerConnection,
    private val userPlaylists: UserPlaylistRepository,
    private val scanner: LibraryScanner,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Estado del diálogo «Analizar carpeta de descargas». */
    data class ScanUiState(
        val visible: Boolean = false,
        val treeUri: String? = null,
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val result: ScanResult? = null,
        val error: String? = null,
    )

    private val _scan = MutableStateFlow(ScanUiState())
    val scan: StateFlow<ScanUiState> = _scan.asStateFlow()
    private var scanJob: Job? = null

    fun openScan() {
        viewModelScope.launch {
            _scan.value = ScanUiState(visible = true, treeUri = settings.current().downloadTreeUri)
        }
    }

    fun closeScan() {
        scanJob?.cancel()
        _scan.value = ScanUiState()
    }

    /** Carpeta elegida en el selector del sistema (se conserva como carpeta de descargas). */
    fun setScanFolder(uri: String) {
        viewModelScope.launch { settings.update { it.copy(downloadTreeUri = uri) } }
        _scan.update { it.copy(treeUri = uri, result = null, error = null) }
    }

    fun startScan() {
        val tree = _scan.value.treeUri
        if (tree == null || !downloads.hasFolderPermission(tree)) {
            _scan.update { it.copy(error = "Elige la carpeta de descargas para poder leerla.") }
            return
        }
        _scan.update { it.copy(running = true, done = 0, total = 0, result = null, error = null) }
        scanJob = viewModelScope.launch {
            try {
                val result = scanner.scan(tree) { done, total -> _scan.update { it.copy(done = done, total = total) } }
                _scan.update { it.copy(running = false, result = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scan.update { it.copy(running = false, error = e.message ?: "No se pudo analizar la carpeta") }
            }
        }
    }

    val state: StateFlow<LibraryUiState> = combine(
        library.observePlaylists(),
        library.observeDownloadedSongs(),
        downloads.statuses,
        userPlaylists.observeAll(),
    ) { playlists, songs, statuses, mine ->
        LibraryUiState(false, playlists, songs, statuses, mine)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setAutoUpdate(playlist: LocalPlaylist, enabled: Boolean) = viewModelScope.launch {
        library.setAutoUpdate(playlist.id, enabled)
    }

    fun sync(playlist: LocalPlaylist) = viewModelScope.launch {
        downloads.syncPlaylist(playlist.remoteUrl, playlist.title)
    }

    fun syncAll() = viewModelScope.launch { downloads.syncAllNow() }

    fun deletePlaylist(playlist: LocalPlaylist, deleteFiles: Boolean) = viewModelScope.launch {
        library.deletePlaylist(playlist.id, deleteFiles)
    }

    fun deleteSong(song: Song) = viewModelScope.launch { library.deleteSong(song.videoId) }

    fun play(songs: List<Song>, index: Int) = player.playSongs(songs, index, shuffle = false)

    fun shuffle(songs: List<Song>) = player.playSongs(songs, 0, shuffle = true)

    fun playNext(song: Song) = player.playNext(song.toMediaItem())

    fun addToQueue(song: Song) = player.addToQueue(song.toMediaItem())

    fun dismissDownload(key: String) = downloads.dismiss(key)

    fun cancelDownload(key: String) = downloads.cancel(key)

    // Playlists propias

    fun createPlaylist(name: String) = viewModelScope.launch { userPlaylists.create(name) }

    fun addSongToPlaylist(playlistId: Long, song: Song) = viewModelScope.launch {
        userPlaylists.add(playlistId, listOf(song.toSummary()))
    }

    fun createPlaylistWith(name: String, song: Song) = viewModelScope.launch {
        userPlaylists.add(userPlaylists.create(name), listOf(song.toSummary()))
    }

    private fun Song.toSummary() = MediaSummary(url, videoId, ItemType.SONG, title, artist, thumbnailUrl, durationSec)
}

class PlaylistViewModel(
    private val playlistId: Long,
    private val library: LibraryRepository,
    private val downloads: DownloadManager,
    private val player: PlayerConnection,
) : ViewModel() {

    data class UiState(
        val playlist: LocalPlaylist? = null,
        val songs: List<me.mjoacobf.openmusicstreaming.core.model.PlaylistSong> = emptyList(),
        val syncing: DownloadStatus? = null,
        val loaded: Boolean = false,
    )

    val state: StateFlow<UiState> = combine(
        library.observePlaylist(playlistId),
        library.observePlaylistSongs(playlistId),
        downloads.statuses,
    ) { playlist, songs, statuses ->
        UiState(playlist, songs, playlist?.let { statuses[it.remoteUrl] }, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val playableSongs get() = state.value.songs.map { it.song }.filter { it.isDownloaded }

    fun play(index: Int) {
        val all = state.value.songs.map { it.song }
        val target = all.getOrNull(index) ?: return
        val playable = playableSongs
        player.playSongs(playable, playable.indexOf(target).coerceAtLeast(0), shuffle = false)
    }

    fun playAll() = player.playSongs(playableSongs, 0, shuffle = false)

    fun shuffle() = player.playSongs(playableSongs, 0, shuffle = true)

    fun setAutoUpdate(enabled: Boolean) = viewModelScope.launch { library.setAutoUpdate(playlistId, enabled) }

    fun sync() = viewModelScope.launch {
        state.value.playlist?.let { downloads.syncPlaylist(it.remoteUrl, it.title) }
    }

    fun playNext(song: Song) = player.playNext(song.toMediaItem())

    fun addToQueue(song: Song) = player.addToQueue(song.toMediaItem())

    fun deleteSong(song: Song) = viewModelScope.launch { library.deleteSong(song.videoId) }
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}

internal fun List<Song>.totalSize() = sumOf { it.fileSizeBytes }
