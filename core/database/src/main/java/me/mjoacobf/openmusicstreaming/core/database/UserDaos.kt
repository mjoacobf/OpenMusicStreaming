package me.mjoacobf.openmusicstreaming.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPlaylistDao {
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM user_playlist_items i WHERE i.playlistId = p.id) AS itemCount, " +
            "(SELECT i.thumbnailUrl FROM user_playlist_items i WHERE i.playlistId = p.id " +
            "ORDER BY i.position LIMIT 1) AS coverUrl FROM user_playlists p ORDER BY p.updatedAt DESC",
    )
    fun observeAll(): Flow<List<UserPlaylistWithInfo>>

    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM user_playlist_items i WHERE i.playlistId = p.id) AS itemCount, " +
            "(SELECT i.thumbnailUrl FROM user_playlist_items i WHERE i.playlistId = p.id " +
            "ORDER BY i.position LIMIT 1) AS coverUrl FROM user_playlists p WHERE p.id = :id",
    )
    fun observe(id: Long): Flow<UserPlaylistWithInfo?>

    @Query("SELECT * FROM user_playlist_items WHERE playlistId = :playlistId ORDER BY position")
    fun observeItems(playlistId: Long): Flow<List<UserPlaylistItemEntity>>

    @Insert
    suspend fun insert(playlist: UserPlaylistEntity): Long

    @Insert
    suspend fun insertItems(items: List<UserPlaylistItemEntity>)

    @Query("UPDATE user_playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE user_playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM user_playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM user_playlist_items WHERE rowId = :rowId")
    suspend fun deleteItem(rowId: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM user_playlist_items WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Query("UPDATE user_playlist_items SET position = :position WHERE rowId = :rowId")
    suspend fun setPosition(rowId: Long, position: Int)

    /** Añade al final, en una sola transacción. */
    @Transaction
    suspend fun append(playlistId: Long, items: List<UserPlaylistItemEntity>, now: Long) {
        var position = nextPosition(playlistId)
        insertItems(items.map { it.copy(playlistId = playlistId, position = position++) })
        touch(playlistId, now)
    }

    /** Guarda un nuevo orden completo (lista de `rowId`). */
    @Transaction
    suspend fun reorder(playlistId: Long, rowIds: List<Long>, now: Long) {
        rowIds.forEachIndexed { index, rowId -> setPosition(rowId, index) }
        touch(playlistId, now)
    }
}

@Dao
interface PlayHistoryDao {
    /** Una fila por canción (la reproducción más reciente), de más reciente a más antigua. */
    @Query(
        "SELECT * FROM play_history WHERE id IN (SELECT MAX(id) FROM play_history GROUP BY itemId) " +
            "ORDER BY playedAt DESC, id DESC LIMIT :limit",
    )
    fun observe(limit: Int): Flow<List<PlayHistoryEntity>>

    /** Quita reproducciones anteriores de la misma canción antes de registrar la nueva. */
    @Query("DELETE FROM play_history WHERE itemId = :itemId")
    suspend fun deleteByItem(itemId: String)

    @Insert
    suspend fun insert(entry: PlayHistoryEntity)

    /** Conserva solo las últimas [keep] reproducciones. */
    @Query(
        "DELETE FROM play_history WHERE id NOT IN " +
            "(SELECT id FROM play_history ORDER BY playedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    /** Borra la canción del historial (todas sus filas, también las duplicadas de versiones anteriores). */
    @Query("DELETE FROM play_history WHERE itemId = (SELECT itemId FROM play_history WHERE id = :id)")
    suspend fun delete(id: Long)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}
