package me.mjoacobf.openmusicstreaming.feature.library

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import me.mjoacobf.openmusicstreaming.core.navigation.LibraryRoute
import me.mjoacobf.openmusicstreaming.core.navigation.LocalPlaylistRoute
import me.mjoacobf.openmusicstreaming.core.navigation.Navigator
import me.mjoacobf.openmusicstreaming.core.navigation.StreamingRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val libraryModule = module {
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { params -> PlaylistViewModel(params.get(), get(), get(), get()) }
}

fun EntryProviderScope<NavKey>.libraryEntries(navigator: Navigator) {
    entry<LibraryRoute> {
        LibraryScreen(
            viewModel = koinViewModel(),
            onOpenPlaylist = { navigator.navigate(LocalPlaylistRoute(it)) },
            onExplore = { navigator.navigate(StreamingRoute) },
        )
    }
    entry<LocalPlaylistRoute> { key ->
        PlaylistScreen(
            viewModel = koinViewModel(key = "playlist-${key.playlistId}") { parametersOf(key.playlistId) },
            onBack = navigator::back,
        )
    }
}
