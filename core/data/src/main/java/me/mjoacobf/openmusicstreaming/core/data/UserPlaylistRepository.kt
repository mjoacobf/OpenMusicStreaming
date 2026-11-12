package me.mjoacobf.openmusicstreaming.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.mjoacobf.openmusicstreaming.core.database.AppDatabase
import me.mjoacobf.openmusicstreaming.core.database.PlayHistoryEntity
import me.mjoacobf.openmusicstreaming.core.database.UserPlaylistEntity
import me.mjoacobf.openmusicstreaming.core.database.UserPlaylistItemEntity
import me.mjoacobf.openmusicstreaming.core.model.HistoryEntry
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.UserPlaylist
import me.mjoacobf.openmusicstreaming.core.model.UserPlaylistItem

/**
 * Playlists propias del usuario. Solo guardan el orden y los datos de cada canción; el audio se
 * reproduce por streaming o, si la canción está descargada, desde el archivo local (lo decide
 * el servicio de reproducción al resolver cada elemento).
 */
class UserPlaylistRepository(db: AppDatabase) {
    private val dao = db.userPlaylistDao()

    fun observeAll(): Flow<List<UserPlaylist>> = dao.observeAll().map { list ->
        list.map { UserPlaylist(it.playlist.id, it.playlist.name, it.itemCount, it.coverUrl) }
    }

    fun observe(id: Long): Flow<UserPlaylist?> = dao.observe(id).map { row ->
        row?.let { UserPlaylist(it.playlist.id, it.playlist.name, it.itemCount, it.coverUrl) }
    }

    fun observeItems(id: Long): Flow<List<UserPlaylistItem>> =
        dao.observeItems(id).map { rows -> rows.map { UserPlaylistItem(it.rowId, it.toSummary()) } }

    /** Crea la playlist (y devuelve su id). Si el nombre está vacío usa uno por defecto. */
    suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(UserPlaylistEntity(name = name.trim().ifBlank { "Mi playlist" }, createdAt = now, updatedAt = now))
    }

    suspend fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        dao.rename(id, name.trim(), System.currentTimeMillis())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun add(playlistId: Long, items: List<MediaSummary>) {
        val playable = items.filter { it.isPlayable }
        if (playable.isEmpty()) return
        dao.append(
            playlistId,
            playable.map {
                UserPlaylistItemEntity(
                    playlistId = playlistId, position = 0, itemId = it.id, url = it.url, type = it.type.name,
                    title = it.title, subtitle = it.subtitle, thumbnailUrl = it.thumbnailUrl, durationSec = it.durationSec,
                )
            },
            System.currentTimeMillis(),
        )
    }

    suspend fun removeItem(rowId: Long) = dao.deleteItem(rowId)

    /** Persiste el orden mostrado (lista de `rowId`). */
    suspend fun saveOrder(playlistId: Long, rowIds: List<Long>) =
        dao.reorder(playlistId, rowIds, System.currentTimeMillis())

    private fun UserPlaylistItemEntity.toSummary() = MediaSummary(
        url = url,
        id = itemId,
        type = runCatching { ItemType.valueOf(type) }.getOrDefault(ItemType.SONG),
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        durationSec = durationSec,
    )
}

/** Historial de reproducción (se registra desde el servicio de reproducción si está activado). */
class PlayHistoryRepository(db: AppDatabase) {
    private val dao = db.playHistoryDao()

    fun observe(limit: Int = MAX_ENTRIES): Flow<List<HistoryEntry>> = dao.observe(limit).map { rows ->
        rows.map {
            HistoryEntry(
                it.id,
                MediaSummary(
                    url = it.url, id = it.itemId,
                    type = runCatching { ItemType.valueOf(it.type) }.getOrDefault(ItemType.SONG),
                    title = it.title, subtitle = it.artist, thumbnailUrl = it.thumbnailUrl, durationSec = it.durationSec,
                ),
                it.playedAt,
            )
        }
    }

    /** Cada canción aparece una sola vez: volver a escucharla sube su entrada al inicio. */
    suspend fun record(item: MediaSummary) {
        dao.deleteByItem(item.id)
        dao.insert(
            PlayHistoryEntity(
                itemId = item.id, url = item.url, type = item.type.name, title = item.title,
                artist = item.subtitle, thumbnailUrl = item.thumbnailUrl, durationSec = item.durationSec,
                playedAt = System.currentTimeMillis(),
            ),
        )
        dao.trim(MAX_ENTRIES)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()

    companion object {
        /** Límite por eficiencia: se conservan solo las últimas reproducciones. */
        const val MAX_ENTRIES = 1000
    }
}
