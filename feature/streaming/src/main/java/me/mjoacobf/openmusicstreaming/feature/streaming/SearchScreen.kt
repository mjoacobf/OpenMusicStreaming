package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import me.mjoacobf.openmusicstreaming.core.designsystem.EmptyState
import me.mjoacobf.openmusicstreaming.core.designsystem.ErrorBox
import me.mjoacobf.openmusicstreaming.core.designsystem.LoadingBox
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.SearchFilter

private val filterLabels = listOf(
    SearchFilter.MUSIC_SONGS to "Canciones",
    SearchFilter.MUSIC_VIDEOS to "Videos musicales",
    SearchFilter.MUSIC_ALBUMS to "Álbumes",
    SearchFilter.MUSIC_PLAYLISTS to "Playlists",
    SearchFilter.VIDEOS to "Videos de YouTube",
    SearchFilter.PLAYLISTS to "Playlists de YouTube",
    SearchFilter.ALL to "Todo",
)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenStream: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var downloadTarget by remember { mutableStateOf<MediaSummary?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.OpenStream -> onOpenStream(event.url)
                is SearchEvent.OpenCollection -> onOpenCollection(event.url)
            }
        }
    }

    val callbacks = remember(viewModel) {
        RemoteItemCallbacks(
            onOpen = { if (it.isCollection) onOpenCollection(it.url) else onOpenStream(it.url) },
            onPlay = { viewModel.actions.play(it) },
            onPlayVideo = { viewModel.actions.play(it, asVideo = true); onOpenPlayer() },
            onPlayNext = viewModel.actions::playNext,
            onAddToQueue = viewModel.actions::addToQueue,
            onDownload = { downloadTarget = it },
            onToggleBookmark = { item, b -> viewModel.actions.toggleBookmark(scope, item, b) },
        )
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .onFocusChanged { focused = it.isFocused },
                placeholder = { Text("Buscar o pegar un enlace de YouTube") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Rounded.Clear, "Borrar") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    viewModel.search()
                }),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(filterLabels, key = { it.first.name }, contentType = { "chip" }) { (filter, label) ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading -> LoadingBox()
                    state.error != null -> ErrorBox(state.error!!, onRetry = { viewModel.search(state.submittedQuery) })
                    state.results.isEmpty() && state.submittedQuery.isEmpty() -> EmptyState(
                        Icons.Rounded.TravelExplore,
                        "Explora YouTube Music",
                        "Busca canciones, álbumes, playlists o videos. También puedes pegar un enlace, incluso de contenido no listado.",
                    )
                    state.results.isEmpty() -> EmptyState(Icons.Rounded.Search, "Sin resultados", "Prueba con otro término o filtro.")
                    else -> ResultList(state, bookmarked, downloaded, callbacks, viewModel::loadMore)
                }
                if (focused && suggestions.isNotEmpty()) {
                    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                        LazyColumn {
                            items(suggestions, key = { it }, contentType = { "suggestion" }) { s ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            focusManager.clearFocus()
                                            viewModel.search(s)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(s, Modifier.padding(start = 16.dp).weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    downloadTarget?.let { item ->
        DownloadDialog(
            title = item.title,
            isCollection = item.isCollection,
            defaultTreeUri = settings.downloadTreeUri,
            defaultFormat = settings.downloadFormat,
            defaultQuality = settings.downloadAudioQuality,
            onDismiss = { downloadTarget = null },
            onConfirm = { choice ->
                viewModel.actions.download(scope, item, choice)
                downloadTarget = null
            },
        )
    }
}

@Composable
private fun ResultList(
    state: SearchUiState,
    bookmarked: Set<String>,
    downloaded: Set<String>,
    callbacks: RemoteItemCallbacks,
    loadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Paginación infinita: carga más al acercarse al final
    LaunchedEffect(listState, state.results.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) loadMore() }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        items(state.results, key = { it.url }, contentType = { it.type }) { item ->
            RemoteItemRow(item, item.url in bookmarked, item.id in downloaded, callbacks)
        }
        if (state.loadingMore) {
            item(key = "loading", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
