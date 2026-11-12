package me.mjoacobf.openmusicstreaming.feature.bookmarks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.BookmarkRepository
import me.mjoacobf.openmusicstreaming.core.designsystem.EmptyState
import me.mjoacobf.openmusicstreaming.core.designsystem.MediaRow
import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import me.mjoacobf.openmusicstreaming.core.model.Bookmark
import me.mjoacobf.openmusicstreaming.core.model.ItemType
import me.mjoacobf.openmusicstreaming.core.model.formatDuration
import me.mjoacobf.openmusicstreaming.core.navigation.BookmarksRoute
import me.mjoacobf.openmusicstreaming.core.navigation.Navigator
import me.mjoacobf.openmusicstreaming.core.navigation.RemoteCollectionRoute
import me.mjoacobf.openmusicstreaming.core.navigation.StreamDetailRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class BookmarksViewModel(
    private val repository: BookmarkRepository,
    private val player: PlayerConnection,
) : ViewModel() {
    val bookmarks: StateFlow<List<Bookmark>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(bookmark: Bookmark) = viewModelScope.launch { repository.remove(bookmark.url) }

    fun play(bookmark: Bookmark) = player.playRemote(listOf(bookmark.toSummary()), 0, bookmark.type == ItemType.VIDEO)

    fun playAllSongs(list: List<Bookmark>) =
        player.playRemote(list.filter { it.type == ItemType.SONG || it.type == ItemType.VIDEO }.map { it.toSummary() }, 0)
}

private val filters = listOf(
    null to "Todos",
    ItemType.SONG to "Canciones",
    ItemType.VIDEO to "Videos",
    ItemType.PLAYLIST to "Playlists",
    ItemType.ALBUM to "Álbumes",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(viewModel: BookmarksViewModel, onOpen: (Bookmark) -> Unit) {
    val all by viewModel.bookmarks.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<ItemType?>(null) }
    val shown = if (filter == null) all else all.filter { it.type == filter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marcadores") },
                actions = {
                    if (shown.any { it.type == ItemType.SONG || it.type == ItemType.VIDEO }) {
                        IconButton(onClick = { viewModel.playAllSongs(shown) }) {
                            Icon(Icons.Rounded.PlayArrow, "Reproducir marcadores")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(filters, key = { it.second }, contentType = { "chip" }) { (type, label) ->
                    FilterChip(
                        selected = filter == type,
                        onClick = { filter = type },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            if (shown.isEmpty()) {
                EmptyState(
                    Icons.Rounded.BookmarkBorder,
                    "Sin marcadores",
                    "Guarda canciones, videos, playlists y álbumes desde Explorar para encontrarlos rápido.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(shown, key = { it.url }, contentType = { it.type }) { b ->
                        val kind = when (b.type) {
                            ItemType.SONG -> "Canción"
                            ItemType.VIDEO -> "Video"
                            ItemType.PLAYLIST -> "Playlist"
                            ItemType.ALBUM -> "Álbum"
                            ItemType.CHANNEL -> "Canal"
                        }
                        val duration = if (b.durationSec > 0) " · " + formatDuration(b.durationSec) else ""
                        MediaRow(
                            title = b.title,
                            subtitle = "$kind · ${b.subtitle}$duration",
                            thumbnailUrl = b.thumbnailUrl,
                            onClick = {
                                if (b.type == ItemType.SONG) viewModel.play(b) else onOpen(b)
                            },
                        ) {
                            IconButton(onClick = { viewModel.remove(b) }) {
                                Icon(Icons.Rounded.BookmarkRemove, "Quitar de marcadores")
                            }
                        }
                    }
                }
            }
        }
    }
}

val bookmarksModule = module {
    viewModel { BookmarksViewModel(get(), get()) }
}

fun EntryProviderScope<NavKey>.bookmarksEntries(navigator: Navigator) {
    entry<BookmarksRoute> {
        BookmarksScreen(
            viewModel = koinViewModel(),
            onOpen = { b ->
                when (b.type) {
                    ItemType.PLAYLIST, ItemType.ALBUM -> navigator.navigate(RemoteCollectionRoute(b.url))
                    else -> navigator.navigate(StreamDetailRoute(b.url))
                }
            },
        )
    }
}
