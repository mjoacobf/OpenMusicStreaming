package me.mjoacobf.openmusicstreaming.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.PlaylistEntry
import me.mjoacobf.openmusicstreaming.core.data.PlaylistRecord
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.SongRecord
import me.mjoacobf.openmusicstreaming.core.extractor.YoutubeService
import me.mjoacobf.openmusicstreaming.core.model.DownloadStatus
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.Song
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Descarga y sincroniza playlists/álbumes, con la misma lógica que
 * youtube_music_playlist_downloader:
 *  - descarga las canciones nuevas o cuyo archivo falta;
 *  - reordena según el orden actual en YouTube;
 *  - las canciones eliminadas/privadas en YouTube se conservan y pasan al final;
 *  - si la playlist completa desaparece, la copia local sigue intacta.
 * Una canción compartida entre playlists se guarda una sola vez (ahorro de espacio).
 */
class PlaylistSyncer(
    private val youtube: YoutubeService,
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val storage: SafStorage,
    private val downloader: SongDownloader,
    private val tracker: DownloadTracker,
) {

    class NoFolderException : Exception("Elige una carpeta de descargas en Ajustes")

    suspend fun sync(
        url: String,
        newTreeUri: String?,
        newAutoUpdate: Boolean,
        /** `fraction` = avance global 0..1 (canciones completas + parte de las que se están bajando). */
        onProgress: (done: Int, total: Int, fraction: Float) -> Unit = { _, _, _ -> },
    ) {
        val settings = settingsRepository.current()
        val key = url
        tracker.set(DownloadStatus(key, "Obteniendo playlist…", DownloadStatus.State.RUNNING, 0f))

        val existingBefore = library.findPlaylistByUrl(url)
        val (collection, items) = try {
            youtube.collectionAllItems(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ContentNotAvailableException) {
            existingBefore?.let {
                library.markPlaylistSynced(it.id, "La playlist ya no está disponible en YouTube", remoteAvailable = false)
            }
            tracker.set(DownloadStatus(key, existingBefore?.title ?: url, DownloadStatus.State.FAILED, 0f, message = e.message))
            throw e
        }

        val existing = existingBefore ?: library.findPlaylistByUrl(collection.url)
        val treeUri = existing?.treeUri ?: newTreeUri ?: settings.downloadTreeUri ?: throw NoFolderException()
        if (!storage.hasPermission(treeUri)) throw NoFolderException()
        val folderName = existing?.folderName ?: sanitizeFileName(collection.title)
        val folder = storage.ensureFolder(existing?.folderUri, treeUri, folderName)

        val isAlbum = collection.type == ItemType.ALBUM
        val playlistId = library.createOrUpdatePlaylist(
            PlaylistRecord(
                remoteUrl = existing?.remoteUrl ?: collection.url,
                remoteId = collection.id,
                title = collection.title,
                uploader = collection.uploader,
                thumbnailUrl = downloader.cachePlaylistCover(collection.id, collection.thumbnailUrl)
                    ?: collection.thumbnailUrl,
                type = collection.type,
                autoUpdate = newAutoUpdate,
                treeUri = treeUri,
                folderName = folderName,
                folderUri = folder.uri.toString(),
            ),
        )
        library.updatePlaylistFolder(playlistId, folder.uri.toString())

        val previous = library.getPlaylistSongs(playlistId)
        val names = storage.listNames(folder)
        val playable = items.filter { it.isPlayable }
        val total = playable.size
        val done = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val inflight = java.util.concurrent.ConcurrentHashMap<Int, Float>()
        fun overall(): Float =
            if (total == 0) 1f else ((done.get() + inflight.values.sum()) / total).coerceIn(0f, 1f)
        tracker.set(DownloadStatus(key, collection.title, DownloadStatus.State.RUNNING, 0f, 0, total))

        val downloadedSnapshot = DownloadedSnapshot(library.downloadedSongs())
        val semaphore = Semaphore(settings.maxParallelDownloads.coerceIn(1, 4))
        val available: List<String?> = coroutineScope {
            playable.mapIndexed { index, item ->
                async {
                    semaphore.withPermit {
                        val usedId = ensureSong(
                            item = item,
                            track = TrackContext(collection.title, collection.uploader, index + 1, isAlbum),
                            folder = folder,
                            names = names,
                            library = downloadedSnapshot,
                        ) { p ->
                            inflight[index] = p
                            onProgress(done.get(), total, overall())
                            tracker.set(
                                DownloadStatus(key, collection.title, DownloadStatus.State.RUNNING, overall(), done.get(), total),
                            )
                        }
                        if (usedId == null) failures.incrementAndGet()
                        inflight.remove(index)
                        val d = done.incrementAndGet()
                        onProgress(d, total, overall())
                        tracker.set(
                            DownloadStatus(key, collection.title, DownloadStatus.State.RUNNING, overall(), d, total),
                        )
                        usedId
                    }
                }
            }.awaitAll()
        }

        // Nuevo orden: el de YouTube, y al final las que ya no están en la playlist remota.
        // Si una canción ya existía (mismo ID o mismo contenido), se enlaza al archivo existente.
        val entries = mutableListOf<PlaylistEntry>()
        val usedIds = HashSet<String>()
        available.forEach { id ->
            if (id != null && usedIds.add(id)) entries += PlaylistEntry(id, entries.size, false)
        }
        previous
            .filter { it.song.videoId !in usedIds && it.song.localUri != null }
            .sortedBy { it.position }
            .forEach { entries += PlaylistEntry(it.song.videoId, entries.size, true) }
        library.replacePlaylistEntries(playlistId, entries)

        if (settings.writeM3uPlaylists) {
            runCatching {
                val songs = library.getPlaylistSongs(playlistId).filter { it.song.localUri != null }
                storage.writeM3u(
                    folder,
                    collection.title,
                    songs.map { "${it.song.artist} - ${it.song.title}" to it.song.durationSec },
                    songs.map { it.song.localUri!! },
                )
            }
        }

        val failed = failures.get()
        val error = if (failed > 0) "$failed canciones no se pudieron descargar" else null
        library.markPlaylistSynced(playlistId, error)
        tracker.set(
            DownloadStatus(
                key, collection.title,
                if (failed == 0) DownloadStatus.State.SUCCEEDED else DownloadStatus.State.FAILED,
                1f, done.get(), total, error,
            ),
        )
    }

    /**
     * Garantiza que la canción esté descargada sin duplicarla. Devuelve el ID de la canción
     * local usada (puede ser otra versión equivalente ya descargada) o null si falló.
     */
    private suspend fun ensureSong(
        item: MediaSummary,
        track: TrackContext,
        folder: androidx.documentfile.provider.DocumentFile,
        names: MutableSet<String>,
        library: DownloadedSnapshot,
        onSongProgress: (Float) -> Unit = {},
    ): String? {
        val videoId = item.id
        val song = this.library.getSong(videoId)
        if (song?.localUri != null) {
            if (storage.exists(song.localUri!!)) {
                this.library.setSongRemoteAvailable(videoId, true)
                return videoId
            }
            this.library.clearMissingFile(videoId) // el usuario borró el archivo: se vuelve a bajar
        }
        library.findEquivalent(item.title, item.subtitle, item.durationSec, exclude = videoId)?.let { return it }

        val settings = settingsRepository.current()
        return try {
            val record: SongRecord = downloader.download(
                videoId, track, folder, names,
                settings.downloadAudioQuality, settings.downloadFormat, settings.embedCoverArt,
                onSongProgress,
            )
            this.library.upsertSong(record)
            library.add(record)
            videoId
        } catch (e: CancellationException) {
            throw e
        } catch (e: ContentNotAvailableException) {
            if (song != null) this.library.setSongRemoteAvailable(videoId, false)
            null
        } catch (e: Exception) {
            android.util.Log.w("PlaylistSyncer", "Failed to download $videoId", e)
            null
        }
    }

    /**
     * Índice de canciones descargadas por "artista|título" normalizados, para no descargar
     * dos veces la misma canción publicada con distinto ID (p. ej. video oficial y audio "Topic").
     */
    private inner class DownloadedSnapshot(songs: List<Song>) {
        private val byKey = HashMap<String, MutableList<Pair<String, Long>>>()

        init {
            songs.forEach { put(it.videoId, it.artist, it.title, it.durationSec) }
        }

        private fun put(id: String, artist: String, title: String, duration: Long) = synchronized(byKey) {
            byKey.getOrPut(key(artist, title)) { mutableListOf() }.add(id to duration)
        }

        fun add(record: SongRecord) = put(record.videoId, record.artist, record.title, record.durationSec)

        suspend fun findEquivalent(rawTitle: String, uploader: String, durationSec: Long, exclude: String): String? {
            val (title, artist) = TrackNames.from(rawTitle, uploader)
            val candidates = synchronized(byKey) { byKey[key(artist, title)]?.toList() }.orEmpty()
            for ((id, duration) in candidates) {
                if (id == exclude) continue
                if (durationSec > 0 && duration > 0 && kotlin.math.abs(durationSec - duration) > 4) continue
                val uri = this@PlaylistSyncer.library.getSong(id)?.localUri ?: continue
                if (storage.exists(uri)) return id
            }
            return null
        }

        private fun key(artist: String, title: String) = normalize(artist) + "|" + normalize(title)

        private fun normalize(s: String): String =
            java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^\\p{L}\\p{N}]+"), "")
    }

    /** Descarga una canción suelta a la subcarpeta "Canciones" (si no existe ya). */
    suspend fun downloadSingle(
        videoId: String,
        title: String,
        newTreeUri: String?,
        onProgress: (Float) -> Unit = {},
    ) {
        val settings = settingsRepository.current()
        val treeUri = newTreeUri ?: settings.downloadTreeUri ?: throw NoFolderException()
        if (!storage.hasPermission(treeUri)) throw NoFolderException()
        tracker.set(DownloadStatus(videoId, title, DownloadStatus.State.RUNNING, 0f))
        val existing = library.getSong(videoId)
        if (existing?.localUri != null && storage.exists(existing.localUri!!)) {
            tracker.set(DownloadStatus(videoId, title, DownloadStatus.State.SUCCEEDED, 1f, message = "Ya estaba descargada"))
            return
        }
        // Misma canción ya descargada con otro ID (otra versión del mismo audio)
        val info = youtube.resolver.streamInfo(videoId)
        val equivalent = DownloadedSnapshot(library.downloadedSongs())
            .findEquivalent(info.name, info.uploaderName.orEmpty(), info.duration, exclude = videoId)
        if (equivalent != null) {
            tracker.set(DownloadStatus(videoId, title, DownloadStatus.State.SUCCEEDED, 1f, message = "Ya estaba descargada"))
            return
        }
        val folder = storage.ensureFolder(null, treeUri, SINGLES_FOLDER)
        try {
            val record = downloader.download(
                videoId,
                TrackContext(album = "", albumArtist = "", trackNumber = null, isAlbum = false),
                folder,
                storage.listNames(folder),
                settings.downloadAudioQuality,
                settings.downloadFormat,
                settings.embedCoverArt,
            ) { p ->
                onProgress(p)
                tracker.set(DownloadStatus(videoId, title, DownloadStatus.State.RUNNING, p))
            }
            library.upsertSong(record)
            tracker.set(DownloadStatus(videoId, record.title, DownloadStatus.State.SUCCEEDED, 1f))
        } catch (e: CancellationException) {
            tracker.remove(videoId)
            throw e
        } catch (e: Exception) {
            tracker.set(DownloadStatus(videoId, title, DownloadStatus.State.FAILED, 0f, message = e.message))
            throw e
        }
    }

    companion object {
        const val SINGLES_FOLDER = "Canciones"
    }
}
