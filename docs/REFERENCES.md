# Repositorios de referencia

Se clonaron con `--depth 1` en un directorio temporal (no forman parte del repo). Para volver a estudiarlos:
```bash
git clone --depth 1 https://github.com/TeamNewPipe/NewPipe.git
git clone --depth 1 https://github.com/Anrimian/music-player.git
git clone --depth 1 https://github.com/onnowhere/youtube_music_playlist_downloader.git
# NewPipeExtractor en Windows falla por rutas largas en los mocks de test: usar
git -c core.longpaths=true clone --depth 1 https://github.com/TeamNewPipe/NewPipeExtractor.git
```
Versión usada del extractor: **`v0.27.1`** (JitPack, `com.github.TeamNewPipe:NewPipeExtractor`). En NewPipe: Koin 4.2.2, Coil 3.6.3, Navigation 3 1.1.2, Room 3.

## TeamNewPipe/NewPipe y NewPipeExtractor (GPLv3) — búsqueda, extracción, descarga
| Qué | Dónde mirar en NewPipe | Dónde quedó aquí |
|---|---|---|
| Cliente HTTP del extractor (OkHttp + Brotli/Gzip, 429→ReCaptcha) | `app/.../DownloaderImpl.java` | `core/extractor/NewPipeDownloader.kt` |
| poToken por WebView (BotGuard) | `app/.../util/potoken/*`, `app/src/main/assets/po_token.html` | `core/extractor/potoken/*`, `core/extractor/src/main/assets/po_token.html` (RxJava → `CompletableFuture`) |
| Peticiones a googlevideo (POST, `range`, `rn`, cabeceras) | `app/.../player/datasource/YoutubeHttpDataSource.java` | `core/extractor/YoutubeDataSource.kt` |
| Elección de fuentes y manifiestos | `app/.../player/resolver/PlaybackResolver.java` | `StreamResolver` (usa solo streams progresivos) |
| WebM→Ogg/Opus con etiquetas | `app/.../streams/OggFromWebMWriter.java`, `WebMReader.java`, `DataReader.java` | `core/download/.../mux/` (cambiado para recibir etiquetas, sin `StreamInfo`) |
| MP4 DASH→M4A | `app/.../streams/Mp4FromDashWriter.java`, `Mp4DashReader.java`, `us/shandian/giga/postprocessing/M4aNoDash.java` | `mux/` (+ `setUserData` para `udta`) |
| API del extractor | `NewPipe.init`, `StreamInfo.getInfo`, `SearchInfo`, `PlaylistInfo`, `StreamInfoItem`, `YoutubeSearchQueryHandlerFactory` (`MUSIC_SONGS`, `MUSIC_ALBUMS`…) | `core/extractor/YoutubeService.kt` |
Servicios disponibles en el extractor (`ServiceList`): `YouTube(0)`, `SoundCloud(1)`, `MediaCCC(2)`, `PeerTube(3)`, `Bandcamp(4)`. Base para v1.01.

## Anrimian/music-player (Simple Music Player) — reproductor y pantalla de reproducción
| Qué | Dónde mirar | Cómo se aplicó |
|---|---|---|
| Modos de repetición (`NONE`, `REPEAT_PLAY_QUEUE`, `REPEAT_COMPOSITION`, `PLAY_COMPOSITION_ONCE`) y ciclo al pulsar | `domain/.../LibraryPlayerInteractor.kt` (`changeRepeatMode`, `onCompositionPlayFinished`) | Ciclo OFF → ALL → ONE en `PlayerConnection.cycleRepeat` |
| «Anterior» reinicia la canción si ya avanzó | `LibraryPlayerInteractor.skipToPrevious` (`skipConstraintMillis`) | `PlayerConnection.previous(restartAfterSec)`; ajuste `previousRestartsAfterSec` (3 s) |
| Pausa al desconectar audífonos | `data/.../SystemMusicControllerImpl.kt` (`ACTION_AUDIO_BECOMING_NOISY`), `BluetoothConnectionReceiver.java` | `setHandleAudioBecomingNoisy` + `AudioDeviceMonitor` |
| Cola de reproducción y pantalla (aleatorio, repetir, cola) | `domain/.../PlayerCoordinatorInteractor.kt`, `app/.../ui/player/*` | `NowPlayingScreen.kt` (la cola con flechas es lo que el usuario quiere cambiar por arrastrar: ver ROADMAP) |
Usa Media3 1.9.3 (aquí 1.11.1).

## onnowhere/youtube_music_playlist_downloader — sincronización de playlists
`youtube_music_playlist_downloader.py`, función `generate_playlist` (~L799–1010): descarga lo nuevo, actualiza metadatos, renumera según el orden actual y mueve las canciones
ausentes (`retain_missing_order=false`) al final (`update_file_order`). Configuración por playlist en `.playlist_config.json`; aquí se guarda en Room (`playlists.autoUpdate`, etc.).
Diferencias: no usa yt-dlp/ffmpeg (Kotlin + extractor de NewPipe), no renombra archivos con el número de pista (el orden vive en la BD y en el `.m3u8`).

## Skills de Android instaladas (`.claude/skills/`)
Origen: `android/skills` (oficial de Google) y `affaan-m/ecc@android-clean-architecture`. Instalar/actualizar: `npx skills add <repo> --skill <nombre> -a claude-code -y`
(`skills-lock.json` y `.agents/skills/find-skills` son del skill-finder del usuario).
