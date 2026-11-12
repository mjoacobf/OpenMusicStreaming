package me.mjoacobf.openmusicstreaming.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        BookmarkEntity::class,
        SearchHistoryEntity::class,
        UserPlaylistEntity::class,
        UserPlaylistItemEntity::class,
        PlayHistoryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun userPlaylistDao(): UserPlaylistDao
    abstract fun playHistoryDao(): PlayHistoryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "openmusic.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v1.01: playlists del usuario e historial de reproducción (tablas nuevas; no toca datos). */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL idéntico al esquema exportado en schemas/.../2.json
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_playlist_items` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`playlistId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `itemId` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT NOT NULL, " +
                        "`thumbnailUrl` TEXT, `durationSec` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`playlistId`) REFERENCES `user_playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_user_playlist_items_playlistId_position` " +
                        "ON `user_playlist_items` (`playlistId`, `position`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`itemId` TEXT NOT NULL, `url` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, `thumbnailUrl` TEXT, `durationSec` INTEGER NOT NULL, `playedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_playedAt` ON `play_history` (`playedAt`)")
            }
        }
    }
}
