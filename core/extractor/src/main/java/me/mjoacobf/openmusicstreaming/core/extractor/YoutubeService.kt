package me.mjoacobf.openmusicstreaming.core.extractor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.extractor.potoken.PoTokenProviderImpl
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.PageToken
import me.mjoacobf.openmusicstreaming.core.model.PagedItems
import me.mjoacobf.openmusicstreaming.core.model.RemoteCollection
import me.mjoacobf.openmusicstreaming.core.model.SearchFilter
import me.mjoacobf.openmusicstreaming.core.model.StreamDetails
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as Q
import java.util.Locale

/** Resultado de analizar un enlace pegado en el buscador. */
sealed interface LinkTarget {
    data class Stream(val url: String) : LinkTarget
    data class Collection(val url: String) : LinkTarget
}

/**
 * Fachada sobre NewPipeExtractor para YouTube / YouTube Music: búsqueda,
 * sugerencias, detalle con recomendados y playlists/álbumes paginados.
 */
class YoutubeService(
    private val context: Context,
    private val downloader: NewPipeDownloader,
    val resolver: StreamResolver,
) {
    private val service get() = ServiceList.YouTube

    fun init(countryCode: String) {
        val locale = Locale.getDefault()
        val country = countryCode.ifBlank { locale.country.ifBlank { "US" } }
        NewPipe.init(downloader, Localization.fromLocale(locale), ContentCountry(country))
        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl(context.applicationContext, downloader))
    }

    fun setContentCountry(countryCode: String) {
        val country = countryCode.ifBlank { Locale.getDefault().country.ifBlank { "US" } }
        NewPipe.setPreferredContentCountry(ContentCountry(country))
    }

    private fun SearchFilter.contentFilter(): String = when (this) {
        SearchFilter.MUSIC_SONGS -> Q.MUSIC_SONGS
        SearchFilter.MUSIC_VIDEOS -> Q.MUSIC_VIDEOS
        SearchFilter.MUSIC_ALBUMS -> Q.MUSIC_ALBUMS
        SearchFilter.MUSIC_PLAYLISTS -> Q.MUSIC_PLAYLISTS
        SearchFilter.VIDEOS -> Q.VIDEOS
        SearchFilter.PLAYLISTS -> Q.PLAYLISTS
        SearchFilter.ALL -> Q.ALL
    }

    private fun queryHandler(query: String, filter: SearchFilter) =
        service.searchQHFactory.fromQuery(query, listOf(filter.contentFilter()), "")

    suspend fun search(query: String, filter: SearchFilter): PagedItems = withContext(Dispatchers.IO) {
        val info = SearchInfo.getInfo(service, queryHandler(query, filter))
        PagedItems(
            info.relatedItems.mapNotNull { it.toSummary(filter) },
            info.nextPage?.let(::PageToken),
        )
    }

    suspend fun searchMore(query: String, filter: SearchFilter, page: PageToken): PagedItems =
        withContext(Dispatchers.IO) {
            val result = SearchInfo.getMoreItems(service, queryHandler(query, filter), page.value as Page)
            PagedItems(result.items.mapNotNull { it.toSummary(filter) }, result.nextPage?.let(::PageToken))
        }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { service.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    /** Detecta si el texto es un enlace de YouTube (incluido contenido oculto / no listado). */
    fun parseLink(text: String): LinkTarget? {
        val url = text.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return when (runCatching { service.getLinkTypeByUrl(url) }.getOrNull()) {
            LinkType.STREAM -> LinkTarget.Stream(url)
            LinkType.PLAYLIST -> LinkTarget.Collection(url)
            else -> null
        }
    }

    suspend fun streamDetails(url: String): StreamDetails = withContext(Dispatchers.IO) {
        val id = videoIdFromUrl(url) ?: error("URL de video no válida")
        val info = resolver.streamInfo(id)
        StreamDetails(
            id = id,
            url = info.url,
            title = cleanCollectionTitle(info.name),
            uploader = cleanArtist(info.uploaderName),
            thumbnailUrl = info.thumbnails.pickUrl(720),
            durationSec = info.duration,
            viewCount = info.viewCount,
            description = info.description?.let { d ->
                if (d.type == org.schabi.newpipe.extractor.stream.Description.Type.HTML) {
                    android.text.Html.fromHtml(d.content, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
                } else {
                    d.content
                }
            }.orEmpty(),
            related = info.relatedItems.mapNotNull { it.toSummary() },
        )
    }

    /** Recomendados (para reproducción automática). */
    suspend fun related(videoId: String): List<MediaSummary> = withContext(Dispatchers.IO) {
        resolver.streamInfo(videoId).relatedItems
            .mapNotNull { it.toSummary() }
            .filter { it.isPlayable }
    }

    suspend fun collection(url: String): RemoteCollection = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(service, url)
        val album = isAlbumUrl(url) || isAlbumUrl(info.url)
        RemoteCollection(
            url = info.url,
            id = info.id,
            type = if (album) ItemType.ALBUM else ItemType.PLAYLIST,
            title = cleanCollectionTitle(info.name),
            uploader = cleanArtist(info.uploaderName),
            thumbnailUrl = info.thumbnails.pickUrl(480),
            streamCount = info.streamCount,
            firstPage = PagedItems(
                info.relatedItems.mapNotNull { it.toSummary(if (album) SearchFilter.MUSIC_SONGS else null) },
                info.nextPage?.let(::PageToken),
            ),
        )
    }

    suspend fun collectionMore(url: String, page: PageToken): PagedItems = withContext(Dispatchers.IO) {
        val result = PlaylistInfo.getMoreItems(service, url, page.value as Page)
        PagedItems(result.items.mapNotNull { it.toSummary() }, result.nextPage?.let(::PageToken))
    }

    /** Descarga todas las páginas de una playlist (para descargar/sincronizar). */
    suspend fun collectionAllItems(url: String): Pair<RemoteCollection, List<MediaSummary>> {
        val collection = collection(url)
        val items = collection.firstPage.items.toMutableList()
        var next = collection.firstPage.nextPage
        while (next != null) {
            val page = collectionMore(collection.url, next)
            items += page.items
            next = page.nextPage
        }
        return collection to items.distinctBy { it.id }
    }
}
