package me.mjoacobf.openmusicstreaming.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.mjoacobf.openmusicstreaming.core.database.AppDatabase
import me.mjoacobf.openmusicstreaming.core.database.SearchHistoryEntity
import me.mjoacobf.openmusicstreaming.core.extractor.LinkTarget
import me.mjoacobf.openmusicstreaming.core.extractor.YoutubeService
import me.mjoacobf.openmusicstreaming.core.model.ArtistProfile
import me.mjoacobf.openmusicstreaming.core.model.ArtistSection
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import me.mjoacobf.openmusicstreaming.core.model.PageToken
import me.mjoacobf.openmusicstreaming.core.model.PagedItems
import me.mjoacobf.openmusicstreaming.core.model.RemoteCollection
import me.mjoacobf.openmusicstreaming.core.model.SearchFilter
import me.mjoacobf.openmusicstreaming.core.model.StreamDetails

sealed interface ParsedLink {
    data class Stream(val url: String) : ParsedLink
    data class Collection(val url: String) : ParsedLink
}

/** Contenido remoto (búsqueda estilo NewPipe, enlaces directos, recomendados). */
class StreamingRepository(
    private val youtube: YoutubeService,
    db: AppDatabase,
) {
    private val historyDao = db.searchHistoryDao()

    suspend fun search(query: String, service: MusicService, filter: SearchFilter): PagedItems =
        youtube.search(query, service, filter)

    suspend fun searchMore(query: String, service: MusicService, filter: SearchFilter, page: PageToken): PagedItems =
        youtube.searchMore(query, service, filter, page)

    suspend fun suggestions(query: String, service: MusicService): List<String> =
        youtube.suggestions(query, service)

    fun parseLink(text: String): ParsedLink? = when (val t = youtube.parseLink(text)) {
        is LinkTarget.Stream -> ParsedLink.Stream(t.url)
        is LinkTarget.Collection -> ParsedLink.Collection(t.url)
        null -> null
    }

    suspend fun streamDetails(url: String): StreamDetails = youtube.streamDetails(url)

    suspend fun related(videoId: String): List<MediaSummary> = youtube.related(videoId)

    suspend fun collection(url: String): RemoteCollection = youtube.collection(url)

    suspend fun collectionMore(url: String, page: PageToken): PagedItems = youtube.collectionMore(url, page)

    // Artistas

    suspend fun artist(url: String): ArtistProfile = youtube.artist(url)

    suspend fun artistItems(profile: ArtistProfile, section: ArtistSection, query: String, page: PageToken?): PagedItems =
        youtube.artistItems(profile, section, query, page)

    /** URL del perfil de un artista a partir de su nombre (para canciones sin enlace guardado). */
    suspend fun findArtistUrl(name: String, service: MusicService): String? = youtube.findArtistUrl(name, service)

    /** URL del álbum de una canción (búsqueda por nombre de álbum, o de canción si no se conoce). */
    suspend fun findAlbumUrl(albumOrTitle: String, artist: String, service: MusicService): String? =
        youtube.findAlbumUrl(albumOrTitle, artist, service)

    fun history(prefix: String, enabled: Boolean): Flow<List<String>> =
        if (!enabled) flowOf(emptyList()) else historyDao.observe(prefix).map { l -> l.map { it.query } }

    suspend fun saveHistory(query: String) =
        historyDao.upsert(SearchHistoryEntity(query.trim(), System.currentTimeMillis()))

    suspend fun deleteHistory(query: String) = historyDao.delete(query)

    suspend fun clearHistory() = historyDao.clear()

    fun setContentCountry(code: String) = youtube.setContentCountry(code)
}
