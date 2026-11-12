package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.model.AppSettings
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.PageToken
import me.mjoacobf.openmusicstreaming.core.model.RemoteCollection
import me.mjoacobf.openmusicstreaming.core.model.StreamDetails

/** Base con flujos comunes a las pantallas de detalle. */
abstract class RemoteViewModel(
    library: LibraryRepository,
    val actions: RemoteActions,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        actions.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val bookmarked: StateFlow<Set<String>> =
        actions.bookmarkedUrls.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val downloadedIds: StateFlow<Set<String>> =
        library.observeDownloadedIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}

class StreamDetailViewModel(
    private val url: String,
    private val repository: StreamingRepository,
    library: LibraryRepository,
    actions: RemoteActions,
) : RemoteViewModel(library, actions) {

    data class UiState(val loading: Boolean = true, val details: StreamDetails? = null, val error: String? = null)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState()
        viewModelScope.launch {
            try {
                _state.value = UiState(false, repository.streamDetails(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UiState(false, null, e.userMessage())
            }
        }
    }

    fun summary(): MediaSummary? = _state.value.details?.let {
        MediaSummary(it.url, it.id, ItemType.SONG, it.title, it.uploader, it.thumbnailUrl, it.durationSec)
    }
}

class CollectionViewModel(
    private val url: String,
    private val repository: StreamingRepository,
    private val library: LibraryRepository,
    actions: RemoteActions,
) : RemoteViewModel(library, actions) {

    data class UiState(
        val loading: Boolean = true,
        val collection: RemoteCollection? = null,
        val items: List<MediaSummary> = emptyList(),
        val nextPage: PageToken? = null,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val localPlaylistId: Long? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState()
        viewModelScope.launch {
            try {
                val c = repository.collection(url)
                val local = library.findPlaylistByUrl(c.url) ?: library.findPlaylistByUrl(url)
                _state.value = UiState(
                    loading = false,
                    collection = c,
                    items = c.firstPage.items,
                    nextPage = c.firstPage.nextPage,
                    localPlaylistId = local?.id,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UiState(loading = false, error = e.userMessage())
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        val c = s.collection ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val more = repository.collectionMore(c.url, page)
                _state.update {
                    it.copy(loadingMore = false, items = (it.items + more.items).distinctBy { i -> i.url }, nextPage = more.nextPage)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, nextPage = null) }
            }
        }
    }

    fun summary(): MediaSummary? = _state.value.collection?.let {
        MediaSummary(it.url, it.id, it.type, it.title, it.uploader, it.thumbnailUrl, itemCount = it.streamCount)
    }
}
