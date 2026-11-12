package me.mjoacobf.openmusicstreaming.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.download.DownloadManager
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.media.toMediaItem
import me.mjoacobf.openmusicstreaming.core.model.DownloadStatus
import me.mjoacobf.openmusicstreaming.core.model.LocalPlaylist
import me.mjoacobf.openmusicstreaming.core.model.Song

data class LibraryUiState(
    val loading: Boolean = true,
    val playlists: List<LocalPlaylist> = emptyList(),
    val songs: List<Song> = emptyList(),
    val downloads: Map<String, DownloadStatus> = emptyMap(),
)

class LibraryViewModel(
    private val library: LibraryRepository,
    private val downloads: DownloadManager,
    private val player: PlayerConnection,
) : ViewModel() {

    val state: StateFlow<LibraryUiState> = combine(
        library.observePlaylists(),
        library.observeDownloadedSongs(),
        downloads.statuses,
    ) { playlists, songs, statuses ->
        LibraryUiState(false, playlists, songs, statuses)
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
