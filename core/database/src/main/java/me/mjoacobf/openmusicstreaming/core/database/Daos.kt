package me.mjoacobf.openmusicstreaming.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE localUri IS NOT NULL ORDER BY downloadedAt DESC")
    fun observeDownloaded(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE videoId = :videoId")
    suspend fun get(videoId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE videoId IN (:ids)")
    suspend fun getAll(ids: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE localUri IS NOT NULL")
    suspend fun getDownloaded(): List<SongEntity>

    @Query("SELECT videoId FROM songs WHERE localUri IS NOT NULL")
    fun observeDownloadedIds(): Flow<List<String>>

    @Upsert
    suspend fun upsert(song: SongEntity)

    @Query("UPDATE songs SET remoteAvailable = :available WHERE videoId = :videoId")
    suspend fun setRemoteAvailable(videoId: String, available: Boolean)

    @Query("UPDATE songs SET localUri = NULL, fileName = NULL, fileSizeBytes = 0 WHERE videoId = :videoId")
    suspend fun clearLocalFile(videoId: String)

    @Query("DELETE FROM songs WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    /** Canciones sin archivo local y sin playlist que las referencie. */
    @Query(
        "DELETE FROM songs WHERE localUri IS NULL AND videoId NOT IN " +
            "(SELECT videoId FROM playlist_songs)",
    )
    suspend fun deleteOrphans()

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE videoId = :videoId")
    suspend fun playlistReferences(videoId: String): Int
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT playlists.*, (SELECT COUNT(*) FROM playlist_songs ps " +
            "WHERE ps.playlistId = playlists.id) AS songCount " +
            "FROM playlists ORDER BY createdAt DESC",
    )
    fun observeAll(): Flow<List<PlaylistWithCount>>

    @Query(
        "SELECT playlists.*, (SELECT COUNT(*) FROM playlist_songs ps " +
            "WHERE ps.playlistId = playlists.id) AS songCount " +
            "FROM playlists WHERE id = :id",
    )
    fun observe(id: Long): Flow<PlaylistWithCount?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE remoteUrl = :url")
    suspend fun getByUrl(url: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE autoUpdate = 1")
    suspend fun getAutoUpdate(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists")
    suspend fun getAllOnce(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET autoUpdate = :enabled WHERE id = :id")
    suspend fun setAutoUpdate(id: Long, enabled: Boolean)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT songs.*, ps.position AS position, ps.missingFromRemote AS missingFromRemote " +
            "FROM playlist_songs ps INNER JOIN songs ON songs.videoId = ps.videoId " +
            "WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC",
    )
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongRow>>

    @Query(
        "SELECT songs.*, ps.position AS position, ps.missingFromRemote AS missingFromRemote " +
            "FROM playlist_songs ps INNER JOIN songs ON songs.videoId = ps.videoId " +
            "WHERE ps.playlistId = :playlistId ORDER BY ps.position ASC",
    )
    suspend fun getSongs(playlistId: Long): List<PlaylistSongRow>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearSongs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(rows: List<PlaylistSongEntity>)

    /** Reemplaza el orden completo de la playlist de forma atómica. */
    @Transaction
    suspend fun replaceSongs(playlistId: Long, rows: List<PlaylistSongEntity>) {
        clearSongs(playlistId)
        insertSongs(rows)
    }
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Query("SELECT url FROM bookmarks")
    fun observeUrls(): Flow<List<String>>

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun delete(url: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history WHERE `query` LIKE :prefix || '%' ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observe(prefix: String, limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
