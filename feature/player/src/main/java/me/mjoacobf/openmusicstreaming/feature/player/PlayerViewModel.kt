package me.mjoacobf.openmusicstreaming.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.BookmarkRepository
import me.mjoacobf.openmusicstreaming.core.data.LyricsRepository
import me.mjoacobf.openmusicstreaming.core.data.LyricsResult
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.data.UserPlaylistRepository
import me.mjoacobf.openmusicstreaming.core.media.MediaIds
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.media.PlayerState
import me.mjoacobf.openmusicstreaming.core.media.toSummary
import me.mjoacobf.openmusicstreaming.core.model.AppSettings
import me.mjoacobf.openmusicstreaming.core.model.Lyrics
import me.mjoacobf.openmusicstreaming.core.model.MusicService

class PlayerViewModel(
    val connection: PlayerConnection,
    private val settingsRepository: SettingsRepository,
    private val bookmarks: BookmarkRepository,
    private val userPlaylists: UserPlaylistRepository,
    private val streaming: StreamingRepository,
    private val lyricsRepository: LyricsRepository,
) : ViewModel() {

    /** Estado de la letra de la canción actual (función experimental, LRCLIB). */
    sealed interface LyricsUiState {
        data object Idle : LyricsUiState
        data object Loading : LyricsUiState
        data class Ready(val lyrics: Lyrics) : LyricsUiState
        data object NotFound : LyricsUiState
        data class Error(val message: String) : LyricsUiState
    }

    /** Navegación pedida desde el reproductor (la pantalla la traduce a rutas). */
    sealed interface Event {
        data class OpenArtist(val url: String) : Event
        data class OpenAlbum(val url: String) : Event
        data class Message(val text: String) : Event
    }

    private val _lyrics = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()
    private var lyricsJob: Job? = null
    private var lyricsFor: String? = null

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 2)
    val events: SharedFlow<Event> = _events

    /** Carga la letra de [item] (no repite la consulta si ya está cargada, salvo [force]). */
    fun loadLyrics(item: MediaItem, force: Boolean = false) {
        if (!force && lyricsFor == item.mediaId) return
        lyricsFor = item.mediaId
        lyricsJob?.cancel()
        _lyrics.value = LyricsUiState.Loading
        val meta = item.mediaMetadata
        lyricsJob = viewModelScope.launch {
            val result = lyricsRepository.lyrics(
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                album = meta.albumTitle?.toString(),
                durationSec = meta.extras?.getLong(MediaIds.EXTRA_DURATION) ?: -1,
            )
            _lyrics.value = when (result) {
                is LyricsResult.Found -> LyricsUiState.Ready(result.lyrics)
                LyricsResult.NotFound -> LyricsUiState.NotFound
                is LyricsResult.Failed -> LyricsUiState.Error(result.message)
            }
        }
    }

    fun retryLyrics(item: MediaItem) = loadLyrics(item, force = true)

    private fun serviceOf(item: MediaItem) = MusicService.ofId(MediaIds.videoId(item.mediaId))

    /** Abre el perfil del artista: usa el enlace guardado o, si no hay, lo busca por nombre. */
    fun openArtist(item: MediaItem) = viewModelScope.launch {
        val known = item.toSummary()?.artistUrl
        val name = item.mediaMetadata.artist?.toString().orEmpty()
        val url = known ?: runCatching { streaming.findArtistUrl(name, serviceOf(item)) }.getOrNull()
        _events.tryEmit(if (url != null) Event.OpenArtist(url) else Event.Message("No se encontró el perfil del artista"))
    }

    /**
     * Abre el álbum: YouTube no informa el álbum de una canción, así que se busca por el nombre del
     * álbum (si la canción está descargada) o por el título de la canción junto al artista.
     */
    fun openAlbum(item: MediaItem) = viewModelScope.launch {
        val meta = item.mediaMetadata
        val albumOrTitle = meta.albumTitle?.toString()?.takeIf { it.isNotBlank() } ?: meta.title?.toString().orEmpty()
        val artist = meta.artist?.toString().orEmpty()
        val url = runCatching { streaming.findAlbumUrl(albumOrTitle, artist, serviceOf(item)) }.getOrNull()
        _events.tryEmit(if (url != null) Event.OpenAlbum(url) else Event.Message("No se encontró el álbum"))
    }

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
