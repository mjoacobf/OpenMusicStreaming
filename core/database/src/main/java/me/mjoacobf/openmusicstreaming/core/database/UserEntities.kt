package me.mjoacobf.openmusicstreaming.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Playlist creada por el usuario (v1.01). */
@Entity(tableName = "user_playlists")
data class UserPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Elemento de una playlist del usuario. Guarda los datos mínimos para mostrarlo y reproducirlo
 * (por streaming o, si está descargado, desde el archivo local). `position` es el orden.
 */
@Entity(
    tableName = "user_playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = UserPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "position")],
)
data class UserPlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val playlistId: Long,
    val position: Int,
    /** Identificador interno de la canción (ver MusicService). */
    val itemId: String,
    val url: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
)

data class UserPlaylistWithInfo(
    @Embedded val playlist: UserPlaylistEntity,
    @ColumnInfo(name = "itemCount") val itemCount: Int,
    @ColumnInfo(name = "coverUrl") val coverUrl: String?,
)

/** Historial de reproducción (v1.01). */
@Entity(tableName = "play_history", indices = [Index("playedAt")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val url: String,
    val type: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val playedAt: Long,
)
