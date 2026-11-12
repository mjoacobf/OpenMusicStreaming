package me.mjoacobf.openmusicstreaming.core.extractor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.extractor.potoken.PoTokenProviderImpl
import me.mjoacobf.openmusicstreaming.core.model.ArtistProfile
import me.mjoacobf.openmusicstreaming.core.model.ArtistSection
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import me.mjoacobf.openmusicstreaming.core.model.PageToken
import me.mjoacobf.openmusicstreaming.core.model.PagedItems
import me.mjoacobf.openmusicstreaming.core.model.RemoteCollection
import me.mjoacobf.openmusicstreaming.core.model.SearchFilter
import me.mjoacobf.openmusicstreaming.core.model.StreamDetails
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.soundcloud.linkHandler.SoundcloudSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as Q
import java.util.Locale

/** Resultado de analizar un enlace pegado en el buscador. */
sealed interface LinkTarget {
    data class Stream(val url: String) : LinkTarget
    data class Collection(val url: String) : LinkTarget
}

/**
 * Fachada sobre NewPipeExtractor para YouTube Music, SoundCloud y Bandcamp: búsqueda,
 * sugerencias, detalle con recomendados y playlists/álbumes paginados. El servicio se deduce de
 * la URL (o del parámetro `service` en búsquedas), igual que hace NewPipe.
 */
class YoutubeService(
    private val context: Context,
    private val downloader: NewPipeDownloader,
    val resolver: StreamResolver,
) {
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

    /** Filtros de búsqueda del servicio (Bandcamp no tiene: mezcla pistas y álbumes). */
    private fun contentFilters(service: MusicService, filter: SearchFilter): List<String> = when (service) {
        MusicService.YOUTUBE -> listOf(
            when (filter) {
                SearchFilter.MUSIC_SONGS -> Q.MUSIC_SONGS
                SearchFilter.MUSIC_VIDEOS -> Q.MUSIC_VIDEOS
                SearchFilter.MUSIC_ALBUMS -> Q.MUSIC_ALBUMS
                SearchFilter.MUSIC_PLAYLISTS -> Q.MUSIC_PLAYLISTS
                SearchFilter.MUSIC_ARTISTS -> Q.MUSIC_ARTISTS
            },
        )

        MusicService.SOUNDCLOUD -> listOf(
            when (filter) {
                SearchFilter.MUSIC_PLAYLISTS, SearchFilter.MUSIC_ALBUMS -> SoundcloudSearchQueryHandlerFactory.PLAYLISTS
                SearchFilter.MUSIC_ARTISTS -> SoundcloudSearchQueryHandlerFactory.USERS
                else -> SoundcloudSearchQueryHandlerFactory.TRACKS
            },
        )

        MusicService.BANDCAMP -> emptyList()
    }

    private fun queryHandler(query: String, service: MusicService, filter: SearchFilter) =
        service.newPipe.searchQHFactory.fromQuery(query, contentFilters(service, filter), "")

    suspend fun search(query: String, service: MusicService, filter: SearchFilter): PagedItems =
        withContext(Dispatchers.IO) {
            val info = SearchInfo.getInfo(service.newPipe, queryHandler(query, service, filter))
            PagedItems(
                info.relatedItems.mapNotNull { it.toSummary(filter) },
                info.nextPage?.let(::PageToken),
            )
        }

    suspend fun searchMore(query: String, service: MusicService, filter: SearchFilter, page: PageToken): PagedItems =
        withContext(Dispatchers.IO) {
            val result = SearchInfo.getMoreItems(
                service.newPipe, queryHandler(query, service, filter), page.value as Page,
            )
            PagedItems(result.items.mapNotNull { it.toSummary(filter) }, result.nextPage?.let(::PageToken))
        }

    suspend fun suggestions(query: String, service: MusicService): List<String> = withContext(Dispatchers.IO) {
        runCatching { service.newPipe.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    /** Detecta si el texto es un enlace de YouTube, SoundCloud o Bandcamp (incluido contenido no listado). */
    fun parseLink(text: String): LinkTarget? {
        val url = text.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return when (runCatching { MusicService.ofUrl(url).newPipe.getLinkTypeByUrl(url) }.getOrNull()) {
            LinkType.STREAM -> LinkTarget.Stream(url)
            LinkType.PLAYLIST -> LinkTarget.Collection(url)
            else -> null
        }
    }

    suspend fun streamDetails(url: String): StreamDetails = withContext(Dispatchers.IO) {
        val id = itemId(url)
        val info = resolver.streamInfo(id)
        StreamDetails(
            id = id,
            url = info.url,
            title = cleanCollectionTitle(info.name),
            uploader = cleanArtist(info.uploaderName),
            uploaderUrl = info.uploaderUrl?.takeIf { it.isNotBlank() },
            thumbnailUrl = info.thumbnails.pickUrl(DETAIL_THUMB),
            durationSec = info.duration,
            viewCount = info.viewCount,
            description = info.description?.let { d ->
                if (d.type == org.schabi.newpipe.extractor.stream.Description.Type.HTML) {
                    android.text.Html.fromHtml(d.content, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
                } else {
                    d.content
                }
            }.orEmpty(),
            related = info.relatedItems.mapNotNull { it.toSummary() }.musicOnly(),
        )
    }

    /** Recomendados (para reproducción automática). */
    suspend fun related(videoId: String): List<MediaSummary> = withContext(Dispatchers.IO) {
        resolver.streamInfo(videoId).relatedItems
            .mapNotNull { it.toSummary() }
            .filter { it.isPlayable }
            .musicOnly()
    }

    suspend fun collection(url: String): RemoteCollection = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(MusicService.ofUrl(url).newPipe, url)
        val album = isAlbumUrl(url) || isAlbumUrl(info.url)
        RemoteCollection(
            url = info.url,
            id = info.id,
            type = if (album) ItemType.ALBUM else ItemType.PLAYLIST,
            title = cleanCollectionTitle(info.name),
            uploader = cleanArtist(info.uploaderName),
            thumbnailUrl = info.thumbnails.pickUrl(DETAIL_THUMB),
            streamCount = info.streamCount,
            firstPage = PagedItems(
                info.relatedItems.mapNotNull { it.toSummary(if (album) SearchFilter.MUSIC_SONGS else null) },
                info.nextPage?.let(::PageToken),
            ),
        )
    }

    suspend fun collectionMore(url: String, page: PageToken): PagedItems = withContext(Dispatchers.IO) {
        val result = PlaylistInfo.getMoreItems(MusicService.ofUrl(url).newPipe, url, page.value as Page)
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

    // region Artistas

    /** Perfil de un artista (canal): cabecera y las secciones que su servicio ofrece. */
    suspend fun artist(url: String): ArtistProfile = withContext(Dispatchers.IO) {
        val service = MusicService.ofUrl(url)
        val info = ChannelInfo.getInfo(service.newPipe, url)
        val byFilter = info.tabs.associateBy { it.contentFilters.firstOrNull().orEmpty() }
        val tabs = LinkedHashMap<ArtistSection, ListLinkHandler>()
        byFilter[ChannelTabs.TRACKS]?.let { tabs[ArtistSection.SONGS] = it }
        byFilter[ChannelTabs.ALBUMS]?.let { tabs[ArtistSection.ALBUMS] = it }
        byFilter[ChannelTabs.VIDEOS]?.let { tabs[ArtistSection.VIDEOS] = it }
        byFilter[ChannelTabs.PLAYLISTS]?.let { tabs[ArtistSection.PLAYLISTS] = it }
        // Servicios sin pestañas reconocibles: la primera hace de lista de canciones
        if (tabs.isEmpty() && service != MusicService.YOUTUBE) info.tabs.firstOrNull()?.let { tabs[ArtistSection.SONGS] = it }
        val sections = if (service == MusicService.YOUTUBE) {
            // En YouTube las canciones, álbumes y videos se buscan por nombre si el canal no los trae
            buildList {
                add(ArtistSection.SONGS); add(ArtistSection.ALBUMS); add(ArtistSection.VIDEOS)
                if (ArtistSection.PLAYLISTS in tabs) add(ArtistSection.PLAYLISTS)
            }
        } else {
            tabs.keys.toList()
        }
        ArtistProfile(
            url = info.url,
            name = cleanArtist(info.name),
            thumbnailUrl = info.avatars.pickUrl(DETAIL_THUMB),
            bannerUrl = info.banners.pickUrl(DETAIL_THUMB),
            subscriberCount = info.subscriberCount,
            description = info.description.orEmpty(),
            sections = sections,
            handle = tabs,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun ArtistProfile.tab(section: ArtistSection): ListLinkHandler? =
        (handle as Map<ArtistSection, ListLinkHandler>)[section]

    private fun ArtistSection.filter() = when (this) {
        ArtistSection.SONGS -> SearchFilter.MUSIC_SONGS
        ArtistSection.ALBUMS -> SearchFilter.MUSIC_ALBUMS
        ArtistSection.VIDEOS -> SearchFilter.MUSIC_VIDEOS
        ArtistSection.PLAYLISTS -> SearchFilter.MUSIC_PLAYLISTS
    }

    private fun ArtistSection.type() = when (this) {
        ArtistSection.SONGS -> ItemType.SONG
        ArtistSection.ALBUMS -> ItemType.ALBUM
        ArtistSection.VIDEOS -> ItemType.VIDEO
        ArtistSection.PLAYLISTS -> ItemType.PLAYLIST
    }

    /**
     * Contenido de una sección del artista. Sin [query] usa la pestaña del canal (o, en YouTube, la
     * búsqueda por nombre del artista); con [query] busca "artista + texto" y deja solo lo que
     * pertenece al artista, que es como se "busca dentro" de un perfil.
     */
    suspend fun artistItems(
        profile: ArtistProfile,
        section: ArtistSection,
        query: String,
        page: PageToken?,
    ): PagedItems = withContext(Dispatchers.IO) {
        val tab = profile.tab(section)
        val useTab = query.isBlank() && tab != null &&
            !(profile.service == MusicService.YOUTUBE && section == ArtistSection.SONGS)
        if (useTab && tab != null) {
            val nw = profile.service.newPipe
            if (page == null) {
                val info = ChannelTabInfo.getInfo(nw, tab)
                PagedItems(
                    info.relatedItems.mapNotNull { it.toSummary(section.filter()) }.filter { it.type == section.type() },
                    info.nextPage?.let(::PageToken),
                )
            } else {
                val result = ChannelTabInfo.getMoreItems(nw, tab, page.value as Page)
                PagedItems(
                    result.items.mapNotNull { it.toSummary(section.filter()) }.filter { it.type == section.type() },
                    result.nextPage?.let(::PageToken),
                )
            }
        } else {
            artistSearch(profile, section, query, page)
        }
    }

    private suspend fun artistSearch(
        profile: ArtistProfile,
        section: ArtistSection,
        query: String,
        page: PageToken?,
    ): PagedItems {
        val text = "${profile.name} $query".trim()
        val matches = ArrayList<MediaSummary>()
        var next = page
        var first = page == null
        var rounds = 0
        // Una página de búsqueda puede traer pocos resultados del artista: se leen hasta 3
        while ((first || next != null) && matches.size < 8 && rounds++ < 3) {
            val result = if (first) {
                search(text, profile.service, section.filter())
            } else {
                searchMore(text, profile.service, section.filter(), next!!)
            }
            first = false
            next = result.nextPage
            matches += result.items.filter { it.type == section.type() && it.belongsTo(profile.name) }
        }
        return PagedItems(matches.distinctBy { it.url }, next)
    }

    private fun MediaSummary.belongsTo(artist: String): Boolean =
        subtitle.contains(artist, ignoreCase = true) || (subtitle.isNotBlank() && artist.contains(subtitle, ignoreCase = true))

    /** Busca un artista por nombre y devuelve la URL de su perfil (para reproducciones sin enlace guardado). */
    suspend fun findArtistUrl(name: String, service: MusicService): String? {
        val first = name.split(", ", " & ", " x ", " feat. ", " ft. ").first().trim()
        if (first.isBlank()) return null
        val items = search(first, service, SearchFilter.MUSIC_ARTISTS).items.filter { it.type == ItemType.CHANNEL }
        return (items.firstOrNull { it.title.equals(first, ignoreCase = true) } ?: items.firstOrNull())?.url
    }

    /** Busca el álbum de una canción (por nombre de álbum o, si no se conoce, el de la canción). */
    suspend fun findAlbumUrl(albumOrTitle: String, artist: String, service: MusicService): String? {
        val firstArtist = artist.split(", ", " & ").first().trim()
        val items = search("$firstArtist $albumOrTitle".trim(), service, SearchFilter.MUSIC_ALBUMS).items
            .filter { it.type == ItemType.ALBUM }
        return (items.firstOrNull { it.subtitle.contains(firstArtist, ignoreCase = true) } ?: items.firstOrNull())?.url
    }

    // endregion

    private companion object {
        /** Ancho de las portadas en cabeceras de álbum/detalle (nítidas a pantalla completa). */
        const val DETAIL_THUMB = 1080
    }
}
