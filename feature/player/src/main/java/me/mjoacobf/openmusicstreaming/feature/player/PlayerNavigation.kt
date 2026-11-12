package me.mjoacobf.openmusicstreaming.feature.player

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import me.mjoacobf.openmusicstreaming.core.navigation.Navigator
import me.mjoacobf.openmusicstreaming.core.navigation.NowPlayingRoute
import org.koin.compose.koinInject
import org.koin.dsl.module

/** El ViewModel del reproductor es único (lo comparten el mini reproductor y la pantalla completa). */
val playerModule = module {
    single { PlayerViewModel(get(), get(), get()) }
}

fun EntryProviderScope<NavKey>.playerEntries(navigator: Navigator) {
    entry<NowPlayingRoute> {
        NowPlayingScreen(viewModel = koinInject(), onClose = navigator::back)
    }
}
