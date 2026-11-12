package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.model.ArtistProfile
import me.mjoacobf.openmusicstreaming.core.model.ArtistSection
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.PageToken

/**
 * Perfil de un artista: cabecera, secciones (canciones, álbumes, videos, playlists) y búsqueda
 * dentro del contenido del artista (la consulta se aplica a la sección elegida).
 */
class ArtistViewModel(
    private val url: String,
    private val repository: StreamingRepository,
    library: LibraryRepository,
    actions: RemoteActions,
) : RemoteViewModel(library, actions) {

    data class UiState(
        val loadingProfile: Boolean = true,
        val profile: ArtistProfile? = null,
        val profileError: String? = null,
        val section: ArtistSection = ArtistSection.SONGS,
        val query: String = "",
        val items: List<MediaSummary> = emptyList(),
        val nextPage: PageToken? = null,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val listError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var listJob: Job? = null

    init {
        loadProfile()
    }

    fun loadProfile() {
        _state.value = UiState()
        viewModelScope.launch {
            try {
                val profile = repository.artist(url)
                _state.update {
                    it.copy(
                        loadingProfile = false,
                        profile = profile,
                        section = profile.sections.firstOrNull() ?: ArtistSection.SONGS,
                    )
                }
                loadSection()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingProfile = false, profileError = e.userMessage()) }
            }
        }
    }

    fun selectSection(section: ArtistSection) {
        if (section == _state.value.section) return
        _state.update { it.copy(section = section) }
        loadSection()
    }

    /** Busca dentro del artista (vacío = vuelve al contenido completo de la sección). */
    fun search(query: String) {
        val q = query.trim()
        if (q == _state.value.query) return
        _state.update { it.copy(query = q) }
        loadSection()
    }

    fun loadSection() {
        val s = _state.value
        val profile = s.profile ?: return
        listJob?.cancel()
        _state.update { it.copy(loading = true, items = emptyList(), nextPage = null, listError = null, loadingMore = false) }
        listJob = viewModelScope.launch {
            try {
                val page = repository.artistItems(profile, s.section, s.query, null)
                _state.update { it.copy(loading = false, items = page.items, nextPage = page.nextPage) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, listError = e.userMessage()) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val profile = s.profile ?: return
        val page = s.nextPage ?: return
        if (s.loadingMore || s.loading) return
        _state.update { it.copy(loadingMore = true) }
        listJob = viewModelScope.launch {
            try {
                val more = repository.artistItems(profile, s.section, s.query, page)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        items = (it.items + more.items).distinctBy { i -> i.url },
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
}
