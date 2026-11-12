package me.mjoacobf.openmusicstreaming.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.Flow
import me.mjoacobf.openmusicstreaming.core.navigation.BookmarksRoute
import me.mjoacobf.openmusicstreaming.core.navigation.LibraryRoute
import me.mjoacobf.openmusicstreaming.core.navigation.Navigator
import me.mjoacobf.openmusicstreaming.core.navigation.NowPlayingRoute
import me.mjoacobf.openmusicstreaming.core.navigation.SettingsRoute
import me.mjoacobf.openmusicstreaming.core.navigation.StreamingRoute
import me.mjoacobf.openmusicstreaming.core.navigation.TopLevelRoute
import me.mjoacobf.openmusicstreaming.feature.bookmarks.bookmarksEntries
import me.mjoacobf.openmusicstreaming.feature.library.libraryEntries
import me.mjoacobf.openmusicstreaming.feature.player.MiniPlayer
import me.mjoacobf.openmusicstreaming.feature.player.PlayerViewModel
import me.mjoacobf.openmusicstreaming.feature.player.playerEntries
import me.mjoacobf.openmusicstreaming.feature.settings.settingsEntries
import me.mjoacobf.openmusicstreaming.feature.streaming.streamingEntries
import org.koin.compose.koinInject

private data class Tab(val route: TopLevelRoute, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(LibraryRoute, "Biblioteca", Icons.Rounded.LibraryMusic),
    Tab(StreamingRoute, "Explorar", Icons.Rounded.TravelExplore),
    Tab(BookmarksRoute, "Marcadores", Icons.Rounded.Bookmarks),
    Tab(SettingsRoute, "Ajustes", Icons.Rounded.Settings),
)

@Composable
fun OmsApp(navigationRequests: Flow<NavKey>) {
    // La app abre directamente en la música descargada
    val topLevel = remember { TopLevelBackStack(LibraryRoute) }
    val navigator = remember {
        object : Navigator {
            override fun navigate(key: NavKey) {
                if (key is TopLevelRoute) topLevel.addTopLevel(key) else topLevel.add(key)
            }

            override fun back() {
                topLevel.removeLast()
            }
        }
    }
    val playerViewModel: PlayerViewModel = koinInject()

    LaunchedEffect(navigationRequests) {
        navigationRequests.collect { navigator.navigate(it) }
    }

    UpdatePrompt()

    val current = topLevel.backStack.lastOrNull()
    val showChrome = current != NowPlayingRoute

    Scaffold(
        bottomBar = {
            if (showChrome) {
                Column {
                    MiniPlayer(playerViewModel, onOpen = { navigator.navigate(NowPlayingRoute) })
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = topLevel.topLevelKey == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (selected) topLevel.resetTopLevel(tab.route) else topLevel.addTopLevel(tab.route)
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = topLevel.backStack,
            onBack = { topLevel.removeLast() },
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                libraryEntries(navigator)
                streamingEntries(navigator)
                playerEntries(navigator)
                bookmarksEntries(navigator)
                settingsEntries()
            },
        )
    }
}
