package me.mjoacobf.openmusicstreaming.core.data

import me.mjoacobf.openmusicstreaming.core.database.BookmarkEntity
import me.mjoacobf.openmusicstreaming.core.database.PlaylistSongRow
import me.mjoacobf.openmusicstreaming.core.database.PlaylistWithCount
import me.mjoacobf.openmusicstreaming.core.database.SongEntity
import me.mjoacobf.openmusicstreaming.core.model.Bookmark
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.LocalPlaylist
import me.mjoacobf.openmusicstreaming.core.model.PlaylistSong
import me.mjoacobf.openmusicstreaming.core.model.Song

internal fun SongEntity.toModel() = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    album = album,
    durationSec = durationSec,
    thumbnailUrl = thumbnailUrl,
    localUri = localUri,
    fileSizeBytes = fileSizeBytes,
    downloadedAt = downloadedAt,
    remoteAvailable = remoteAvailable,
)

internal fun PlaylistSongRow.toModel() = PlaylistSong(song.toModel(), position, missingFromRemote)

internal fun PlaylistWithCount.toModel() = LocalPlaylist(
    id = playlist.id,
    remoteUrl = playlist.remoteUrl,
    title = playlist.title,
    uploader = playlist.uploader,
    thumbnailUrl = playlist.thumbnailUrl,
    type = runCatching { ItemType.valueOf(playlist.type) }.getOrDefault(ItemType.PLAYLIST),
    autoUpdate = playlist.autoUpdate,
    lastSyncedAt = playlist.lastSyncedAt,
    remoteAvailable = playlist.remoteAvailable,
    songCount = songCount,
    folderName = playlist.folderName,
    treeUri = playlist.treeUri,
    lastError = playlist.lastError,
)

internal fun BookmarkEntity.toModel() = Bookmark(
    url = url,
    id = id,
    type = runCatching { ItemType.valueOf(type) }.getOrDefault(ItemType.VIDEO),
    title = title,
    subtitle = subtitle,
    thumbnailUrl = thumbnailUrl,
    durationSec = durationSec,
    addedAt = addedAt,
)
