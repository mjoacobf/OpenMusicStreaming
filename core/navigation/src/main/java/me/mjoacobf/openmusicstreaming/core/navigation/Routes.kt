package me.mjoacobf.openmusicstreaming.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinos de nivel superior (barra de navegación). */
@Serializable
sealed interface TopLevelRoute : NavKey

/** Pantalla inicial: música descargada. */
@Serializable
data object LibraryRoute : TopLevelRoute

@Serializable
data object StreamingRoute : TopLevelRoute

@Serializable
data object BookmarksRoute : TopLevelRoute

@Serializable
data object SettingsRoute : TopLevelRoute

/** Playlist/álbum descargado. */
@Serializable
data class LocalPlaylistRoute(val playlistId: Long) : NavKey

/** Playlist creada por el usuario (streaming y/o descargadas). */
@Serializable
data class UserPlaylistRoute(val playlistId: Long) : NavKey

/** Historial de reproducción. */
@Serializable
data object HistoryRoute : NavKey

/** Lista de todas las canciones descargadas. */
@Serializable
data object AllSongsRoute : NavKey

/** Playlist o álbum remoto (YouTube / YouTube Music). */
@Serializable
data class RemoteCollectionRoute(val url: String) : NavKey

/** Detalle de un video/canción remota con recomendados. */
@Serializable
data class StreamDetailRoute(val url: String) : NavKey

/** Perfil de un artista (canal de YouTube Music, SoundCloud o Bandcamp). */
@Serializable
data class ArtistRoute(val url: String) : NavKey

/** Reproductor a pantalla completa. */
@Serializable
data object NowPlayingRoute : NavKey

/** Navegación expuesta a los módulos de funcionalidad. */
interface Navigator {
    fun navigate(key: NavKey)
    fun back()
}
