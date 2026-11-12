package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import me.mjoacobf.openmusicstreaming.core.navigation.ArtistRoute
import me.mjoacobf.openmusicstreaming.core.navigation.LocalPlaylistRoute
import me.mjoacobf.openmusicstreaming.core.navigation.Navigator
import me.mjoacobf.openmusicstreaming.core.navigation.NowPlayingRoute
import me.mjoacobf.openmusicstreaming.core.navigation.RemoteCollectionRoute
import me.mjoacobf.openmusicstreaming.core.navigation.StreamDetailRoute
import me.mjoacobf.openmusicstreaming.core.navigation.StreamingRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val streamingModule = module {
    factory { RemoteActions(get(), get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { params -> StreamDetailViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> CollectionViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> ArtistViewModel(params.get(), get(), get(), get()) }
}

fun EntryProviderScope<NavKey>.streamingEntries(navigator: Navigator) {
    entry<StreamingRoute> {
        SearchScreen(
            viewModel = koinViewModel(),
            onOpenStream = { navigator.navigate(StreamDetailRoute(it)) },
            onOpenCollection = { navigator.navigate(RemoteCollectionRoute(it)) },
            onOpenArtist = { navigator.navigate(ArtistRoute(it)) },
            onOpenPlayer = { navigator.navigate(NowPlayingRoute) },
        )
    }
    entry<StreamDetailRoute> { key ->
        StreamDetailScreen(
            viewModel = koinViewModel(key = "detail-${key.url}") { parametersOf(key.url) },
            onBack = navigator::back,
            onOpenStream = { navigator.navigate(StreamDetailRoute(it)) },
            onOpenCollection = { navigator.navigate(RemoteCollectionRoute(it)) },
            onOpenArtist = { navigator.navigate(ArtistRoute(it)) },
            onOpenPlayer = { navigator.navigate(NowPlayingRoute) },
        )
    }
    entry<ArtistRoute> { key ->
        ArtistScreen(
            viewModel = koinViewModel(key = "artist-${key.url}") { parametersOf(key.url) },
            onBack = navigator::back,
            onOpenStream = { navigator.navigate(StreamDetailRoute(it)) },
            onOpenCollection = { navigator.navigate(RemoteCollectionRoute(it)) },
            onOpenPlayer = { navigator.navigate(NowPlayingRoute) },
        )
    }
    entry<RemoteCollectionRoute> { key ->
        CollectionScreen(
            viewModel = koinViewModel(key = "collection-${key.url}") { parametersOf(key.url) },
            onBack = navigator::back,
            onOpenStream = { navigator.navigate(StreamDetailRoute(it)) },
            onOpenArtist = { navigator.navigate(ArtistRoute(it)) },
            onOpenLocalPlaylist = { navigator.navigate(LocalPlaylistRoute(it)) },
            onOpenPlayer = { navigator.navigate(NowPlayingRoute) },
        )
    }
}
