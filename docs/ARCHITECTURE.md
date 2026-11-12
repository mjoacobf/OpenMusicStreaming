# Arquitectura

Paquete raíz: `me.mjoacobf.openmusicstreaming`. Cada módulo usa `<raíz>.core.<nombre>` o `<raíz>.feature.<nombre>`.
Todo el código vive en `src/main/java/...` (también el Kotlin).

## Módulos y dependencias

```
app ──► feature:library ─────┐
    ──► feature:streaming ───┤
    ──► feature:player ──────┼──► core:data ──► core:database (Room)
    ──► feature:bookmarks ───┤              ──► core:datastore (DataStore)
    ──► feature:settings ────┘              ──► core:extractor (NewPipeExtractor, OkHttp)
            │                core:media ────► core:data, core:extractor   (Media3)
            │                core:download ─► core:data, core:extractor   (WorkManager)
            └─► todos: core:model · core:navigation · core:designsystem
```
Regla: las `feature:*` nunca dependen entre sí; se comunican por rutas (`core:navigation`) y por singletons de Koin.
`core:model` no depende de nada de Android salvo `kotlinx.serialization`.

| Módulo | Archivos clave |
|---|---|
| `core:model` | `Models.kt` (Song, LocalPlaylist, MediaSummary, Bookmark, DownloadStatus, `formatDuration`), `AppSettings.kt` |
| `core:navigation` | `Routes.kt`: `LibraryRoute`, `StreamingRoute`, `BookmarksRoute`, `SettingsRoute` (pestañas) + `LocalPlaylistRoute`, `RemoteCollectionRoute`, `StreamDetailRoute`, `NowPlayingRoute`; interfaz `Navigator` |
| `core:designsystem` | `Theme.kt` (`OmsTheme`), `Components.kt` (`Artwork`, `MediaRow`, `EmptyState`…), `FolderPicker.kt` |
| `core:database` | `Entities.kt`, `Daos.kt`, `AppDatabase.kt` (versión 1, esquemas en `schemas/`) |
| `core:datastore` | `SettingsDataSource.kt` |
| `core:extractor` | `YoutubeService` (fachada), `StreamResolver`, `YoutubeDataSource`, `NewPipeDownloader`, `Mappers.kt`, `potoken/*` |
| `core:data` | `LibraryRepository`, `StreamingRepository`, `BookmarkRepository`, `SettingsRepository`, `di/DataModule.kt` |
| `core:media` | `PlaybackService`, `PlayerConnection`, `MediaItems.kt` (`MediaIds`), `RoutingDataSource`, `AudioDeviceMonitor` |
| `core:download` | `DownloadManager`, `DownloadWorker`, `PlaylistSyncer`, `SongDownloader`, `SafStorage`, `CoverArt`, `DownloadTracker`, `mux/*` |
| `feature:*` | `*Screen.kt`, `*ViewModel.kt`, `*Navigation.kt` (módulo Koin + `EntryProviderScope.xxxEntries(navigator)`) |
| `app` | `OmsApplication` (Koin, NewPipe, Coil), `MainActivity` (intents), `navigation/OmsApp.kt` y `TopLevelBackStack.kt` |

## Inyección de dependencias (Koin 4)
`OmsApplication.onCreate` registra: `dataModule`, `downloadModule`, `mediaModule`, `libraryModule`, `streamingModule`,
`playerModule`, `bookmarksModule`, `settingsModule`. `PlayerViewModel` es `single` (lo comparten mini reproductor y pantalla completa).
`PlaybackService` y `DownloadWorker` obtienen dependencias con `by inject()` / `KoinComponent`. `RemoteActions` es `factory`.

## Navegación (Navigation 3)
- Rutas = `@Serializable ... : NavKey`. `TopLevelBackStack` mantiene una pila por pestaña y la aplana para `NavDisplay`.
- Tocar la pestaña activa vuelve a su raíz. `NowPlayingRoute` oculta la barra inferior y el mini reproductor.
- `MainActivity` convierte intents en rutas: toque en la notificación → `NowPlayingRoute`; `ACTION_SEND` con enlace de YouTube → detalle o colección.
- Decoradores de `NavDisplay`: `rememberSaveableStateHolderNavEntryDecorator` y `rememberViewModelStoreNavEntryDecorator`.

## Reproducción (core:media + core:extractor)
```
UI ─► PlayerViewModel ─► PlayerConnection (MediaController) ═► PlaybackService (MediaSessionService + ExoPlayer)
```
1. **Identificadores de cola** (`MediaIds`): `local:<id>` (archivo descargado), `yt:<id>` (audio por streaming; si existe copia local se usa esa),
   `ytv:<id>` (video). `PlaybackService.onAddMediaItems` → `resolveItems` asigna la URI real.
2. **URIs internas:** `ytstream://audio/<id>?q=HIGH[&f=m4a]`, `ytstream://video/<id>?h=480`. `RoutingDataSource` envía ese esquema a
   `YoutubeDataSource` y el resto (archivos SAF `content://`) a `DefaultDataSource`.
3. **`YoutubeDataSource`:** resuelve la URL real con `StreamResolver` (caché LRU 48 entradas, validez 4 h), pide bloques de 8 MB con
   `&range=a-b`, método POST con cuerpo `0x78 0x00`, `&rn=N` y cabeceras Origin/Referer/Sec-Fetch (como `YoutubeHttpDataSource` de NewPipe).
   Ante 403/410 vuelve a resolver una vez. Lo reutiliza el gestor de descargas.
4. **Selección de stream** (`StreamResolver`): audio prefiere Opus (`WEBMA_OPUS`) y cae a M4A; `selectM4a` para descargas AAC;
   video usa MP4/H.264 «solo video» ≤ altura elegida y se combina con el audio mediante `MergingMediaSource` (`OmsMediaSourceFactory` dentro de `PlaybackService`, vía `VideoTag`).
5. **PoToken:** `YoutubeService.init` registra `PoTokenProviderImpl` en `YoutubeStreamExtractor.setPoTokenProvider`. Genera tokens BotGuard en un `WebView`
   oculto cargando `assets/po_token.html` (portado de NewPipe, sin RxJava: `CompletableFuture`). Sin WebView válido devuelve `null` y se intenta sin token.
6. **Autoplay:** `PlaybackService.maybeQueueRelated` — si el elemento actual es el último, es streaming y el repeat está en OFF, añade el primer relacionado
   que no esté ya en la cola. Se controla con `AppSettings.autoplayRelated` (switch en la pantalla de reproducción y en Ajustes).
7. **Estado persistente:** aleatorio y repetición se guardan en `AppSettings` vía listeners del player; `skipSilence` se observa.
   Ahorro de datos: red medida + `dataSaverOnMobile` ⇒ calidad LOW.
8. **Audífonos:** `setHandleAudioBecomingNoisy(true)` (desconexión) + `AudioDeviceMonitor` (`AudioDeviceCallback`: conexión y desconexión) ⇒ pausa si `pauseOnAudioDeviceChange`.
9. **Notificación/sesión:** botones personalizados Aleatorio y Repetir con `setMediaButtonPreferences`; `onTaskRemoved` actual: sigue sonando si estaba reproduciendo.
10. **Video:** la pista de video se desactiva en `ON_STOP` de la pantalla de reproducción y se reactiva en `ON_START`.

## Descargas (core:download)
```
DownloadDialog ─► RemoteActions.download ─► DownloadManager ─► WorkManager ─► DownloadWorker ─► PlaylistSyncer ─► SongDownloader
```
- `DownloadManager`: trabajos únicos `sync:<url>` y `song:<id>` (`ExistingWorkPolicy.KEEP`), restricciones de red según `downloadOnlyOnWifi`,
  y trabajo periódico `auto-update` (`scheduleAutoUpdate`) que sincroniza las playlists con `autoUpdate = true`.
- `DownloadWorker`: servicio en primer plano `dataSync`; la notificación (canal `downloads`) muestra `hechas / total` con barra, actualizada como máximo 1 vez/s (`setForegroundAsync`).
- **`PlaylistSyncer.sync`** (lógica de `youtube_music_playlist_downloader`):
  1. `YoutubeService.collectionAllItems` pagina toda la playlist. Si `ContentNotAvailableException` → marca la playlist `remoteAvailable=false` y **conserva** lo local.
  2. Carpeta: subcarpeta con el nombre de la playlist dentro del árbol SAF elegido (`SafStorage.ensureFolder`). Canciones sueltas → carpeta `Canciones`.
  3. Por cada elemento (semáforo `maxParallelDownloads` 1–4) `ensureSong`: mismo `videoId` con archivo presente ⇒ reutiliza; archivo borrado a mano ⇒ `clearMissingFile` y vuelve a bajar;
     **`DownloadedSnapshot.findEquivalent`** (artista|título normalizados y duración ±4 s) ⇒ reutiliza otra versión ya descargada; si no, `SongDownloader.download`.
  4. Nuevo orden = el de YouTube; las canciones locales que ya no están en la playlist remota se añaden **al final** con `missingFromRemote = true`. `replacePlaylistEntries` es atómico.
  5. Escribe `<playlist>.m3u8` (rutas relativas, incluso a `../Canciones/...`) si `writeM3uPlaylists`; `markPlaylistSynced`.
- **`SongDownloader.download`:** `StreamInfo` → stream elegido → temporal `cacheDir/dl_<id>.tmp` por `YoutubeDataSource` →
  Opus: `OggFromWebMWriter` escribe Ogg/Opus con comentarios Vorbis (`TITLE ARTIST ALBUM ALBUMARTIST TRACKNUMBER DATE COMMENT METADATA_BLOCK_PICTURE`);
  M4A: `Mp4FromDashWriter` desfragmenta el MP4 DASH y se inyecta `udta/meta/ilst` (`Mp4Tags`) al final de `moov` → archivo temporal → copia al documento SAF.
  Nombre `Artista - Título.ext` (`sanitizeFileName`), con `[videoId]` si choca. Título/artista con `TrackNames.from` (heurística para «Artista - Título (Official Video)»).
- **Carátulas:** `CoverArt` elige la miniatura 16:9, recorta al centro a cuadrado, 360 px JPEG q80. Se incrusta (si `embedCoverArt`) y se guarda también en
  `filesDir/covers/` (`song_<id>.jpg`, `playlist_<id>.jpg`) para verlas sin conexión; `LibraryRepository` las borra al eliminar.
- El progreso en pantalla sale de `DownloadTracker` (StateFlow en memoria), no de WorkManager.

## Base de datos (Room 2.8.5 + KSP, versión 2)
| Tabla | Columnas principales |
|---|---|
| `songs` | `videoId` PK, title, artist, album, durationSec, thumbnailUrl (puede ser `file://`), `localUri` (SAF), fileName, fileSizeBytes, downloadedAt, `remoteAvailable` |
| `playlists` | `id` PK auto, `remoteUrl` único, remoteId, title, uploader, thumbnailUrl, type (PLAYLIST/ALBUM), `autoUpdate`, lastSyncedAt, remoteAvailable, treeUri, folderName, folderUri, lastError, createdAt |
| `playlist_songs` | PK (`playlistId`,`videoId`), `position`, `missingFromRemote`; FK a `playlists` con CASCADE |
| `bookmarks` | `url` PK, id, type, title, subtitle, thumbnailUrl, durationSec, addedAt |
| `search_history` | `query` PK, lastUsedAt |
Una canción existe **una sola vez** en `songs` aunque esté en varias playlists. Si se borra una playlist con archivos, solo se eliminan los no referenciados por otra.

## Ajustes
`AppSettings` (`@Serializable`) se guarda como un único JSON en DataStore (`app_settings_json`). `SettingsDataSource.update { copy(...) }` persiste en cada cambio.
Exportar/importar (`SettingsRepository`) usa el mismo JSON (`ignoreUnknownKeys`, `coerceInputValues`). Un cambio de frecuencia reprograma el trabajo periódico;
un cambio de país llama a `YoutubeService.setContentCountry`.

## Búsqueda y contenido remoto
`YoutubeService.search` usa `SearchInfo` de NewPipeExtractor con `YoutubeSearchQueryHandlerFactory` (filtros `music_songs`, `music_videos`, `music_albums`,
`music_playlists`, `videos`, `playlists`, `all`). `parseLink` detecta enlaces (también contenido no listado) y `StreamingRepository` los envía a detalle o colección.
Sugerencias: historial local + `SuggestionExtractor` de YouTube. Un álbum se distingue por `list=OLAK5uy_` / `browse/MPREb` o por el filtro `music_albums`.

## Rendimiento y consumo (decisiones de implementación)
R8 activado en release (reglas del extractor en `core/extractor/consumer-rules.pro`); búfer ExoPlayer 20–50 s; Coil: caché de memoria 10 % (RAM baja) o 20 %, disco 64 MB,
RGB565 en equipos `isLowRamDevice`; la posición de reproducción se observa solo en `SeekBar` y `MiniProgress`; listas con `key`/`contentType`; descargas sin recodificar.

## Novedades v1.01
- **Servicios:** `MusicService` (YouTube Music / SoundCloud / Bandcamp) en `core:model`; ids `sc:`/`bc:` + URL (YouTube sin prefijo). `YoutubeService` y `StreamResolver` resuelven por servicio; `YoutubeDataSource` abre directo los que no son googlevideo. SoundCloud HLS: `PlaybackService.streamItem` + `SongDownloader.fetchHls`.
- **BD v2:** `user_playlists`, `user_playlist_items` (snapshot de metadatos + orden), `play_history`. Repos: `UserPlaylistRepository`, `PlayHistoryRepository` (`core:data`).
- **UI:** `UserPlaylistScreen`, `HistoryScreen` (`feature:library`, rutas `UserPlaylistRoute`/`HistoryRoute`), `Reorderable.kt` y `PlaylistDialogs.kt` (`core:designsystem`), chips de servicio en `SearchScreen`.
- **Historial:** `PlaybackService.recordHistory()` en `onIsPlayingChanged`/`onMediaItemTransition` si `AppSettings.savePlayHistory`.
- **Descargas:** progreso agregado (`PlaylistSyncer.sync(onProgress(done,total,fraction))`) → notificación con % y conteo.

## Novedades v1.02
- **Artistas:** `ArtistRoute`, `ArtistScreen`/`ArtistViewModel` (`feature:streaming`), `ArtistProfile`/`ArtistSection` (`core:model`), `YoutubeService.artist/artistItems/findArtistUrl/findAlbumUrl`, `StreamingRepository` los expone. Los resultados de canal (`ItemType.CHANNEL`) solo aparecen con el filtro Artistas.
- **Reproductor:** menú «Ver artista / Ver álbum» (`PlayerViewModel.Event` → rutas) y panel de letra (`LyricsPanel`, solo recompone al cambiar la línea activa). `LyricsRepository` + `Lyrics` (parser LRC y búsqueda binaria de línea activa).
- **Biblioteca:** búsqueda local (canciones, playlists, listas propias) en `LibraryScreen`.
- **Ajustes:** sección Almacenamiento (`MaintenanceRepository`, `clearImageCache` en `core:designsystem`) y Actualizaciones (`UpdateRepository`, `AppSettings.checkForUpdates/dismissedUpdateVersion`).
- **Historial:** una fila por canción (ver DECISIONS 24).
