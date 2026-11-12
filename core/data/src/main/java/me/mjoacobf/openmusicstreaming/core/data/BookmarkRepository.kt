package me.mjoacobf.openmusicstreaming.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.mjoacobf.openmusicstreaming.core.database.AppDatabase
import me.mjoacobf.openmusicstreaming.core.database.BookmarkEntity
import me.mjoacobf.openmusicstreaming.core.model.Bookmark
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary

/** Marcadores de canciones, videos, playlists y álbumes. */
class BookmarkRepository(db: AppDatabase) {
    private val dao = db.bookmarkDao()

    fun observeAll(): Flow<List<Bookmark>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeUrls(): Flow<Set<String>> = dao.observeUrls().map { it.toSet() }

    fun isBookmarked(url: String): Flow<Boolean> = dao.observeIsBookmarked(url)

    suspend fun add(item: MediaSummary) = dao.upsert(
        BookmarkEntity(
            url = item.url,
            id = item.id,
            type = item.type.name,
            title = item.title,
            subtitle = item.subtitle,
            thumbnailUrl = item.thumbnailUrl,
            durationSec = item.durationSec,
            addedAt = System.currentTimeMillis(),
        ),
    )

    suspend fun remove(url: String) = dao.delete(url)

    suspend fun toggle(item: MediaSummary, currentlyBookmarked: Boolean) {
        if (currentlyBookmarked) remove(item.url) else add(item)
    }
}
