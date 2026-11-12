package me.mjoacobf.openmusicstreaming.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.BookmarkRepository
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.UserPlaylistRepository
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.media.PlayerState
import me.mjoacobf.openmusicstreaming.core.media.toSummary
import me.mjoacobf.openmusicstreaming.core.model.AppSettings

class PlayerViewModel(
    val connection: PlayerConnection,
    private val settingsRepository: SettingsRepository,
    private val bookmarks: BookmarkRepository,
    private val userPlaylists: UserPlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<me.mjoacobf.openmusicstreaming.core.model.UserPlaylist>> =
        userPlaylists.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addToPlaylist(playlistId: Long, item: MediaItem) = viewModelScope.launch {
        item.toSummary()?.let { userPlaylists.add(playlistId, listOf(it)) }
    }

    fun createPlaylistWith(name: String, item: MediaItem) = viewModelScope.launch {
        item.toSummary()?.let { userPlaylists.add(userPlaylists.create(name), listOf(it)) }
    }


    val state: StateFlow<PlayerState> = connection.state
    val position: StateFlow<Long> = connection.position

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val bookmarkedUrls: StateFlow<Set<String>> =
        bookmarks.observeUrls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        connection.connect()
    }

    fun togglePlay() = connection.togglePlay()
    fun next() = connection.next()
    fun previous() = connection.previous(settings.value.previousRestartsAfterSec)
    fun seekTo(ms: Long) = connection.seekTo(ms)
    fun toggleShuffle() = connection.toggleShuffle()
    fun cycleRepeat() = connection.cycleRepeat()
    fun skipTo(index: Int) = connection.skipTo(index)
    fun removeAt(index: Int) = connection.removeAt(index)
    fun move(from: Int, to: Int) = connection.move(from, to)
    fun clearQueue() = connection.clearQueue()
    fun setVideoEnabled(enabled: Boolean) = connection.setVideoEnabled(enabled)

    /** Switch de reproducción automática de recomendados. */
    fun setAutoplay(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(autoplayRelated = enabled) }
    }

    fun toggleBookmark(item: MediaItem) = viewModelScope.launch {
        val summary = item.toSummary() ?: return@launch
        bookmarks.toggle(summary, summary.url in bookmarkedUrls.value)
    }
}
