package me.mjoacobf.openmusicstreaming.feature.streaming

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.BookmarkRepository
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.UserPlaylistRepository
import me.mjoacobf.openmusicstreaming.core.download.DownloadManager
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.media.toMediaItem
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary

/** Acciones comunes sobre elementos remotos, compartidas por los ViewModels de streaming. */
class RemoteActions(
    private val player: PlayerConnection,
    private val bookmarks: BookmarkRepository,
    private val downloads: DownloadManager,
    private val settings: SettingsRepository,
    private val userPlaylists: UserPlaylistRepository,
) {
    val bookmarkedUrls = bookmarks.observeUrls()
    val settingsFlow = settings.settings
    val playlistsFlow = userPlaylists.observeAll()

    fun addToPlaylist(scope: CoroutineScope, playlistId: Long, item: MediaSummary) = scope.launch {
        userPlaylists.add(playlistId, listOf(item))
    }

    fun createPlaylistWith(scope: CoroutineScope, name: String, item: MediaSummary) = scope.launch {
        userPlaylists.add(userPlaylists.create(name), listOf(item))
    }

    fun play(item: MediaSummary, asVideo: Boolean = false) = player.playRemote(listOf(item), 0, asVideo)

    fun playAll(items: List<MediaSummary>, index: Int, asVideo: Boolean = false) =
        player.playRemote(items, index, asVideo)

    fun playNext(item: MediaSummary) = player.playNext(item.toMediaItem())

    fun addToQueue(item: MediaSummary) = player.addToQueue(item.toMediaItem())

    fun toggleBookmark(scope: CoroutineScope, item: MediaSummary, bookmarked: Boolean) = scope.launch {
        bookmarks.toggle(item, bookmarked)
    }

    /** Guarda la carpeta/calidad elegidas como predeterminadas y encola la descarga. */
    fun download(scope: CoroutineScope, item: MediaSummary, choice: DownloadChoice) = scope.launch {
        settings.update {
            it.copy(
                downloadTreeUri = choice.treeUri,
                downloadFormat = choice.format,
                downloadAudioQuality = choice.quality,
            )
        }
        if (item.isCollection) {
            downloads.downloadCollection(item.url, item.title, choice.treeUri, choice.autoUpdate)
        } else {
            downloads.downloadSong(item.id, item.title, choice.treeUri)
        }
    }
}
