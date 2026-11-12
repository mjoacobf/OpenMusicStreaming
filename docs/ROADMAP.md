# Hoja de ruta

## Versión 1.02 — IMPLEMENTADA (emulador API 36, debug)
| Ítem | Resultado |
|---|---|
| Historial sin duplicados | Hecho (una fila por canción, más reciente primero). Solo lógica/compilación. |
| Artistas en la búsqueda + perfil | OK: filtro Artistas (YouTube Music y SoundCloud), perfil con canciones/álbumes/videos/playlists y búsqueda dentro del artista. |
| Ver artista / álbum desde el reproductor | OK (el álbum se busca por nombre; puede fallar con canciones sueltas). |
| Letras (LRCLIB, experimental) | OK: sincronizadas con línea activa; caché en disco. |
| Limpiar caché / borrar datos | Caché probada; «Borrar datos» solo compilada. |
| Búsqueda en Biblioteca | Hecho (filtro local por pestaña). Sin probar en emulador. |
| Aviso de actualización | Hecho (GitHub releases, una vez por inicio, opcional). Pendiente probar con un release más nuevo publicado. |
| Versión | `versionName 1.02`, `versionCode 3`. Room sin cambios (v2). |

Pendiente para publicar: firmar el release (necesita `keystore.properties` + `release.jks`) y crear el release `v1.02` en GitHub.

# Hoja de ruta — versión 1.01

**Estado: IMPLEMENTADA y verificada en emulador (API 36, release firmado, actualización 1.0→1.01 sobre datos existentes).** Sin commitear aún.
Resumen de lo hecho y de lo que queda abierto:

| Ítem | Resultado |
|---|---|
| A1 Recientes | OK: sigue sonando en Recientes; al limpiarla se detiene la sesión y muere el proceso (salvo descargas). Probado. |
| A2 Notificación | OK: «10 % · 1/18 canciones» con barra (probado con `dumpsys notification`). |
| A3 Solo música | OK: filtros reducidos y recomendados filtrados. Los enlaces de videos no musicales pegados se siguen abriendo (decisión del usuario). |
| A4 Portadas | Hecho: miniaturas 544 px en listas, 1080 en cabeceras/reproductor (reescritura de tamaño en URLs googleusercontent), carátula local 600 px, embebida ≤42 KB. No comparado visualmente contra la v1.0. |
| A5 Cola | OK: mantener y arrastrar (probado con `input draganddrop`); tocar reproduce. |
| B1 Playlists propias | OK: pestaña «Mis listas», añadir desde Explorar/reproductor/Canciones, reordenar, renombrar, eliminar. Crear probado; añadir/reordenar de playlist propia solo compilado. |
| B2 SoundCloud/Bandcamp | OK: búsqueda, reproducción (HLS SoundCloud, release/R8) y descarga de álbum Bandcamp (MP3 + ID3 + carátula, validado con ffmpeg). Descarga de SoundCloud (HLS AAC → M4A de 29:38 con etiquetas y carátula, decodifica sin errores) probada. Sin probar: enlaces pegados, actualización de playlists de estos servicios. |
| B3 Historial | OK: pantalla (icono en Biblioteca), ajustes para activar/borrar. |
| Nombre/versión | «Open Music Streaming», `versionName 1.01`, `versionCode 2`. |

Lo que sigue abajo es el análisis original (referencia histórica).


Fuente: `ObservacionesYNuevasCaracteristicas.md` (escrito por el usuario; no modificarlo). Instrucciones generales del usuario para esta versión:
- Nombre de la app: **«Open Music Streaming»** (hoy `app_name` = `OpenMusicStreaming` en `app/src/main/res/values/strings.xml`; revisar también README y textos de Ajustes).
- Etiquetar la versión como **1.01** → `versionName = "1.01"` y `versionCode = 2` en `app/build.gradle.kts` (el `versionCode` debe crecer siempre). Opcional: `git tag v1.01`.
- «Seguir manteniendo la eficiencia y las prácticas empleadas» (ver `CLAUDE.md` → Convenciones).
- El ícono nuevo (`branding/`, `ic_launcher_*`, `mipmap-*`) ya lo dejó el usuario en el árbol de trabajo sin commitear; incluirlo en un commit propio solo si él lo pide.
- Antes de empezar, hacer `graphify update .` y consultar el grafo para ubicar el código.

> Notas técnicas = análisis hecho sobre el código actual; verificar antes de aplicar.

## A. Observaciones (correcciones)

### A1. Ciclo de vida de la reproducción
**Pedido:** que suene mientras la app esté en «Recientes»; al limpiar la app de Recientes debe desaparecer la reproducción y cerrarse todo, salvo que haya descargas en curso.
**Hoy:** `PlaybackService.onTaskRemoved` solo hace `stopSelf()` si no estaba reproduciendo, o sea sigue sonando tras quitarla de Recientes (comportamiento contrario).
**Idea:** en `onTaskRemoved` pausar y detener siempre (p. ej. `pauseAllPlayersAndStopSelf()` de `MediaSessionService`, verificar que existe en Media3 1.11) y liberar la sesión.
Las descargas viven en el servicio de primer plano de WorkManager (`DownloadWorker`), independiente, así que continúan; el proceso sigue vivo mientras dure la descarga y muere después.
Probar en emulador: reproducir → botón Recientes → deslizar la app → `dumpsys media_session` debe quedar sin sesión; repetir con una descarga activa.

### A2. Notificación de descarga con progreso
**Pedido:** mostrar en la barra de notificaciones el avance: porcentaje descargado y cantidad de canciones ya bajadas.
**Hoy:** `DownloadWorker.foregroundInfo` muestra `hechas / total` con barra; para canciones sueltas es indeterminada y no hay porcentaje. `updateNotification` limita a 1 actualización/s y se enlaza con `PlaylistSyncer.sync(onProgress)`.
**Idea:** pasar también el progreso intra-canción (`SongDownloader.download(onProgress)` ya lo emite) y componer «37 % · 4/10». Para varias descargas simultáneas (`maxParallelDownloads`) usar el progreso agregado.
**Verificar** que realmente aparece: no se comprobó en la sesión anterior (`dumpsys notification --noredact`); en Android 12+ `setForeground` puede fallar si arranca desde segundo plano (ya se captura la excepción).

### A3. Solo contenido musical en la búsqueda
**Pedido:** quitar la búsqueda de cualquier video de YouTube; dejar videos musicales y canciones (el algoritmo recomienda videos sin relación).
**Hoy:** `SearchScreen.kt` (`filterLabels`) ofrece `MUSIC_SONGS, MUSIC_VIDEOS, MUSIC_ALBUMS, MUSIC_PLAYLISTS, VIDEOS, PLAYLISTS, ALL`.
**Idea:** quitar `VIDEOS`, `PLAYLISTS` y `ALL` de `SearchFilter` (`core/model`) y de `YoutubeService.contentFilter`. Revisar también los **recomendados** (`YoutubeService.related`, `StreamDetails.related`):
vienen de `StreamInfo.relatedItems` y pueden ser videos genéricos; filtrar a música (p. ej. uploader terminado en « - Topic», URL de `music.youtube.com`, o duración razonable) o priorizar `MUSIC_VIDEOS`.
Decidir con el usuario si los enlaces pegados de videos no musicales deben seguir abriéndose (hoy sí).

### A4. Calidad de las miniaturas de álbum
**Pedido:** mejorar la calidad de la imagen de portada en álbumes.
**Causa probable:** `Mappers.pickUrl(320)` toma ~320 px en resultados/biblioteca y `YoutubeService` usa 480 (colección) / 720 (detalle); `PlayerViewModel`/`MediaItems` usan `thumbnailUrl` de 320 px a pantalla completa (≈420 dp ⇒ borroso);
`SongDownloader` guarda la carátula local a 360 px y `PlaylistSyncer` la de playlist a 480 px.
**Idea:** pedir la mayor resolución disponible para cabeceras y reproductor (≥544/720), subir `CoverArt.squareJpeg` a 512–600 px (q 85), guardar `thumbnailUrl` de alta calidad para `NowPlayingScreen`
y cargar con Coil el tamaño exacto. Cuidar el peso de las carátulas incrustadas (hoy ≈15–30 KB por canción) y el límite de 65 025 bytes de la página Ogg de `OpusTags`
(si se excede, `OggFromWebMWriter.makeMetadata` descarta la carátula).

### A5. Cola de reproducción con arrastrar y soltar
**Pedido:** en la cola, en vez de flechas arriba/abajo, mantener presionado y arrastrar para reordenar; un toque reproduce ese elemento.
**Hoy:** `NowPlayingScreen.kt` → `QueueList` (flechas `KeyboardArrowUp/Down` → `PlayerViewModel.move(from,to)` → `PlayerConnection.move`/`moveMediaItem`); tocar ya llama `skipTo(index)`.
**Idea:** reordenable con `detectDragGesturesAfterLongPress` + `LazyListState` + `Modifier.animateItem()` (o una librería pequeña como `sh.calvin.reorderable`; revisar licencia/tamaño por el objetivo de eficiencia).
Con `key` estable por elemento (hoy la clave es `"$index-$mediaId"`, hay que cambiarla para que la animación siga al elemento). Mantener quitar elemento (botón o deslizar).

## B. Nuevas características

### B1. Playlists propias (streaming o descargadas)
**Pedido:** crear playlists de usuario; pueden mezclar streaming y descargadas; se guardan los índices/orden de canciones en el dispositivo.
**Hoy:** `playlists` en Room está atada a una URL remota única (`remoteUrl` no nula) y a una carpeta SAF; no sirve para listas del usuario.
**Idea:** tablas nuevas `user_playlists(id, name, createdAt, thumbnail?)` y `user_playlist_items(playlistId, position, itemKey, type, title, subtitle, thumbnailUrl, durationSec, url)`,
donde `itemKey` identifica la canción (`videoId` de YouTube, o `servicio:id` si se hace B2). Si la canción está descargada se reproduce `local:`; si no, `yt:` por streaming (`MediaIds`).
**Migración Room obligatoria:** subir `AppDatabase.version` a 2 con `Migration(1,2)` (ver Convenciones; hay datos de usuarios). Menús «Añadir a playlist» en filas de resultados, biblioteca, cola y reproductor.
Exportar/importar y reordenar con el mismo componente de A5.

### B2. SoundCloud y Bandcamp (como NewPipe), con descarga y actualización de playlists
**Pedido:** integrar ambos servicios basándose en NewPipe; que también se pueda descargar y actualizar playlists.
**Hoy todo está acoplado a YouTube:** `YoutubeService` usa `ServiceList.YouTube`; `StreamResolver.streamInfo` también; `videoIdFromUrl` usa el `streamLHFactory` de YouTube;
`songs.videoId` es PK de 11 caracteres de YouTube; `MediaIds` (`local:/yt:/ytv:`) y `ytstream://` asumen YouTube; `YoutubeDataSource` (bloques, POST, cabeceras) y el poToken son específicos de googlevideo;
`PlaylistSyncer` llama a `YoutubeService.collectionAllItems`; `TrackNames`/«- Topic» y `isAlbumUrl` son heurísticas de YouTube Music.
**Idea (por fases):**
1. Abstraer un `MediaService` (YouTube/SoundCloud/Bandcamp) en `core:extractor` con búsqueda, detalle, colección y resolución de streams, apoyado en `NewPipe.getServiceByUrl(url)` y `ServiceList.SoundCloud/Bandcamp`
   (los dos ya están en NewPipeExtractor v0.27.1; SoundCloud obtiene su `client_id` solo; filtros de búsqueda propios: `tracks`, `albums`, `playlists`…).
2. **Ids con espacio de nombres** (`yt:`, `sc:`, `bc:`) en `songs`, `playlist_songs`, `bookmarks`, `MediaIds` → migración Room 2→3 (o 1→2 si se combina con B1) que prefije los IDs existentes con `yt:`.
3. Reproducción: SoundCloud suele dar HTTP progresivo o HLS; Bandcamp, MP3 progresivo. Usar `DefaultDataSource` (o `HlsMediaSource`) para esos servicios y dejar `YoutubeDataSource` solo para googlevideo.
   Para descargas: guardar tal cual si ya es MP3/M4A (sin recodificar); si es HLS, remux/segmentos (valorar) o limitarse a progresivo.
4. UI: selector de servicio en Explorar (chips) y badge de origen; `PlaylistSyncer` generalizado (el algoritmo de orden/ausentes/dedupe no depende del servicio, solo de `MediaSummary`).
5. Probar con enlaces reales de ambos servicios, también pegados en el buscador (`parseLink` debe detectar cada servicio).
Es el cambio más grande de la v1.01: conviene hacerlo en commits pequeños y con pruebas de `PlaylistSyncer`.

### B3. Historial de reproducción
**Pedido:** pantalla de historial, con opción en Ajustes para borrarlo y para activarlo/desactivarlo.
**Idea:** tabla `play_history(id, itemKey, title, artist, thumbnailUrl, durationSec, url, playedAt)` (migración Room), registrando en `PlaybackService.playerListener.onMediaItemTransition` (el servicio ya inyecta repositorios)
solo si el ajuste está activo; nuevo campo `AppSettings.savePlayHistory` (por defecto `true`) + «Borrar historial» en `SettingsScreen`, igual que el historial de búsqueda (`saveSearchHistory`/`clearHistory`).
UI: nueva ruta en `core:navigation` y pantalla (en `feature:library` o módulo `feature:history`, siguiendo el plugin `oms.android.feature`) con lista agrupada por día, reproducir al tocar, paginada (Room `PagingSource` o `LIMIT`).
Limitar el tamaño (p. ej. 500–1 000 entradas) por eficiencia.

## C. Deuda de verificación y mejoras detectadas (de la v1.0)
- Probar en teléfono real: pausa al conectar/desconectar audífonos y Bluetooth; consumo en un equipo de gama baja. (Ver `docs/TESTING.md`.)
- Probar la conservación de playlists/canciones eliminadas o privadas en YouTube.
- Tests unitarios de `PlaylistSyncer`, `StreamResolver`, `AppSettings`, `TopLevelBackStack`.
- Baseline profile propio y comprobación de `r8-analyzer` sobre las reglas keep.
- Textos de UI en español escritos directamente en los composables; extraer a `strings.xml` si se quiere localizar.
- `graphify` aún no etiqueta comunidades (solo `Community N`); opcional con una API key (`graphify label .`), no necesario para consultar.
