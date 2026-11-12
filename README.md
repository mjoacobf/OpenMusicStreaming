# OpenMusicStreaming

Biblioteca musical para Android (Kotlin + Jetpack Compose Material 3) que descarga canciones,
álbumes y playlists de YouTube / YouTube Music para escucharlas sin conexión, y también permite
reproducirlas por streaming. Sin inicio de sesión.

## Arquitectura modular

| Módulo | Responsabilidad |
|---|---|
| `app` | `Application` (Koin, NewPipe, Coil), `MainActivity`, navegación (Navigation 3) |
| `core:model` | Modelos puros y `AppSettings` (serializable a JSON) |
| `core:navigation` | Rutas (`NavKey`) e interfaz `Navigator` |
| `core:designsystem` | Tema Material 3, componentes y selector de carpeta (SAF) |
| `core:database` | Room: canciones, playlists, marcadores, historial |
| `core:datastore` | Ajustes con guardado automático (DataStore) |
| `core:extractor` | NewPipeExtractor, poToken (WebView), `YoutubeDataSource` por bloques |
| `core:data` | Repositorios e inyección de dependencias |
| `core:media` | `PlaybackService` (Media3), cola, autoplay, pausa por audífonos |
| `core:download` | Descargas/sincronización (WorkManager), remux WebM→Opus con etiquetas |
| `feature:*` | Pantallas: biblioteca, explorar, reproductor, marcadores, ajustes |

La configuración Gradle común está en `build-logic/` (plugins de convención `oms.android.*`).

## Referencias

- [NewPipe](https://github.com/TeamNewPipe/NewPipe) / NewPipeExtractor: búsqueda, extracción,
  poToken, peticiones a googlevideo y conversión WebM→Ogg (código adaptado, GPLv3).
- [Simple Music Player](https://github.com/Anrimian/music-player): comportamiento del reproductor.
- [youtube_music_playlist_downloader](https://github.com/onnowhere/youtube_music_playlist_downloader):
  lógica de sincronización de playlists.

## Licencia

OpenMusicStreaming es software libre bajo la [GNU General Public License v3.0](LICENSE).
