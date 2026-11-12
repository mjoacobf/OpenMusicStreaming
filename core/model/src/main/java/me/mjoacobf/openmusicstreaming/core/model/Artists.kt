package me.mjoacobf.openmusicstreaming.core.model

/** Secciones del perfil de un artista. */
enum class ArtistSection(val label: String) {
    SONGS("Canciones"),
    ALBUMS("Álbumes"),
    VIDEOS("Videos"),
    PLAYLISTS("Playlists"),
}

/**
 * Perfil de un artista (canal). [sections] indica qué secciones tienen contenido en su servicio;
 * [handle] es opaco (oculta los tipos de NewPipe), igual que [PageToken].
 */
class ArtistProfile(
    val url: String,
    val name: String,
    val thumbnailUrl: String?,
    val bannerUrl: String?,
    val subscriberCount: Long,
    val description: String,
    val sections: List<ArtistSection>,
    val handle: Any,
) {
    val service: MusicService get() = MusicService.ofUrl(url)
}
