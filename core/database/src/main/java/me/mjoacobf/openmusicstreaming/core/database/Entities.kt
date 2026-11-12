package me.mjoacobf.openmusicstreaming.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    /** URI del archivo (SAF content://). Null si aún no se descargó. */
    val localUri: String?,
    val fileName: String?,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
    /** False si la canción fue eliminada o puesta en privado en YouTube. */
    val remoteAvailable: Boolean = true,
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["remoteUrl"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteUrl: String,
    val remoteId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    /** ItemType.name (PLAYLIST / ALBUM). */
    val type: String,
    val autoUpdate: Boolean,
    val lastSyncedAt: Long,
    val remoteAvailable: Boolean = true,
    /** Árbol SAF elegido por el usuario y carpeta de la playlist dentro de él. */
    val treeUri: String,
    val folderName: String,
    val folderUri: String?,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("videoId")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val videoId: String,
    val position: Int,
    val missingFromRemote: Boolean = false,
)

data class PlaylistSongRow(
    @Embedded val song: SongEntity,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "missingFromRemote") val missingFromRemote: Boolean,
)

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "songCount") val songCount: Int,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val url: String,
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val addedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val lastUsedAt: Long,
)
