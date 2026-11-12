package me.mjoacobf.openmusicstreaming.core.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.database.AppDatabase
import me.mjoacobf.openmusicstreaming.core.database.PlaylistEntity
import me.mjoacobf.openmusicstreaming.core.database.PlaylistSongEntity
import me.mjoacobf.openmusicstreaming.core.database.SongEntity
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.LocalPlaylist
import me.mjoacobf.openmusicstreaming.core.model.PlaylistSong
import me.mjoacobf.openmusicstreaming.core.model.Song

/** Datos para registrar/actualizar una canción descargada. */
data class SongRecord(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val localUri: String?,
    val fileName: String?,
    val fileSizeBytes: Long,
)

data class PlaylistRecord(
    val remoteUrl: String,
    val remoteId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val type: ItemType,
    val autoUpdate: Boolean,
    val treeUri: String,
    val folderName: String,
    val folderUri: String?,
)

data class PlaylistEntry(val videoId: String, val position: Int, val missingFromRemote: Boolean)

/** Info interna de una playlist necesaria para sincronizarla. */
data class PlaylistStorage(
    val id: Long,
    val remoteUrl: String,
    val title: String,
    val type: ItemType,
    val treeUri: String,
    val folderName: String,
    val folderUri: String?,
)

/** Biblioteca local: canciones y playlists descargadas. */
class LibraryRepository(
    private val context: Context,
    db: AppDatabase,
) {
    private val songDao = db.songDao()
    private val playlistDao = db.playlistDao()

    fun observePlaylists(): Flow<List<LocalPlaylist>> =
        playlistDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observePlaylist(id: Long): Flow<LocalPlaylist?> =
        playlistDao.observe(id).map { it?.toModel() }

    fun observePlaylistSongs(id: Long): Flow<List<PlaylistSong>> =
        playlistDao.observeSongs(id).map { rows -> rows.map { it.toModel() } }

    fun observeDownloadedSongs(): Flow<List<Song>> =
        songDao.observeDownloaded().map { list -> list.map { it.toModel() } }

    fun observeDownloadedIds(): Flow<Set<String>> =
        songDao.observeDownloadedIds().map { it.toSet() }

    suspend fun getSong(videoId: String): Song? = songDao.get(videoId)?.toModel()

    suspend fun downloadedSongs(): List<Song> = songDao.getDownloaded().map { it.toModel() }

    suspend fun getPlaylistSongs(id: Long): List<PlaylistSong> =
        playlistDao.getSongs(id).map { it.toModel() }

    suspend fun setAutoUpdate(id: Long, enabled: Boolean) = playlistDao.setAutoUpdate(id, enabled)

    suspend fun findPlaylistByUrl(url: String): PlaylistStorage? =
        playlistDao.getByUrl(url)?.toStorage()

    suspend fun getPlaylistStorage(id: Long): PlaylistStorage? = playlistDao.get(id)?.toStorage()

    suspend fun autoUpdatePlaylists(): List<PlaylistStorage> =
        playlistDao.getAutoUpdate().map { it.toStorage() }

    suspend fun allPlaylists(): List<PlaylistStorage> = playlistDao.getAllOnce().map { it.toStorage() }

    private fun PlaylistEntity.toStorage() = PlaylistStorage(
        id = id,
        remoteUrl = remoteUrl,
        title = title,
        type = runCatching { ItemType.valueOf(type) }.getOrDefault(ItemType.PLAYLIST),
        treeUri = treeUri,
        folderName = folderName,
        folderUri = folderUri,
    )

    // region Escritura (usada por el gestor de descargas)

    suspend fun createOrUpdatePlaylist(record: PlaylistRecord): Long {
        val existing = playlistDao.getByUrl(record.remoteUrl)
        val entity = PlaylistEntity(
            id = existing?.id ?: 0,
            remoteUrl = record.remoteUrl,
            remoteId = record.remoteId,
            title = record.title,
            uploader = record.uploader,
            thumbnailUrl = record.thumbnailUrl ?: existing?.thumbnailUrl,
            type = record.type.name,
            autoUpdate = existing?.autoUpdate ?: record.autoUpdate,
            lastSyncedAt = existing?.lastSyncedAt ?: 0,
            remoteAvailable = true,
            treeUri = existing?.treeUri ?: record.treeUri,
            folderName = existing?.folderName ?: record.folderName,
            folderUri = existing?.folderUri ?: record.folderUri,
            lastError = existing?.lastError,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        return if (existing == null) playlistDao.insert(entity) else {
            playlistDao.upsert(entity)
            existing.id
        }
    }

    suspend fun updatePlaylistFolder(id: Long, folderUri: String) {
        val p = playlistDao.get(id) ?: return
        playlistDao.upsert(p.copy(folderUri = folderUri))
    }

    suspend fun markPlaylistSynced(id: Long, error: String?, remoteAvailable: Boolean = true) {
        val p = playlistDao.get(id) ?: return
        playlistDao.upsert(
            p.copy(
                lastSyncedAt = if (error == null) System.currentTimeMillis() else p.lastSyncedAt,
                lastError = error,
                remoteAvailable = remoteAvailable,
            ),
        )
    }

    suspend fun replacePlaylistEntries(id: Long, entries: List<PlaylistEntry>) {
        playlistDao.replaceSongs(
            id,
            entries.map { PlaylistSongEntity(id, it.videoId, it.position, it.missingFromRemote) },
        )
    }

    suspend fun upsertSong(record: SongRecord) {
        val existing = songDao.get(record.videoId)
        songDao.upsert(
            SongEntity(
                videoId = record.videoId,
                title = record.title,
                artist = record.artist,
                album = record.album,
                durationSec = record.durationSec,
                thumbnailUrl = record.thumbnailUrl ?: existing?.thumbnailUrl,
                localUri = record.localUri ?: existing?.localUri,
                fileName = record.fileName ?: existing?.fileName,
                fileSizeBytes = if (record.localUri != null) record.fileSizeBytes else existing?.fileSizeBytes ?: 0,
                downloadedAt = if (record.localUri != null && existing?.localUri == null) {
                    System.currentTimeMillis()
                } else {
                    existing?.downloadedAt ?: 0
                },
                remoteAvailable = true,
            ),
        )
    }

    suspend fun setSongRemoteAvailable(videoId: String, available: Boolean) =
        songDao.setRemoteAvailable(videoId, available)

    /** Si el usuario borró el archivo manualmente, se limpia la referencia. */
    suspend fun clearMissingFile(videoId: String) = songDao.clearLocalFile(videoId)

    // endregion

    // region Borrado

    suspend fun deleteSong(videoId: String) = withContext(Dispatchers.IO) {
        val song = songDao.get(videoId) ?: return@withContext
        song.localUri?.let { deleteDocument(it) }
        songDao.clearLocalFile(videoId)
        if (songDao.playlistReferences(videoId) == 0) {
            songDao.delete(videoId)
            deleteLocalCover(song.thumbnailUrl)
        }
    }

    /** Borra la playlist; los archivos solo se eliminan si ninguna otra playlist los usa. */
    suspend fun deletePlaylist(id: Long, deleteFiles: Boolean) = withContext(Dispatchers.IO) {
        val songs = playlistDao.getSongs(id)
        val playlist = playlistDao.get(id)
        playlistDao.delete(id)
        if (deleteFiles) {
            songs.forEach { row ->
                if (songDao.playlistReferences(row.song.videoId) == 0) {
                    row.song.localUri?.let { deleteDocument(it) }
                    songDao.delete(row.song.videoId)
                    deleteLocalCover(row.song.thumbnailUrl)
                }
            }
            deleteLocalCover(playlist?.thumbnailUrl)
            playlist?.folderUri?.let { folder ->
                // borra la carpeta solo si quedó vacía (o con la lista .m3u8)
                runCatching {
                    val dir = androidx.documentfile.provider.DocumentFile
                        .fromTreeUri(context, Uri.parse(folder))
                    val remaining = dir?.listFiles().orEmpty().filterNot { it.name?.endsWith(".m3u8") == true }
                    if (remaining.isEmpty()) dir?.delete()
                }
            }
        }
        songDao.deleteOrphans()
    }

    /** Borra la carátula guardada en el almacenamiento interno (file://), si la hay. */
    private fun deleteLocalCover(uri: String?) {
        if (uri == null || !uri.startsWith("file://")) return
        runCatching { Uri.parse(uri).path?.let { java.io.File(it).delete() } }
    }

    private fun deleteDocument(uri: String) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri)) }
    }

    // endregion
}
