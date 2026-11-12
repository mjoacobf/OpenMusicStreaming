package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import me.mjoacobf.openmusicstreaming.core.designsystem.Artwork
import me.mjoacobf.openmusicstreaming.core.designsystem.EmptyState
import me.mjoacobf.openmusicstreaming.core.designsystem.ErrorBox
import me.mjoacobf.openmusicstreaming.core.designsystem.LoadingBox
import me.mjoacobf.openmusicstreaming.core.model.ArtistProfile
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel,
    onBack: () -> Unit,
    onOpenStream: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var downloadTarget by remember { mutableStateOf<MediaSummary?>(null) }
    var playlistTarget by remember { mutableStateOf<MediaSummary?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val actions = viewModel.actions
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val callbacks = remember(viewModel) {
        RemoteItemCallbacks(
            onOpen = { if (it.isCollection) onOpenCollection(it.url) else onOpenStream(it.url) },
            onPlay = { item ->
                val items = viewModel.state.value.items.filter { it.isPlayable }
                actions.playAll(items, items.indexOf(item).coerceAtLeast(0))
            },
            onPlayVideo = { actions.play(it, asVideo = true); onOpenPlayer() },
            onPlayNext = actions::playNext,
            onAddToQueue = actions::addToQueue,
            onDownload = { downloadTarget = it },
            onToggleBookmark = { item, b -> actions.toggleBookmark(scope, item, b) },
            onAddToPlaylist = { playlistTarget = it },
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }.distinctUntilChanged().collect { if (it) viewModel.loadMore() }
    }
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            placeholder = { Text("Buscar de ${state.profile?.name ?: "este artista"}") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                viewModel.search(text)
                            }),
                        )
                    } else {
                        Text(state.profile?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
                actions = {
                    if (state.profile != null) {
                        IconButton(onClick = {
                            if (searching) {
                                searching = false
                                text = ""
                                viewModel.search("")
                            } else {
                                searching = true
                            }
                        }) {
                            Icon(if (searching) Icons.Rounded.Clear else Icons.Rounded.Search, if (searching) "Cerrar búsqueda" else "Buscar")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val profile = state.profile
        when {
            state.loadingProfile -> LoadingBox(Modifier.padding(padding))
            profile == null -> ErrorBox(state.profileError ?: "Error", viewModel::loadProfile, Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "header", contentType = "header") {
                    ArtistHeader(profile, state.items, actions, onOpenPlayer)
                }
                if (profile.sections.size > 1) {
                    item(key = "sections", contentType = "sections") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                            items(profile.sections, key = { it.name }, contentType = { "chip" }) { section ->
                                FilterChip(
                                    selected = state.section == section,
                                    onClick = { viewModel.selectSection(section) },
                                    label = { Text(section.label) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }
                }
                when {
                    state.loading -> item(key = "loading", contentType = "state") {
                        LoadingBox(Modifier.fillMaxWidth().height(160.dp))
                    }

                    state.listError != null -> item(key = "error", contentType = "state") {
                        ErrorBox(state.listError!!, viewModel::loadSection, Modifier.fillMaxWidth().height(200.dp))
                    }

                    state.items.isEmpty() -> item(key = "empty", contentType = "state") {
                        EmptyState(
                            Icons.Rounded.Search,
                            "Sin resultados",
                            if (state.query.isBlank()) "Este artista no tiene contenido en esta sección." else "Nada coincide con \"${state.query}\".",
                            Modifier.fillMaxWidth().height(260.dp),
                        )
                    }

                    else -> items(state.items, key = { it.url }, contentType = { it.type }) { item ->
                        RemoteItemRow(item, item.url in bookmarked, item.id in downloaded, callbacks)
                    }
                }
                if (state.loadingMore) {
                    item(key = "loadingMore", contentType = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    AddToPlaylistHost(actions, playlistTarget) { playlistTarget = null }

    downloadTarget?.let { item ->
        DownloadDialog(
            title = item.title,
            isCollection = item.isCollection,
            defaultTreeUri = settings.downloadTreeUri,
            defaultFormat = settings.downloadFormat,
            defaultQuality = settings.downloadAudioQuality,
            onDismiss = { downloadTarget = null },
            onConfirm = { choice ->
                actions.download(scope, item, choice)
                downloadTarget = null
            },
        )
    }
}

@Composable
private fun ArtistHeader(
    profile: ArtistProfile,
    items: List<MediaSummary>,
    actions: RemoteActions,
    onOpenPlayer: () -> Unit,
) {
    val playable = items.filter { it.isPlayable }
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(profile.thumbnailUrl, size = 96.dp, shape = CircleShape)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = buildString {
                    append(profile.service.label)
                    if (profile.subscriberCount > 0) append(" · ").append("%,d".format(profile.subscriberCount)).append(" suscriptores")
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (profile.description.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Text(
                profile.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp).clickable { expanded = !expanded },
            )
        }
        if (playable.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.playAll(playable, 0); onOpenPlayer() }) {
                    Icon(Icons.Rounded.PlayArrow, null); Text(" Reproducir")
                }
                FilledTonalButton(onClick = { actions.playAll(playable.shuffled(), 0); onOpenPlayer() }) {
                    Icon(Icons.Rounded.Shuffle, null); Text(" Aleatorio")
                }
            }
        }
    }
}
