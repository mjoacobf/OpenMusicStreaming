package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.ParsedLink
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.model.AppSettings
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.PageToken
import me.mjoacobf.openmusicstreaming.core.model.SearchFilter

data class SearchUiState(
    val submittedQuery: String = "",
    val filter: SearchFilter = SearchFilter.MUSIC_SONGS,
    val results: List<MediaSummary> = emptyList(),
    val nextPage: PageToken? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

sealed interface SearchEvent {
    data class OpenStream(val url: String) : SearchEvent
    data class OpenCollection(val url: String) : SearchEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: StreamingRepository,
    library: LibraryRepository,
    val actions: RemoteActions,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SearchEvent> = _events

    val settings: StateFlow<AppSettings> =
        actions.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val bookmarked: StateFlow<Set<String>> =
        actions.bookmarkedUrls.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedIds: StateFlow<Set<String>> =
        library.observeDownloadedIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Sugerencias de YouTube + historial local mientras se escribe. */
    val suggestions: StateFlow<List<String>> = combine(_query, settings) { q, s -> q to s.saveSearchHistory }
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { (q, historyEnabled) ->
            repository.history(q.trim(), historyEnabled).map { history ->
                history to q
            }
        }
        .flatMapLatest { (history, q) ->
            flow {
                emit(history)
                if (q.isNotBlank() && repository.parseLink(q) == null) {
                    val remote = repository.suggestions(q)
                    emit((history + remote).distinct().take(12))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun setFilter(filter: SearchFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        if (_state.value.submittedQuery.isNotBlank()) search(_state.value.submittedQuery)
    }

    /** Busca texto o, si es un enlace (incluido contenido oculto/no listado), lo abre directamente. */
    fun search(text: String = _query.value) {
        val q = text.trim()
        if (q.isEmpty()) return
        _query.value = q
        when (val link = repository.parseLink(q)) {
            is ParsedLink.Stream -> {
                _events.tryEmit(SearchEvent.OpenStream(link.url)); return
            }
            is ParsedLink.Collection -> {
                _events.tryEmit(SearchEvent.OpenCollection(link.url)); return
            }
            null -> Unit
        }
        searchJob?.cancel()
        _state.update { it.copy(submittedQuery = q, loading = true, error = null, results = emptyList(), nextPage = null) }
        searchJob = viewModelScope.launch {
            if (settings.value.saveSearchHistory) runCatching { repository.saveHistory(q) }
            try {
                val page = repository.search(q, _state.value.filter)
                _state.update { it.copy(loading = false, results = page.items, nextPage = page.nextPage) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.loadingMore || s.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val more = repository.searchMore(s.submittedQuery, s.filter, page)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        results = (it.results + more.items).distinctBy { r -> r.url },
                        nextPage = more.nextPage,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, nextPage = null) }
            }
        }
    }

    fun deleteHistory(query: String) = viewModelScope.launch { repository.deleteHistory(query) }
}

internal fun Throwable.userMessage(): String = when {
    this is java.net.UnknownHostException || this is java.net.SocketTimeoutException ->
        "Sin conexión a internet"
    this::class.simpleName == "ContentNotAvailableException" -> "Contenido no disponible"
    this::class.simpleName == "ReCaptchaException" -> "YouTube solicitó un captcha. Intenta más tarde."
    else -> message ?: "Error desconocido"
}
