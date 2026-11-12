# Decisiones de diseño

Formato: decisión → motivo. No reabrir sin avisar al usuario; varias las tomó él mismo.

1. **Formato de descarga a elección del usuario: Opus o M4A (AAC), sin recodificar.** *(decisión del usuario)* No hay MP3: Android no trae codificador MP3 y
   convertir gastaría batería y calidad, contra el requisito de eficiencia. Opus ≈ 160 kbps (menor tamaño); M4A ≈ 128 kbps (compatibilidad universal, «como MP3»).
   Por defecto Opus. Se elige en el diálogo de descarga y en Ajustes; el último valor se recuerda.
2. **Licencia GPLv3, código libre.** *(decisión del usuario)* NewPipeExtractor y el código portado de NewPipe son GPLv3. `LICENSE` en la raíz.
3. **Sin duplicados en descargas.** *(decisión del usuario)* Una canción se guarda una vez aunque esté en varias playlists. Además se detectan versiones equivalentes con otro ID
   (video oficial vs. audio «Topic») por artista+título normalizados y duración ±4 s. `PlaylistSyncer.DownloadedSnapshot`.
4. **Sin inicio de sesión.** Requisito. El anti-bot de YouTube se resuelve con poToken (BotGuard en WebView), igual que NewPipe.
5. **Almacenamiento por SAF.** La carpeta la elige el usuario con el selector del sistema (empieza en `Music`), permiso persistente, subcarpeta por playlist.
   No se piden permisos de almacenamiento. La carpeta es accesible desde cualquier gestor de archivos/reproductor, por eso se escribe también un `.m3u8`.
6. **Contenido descargado independiente de YouTube.** Si se elimina/privatiza algo, se conserva y se marca (`missingFromRemote`, `remoteAvailable=false`). Las canciones ausentes van al final.
7. **Esquema interno `ytstream://`.** Permite que ExoPlayer y el gestor de descargas compartan `YoutubeDataSource` (bloques, cabeceras, renovación de URL caducada).
8. **Streams progresivos, no manifiestos DASH.** Más simples y suficientes para audio; el video se une como «solo video» + audio. No se usan los creadores de manifiestos de NewPipe.
9. **Remux con los muxers de NewPipe, no ffmpeg.** Sin binarios nativos (APK de 4 MB). `OggFromWebMWriter` (modificado para recibir etiquetas) y `Mp4FromDashWriter` (con `setUserData` para `udta`).
10. **Navigation 3, Koin, Room 2.8 (KSP), DataStore, WorkManager, Coil 3, OkHttp 5.** Koin y Coil igual que NewPipe; Room 2.8 estable (NewPipe usa Room 3).
    AGP 9 con Kotlin integrado y plugins de convención en `build-logic`.
11. **Un solo JSON para los ajustes.** Facilita exportar/importar y añadir campos sin migraciones.
12. **Carátulas en local.** Cada canción/playlist guarda una copia en `filesDir/covers` (≈15–30 KB) para que la biblioteca se vea sin conexión.
13. **Reproducción en segundo plano con `MediaSessionService`.** Controles en notificación y bloqueo; Aleatorio/Repetir como botones personalizados; sigue sonando con la pantalla apagada.
14. **Release con R8 y firma propia.** `keystore.properties` + `release.jks` fuera de git. Sin `baseline profile` todavía (pendiente).
15. **graphify local.** `graphify-out/` ignorado por git, se reconstruye en 3 s con `graphify update .`; sin etiquetas LLM para no gastar tokens.
16. **Rama de trabajo `feat/app-inicial`.** No se escribió en `main`; el usuario decide cuándo fusionar.
17. **v1.01 — Ids por servicio sin migrar datos.** YouTube conserva el ID de 11 caracteres; SoundCloud/Bandcamp usan `sc:<url>` / `bc:<url>` (`MusicService` en `core:model`). La URL se recupera del id (`streamUrlOf`), así `songs`, marcadores y `MediaIds` no cambian.
18. **v1.01 — `YoutubeService` sirve a los tres servicios** (se deduce por URL o por parámetro `service`); no se renombró la clase. `YoutubeDataSource` tiene modo directo (sin bloques ni POST) para SoundCloud/Bandcamp.
19. **v1.01 — SoundCloud solo ofrece HLS en muchas pistas.** Reproducción con `media3-exoplayer-hls` (URL .m3u8 resuelta en `PlaybackService.streamItem`); descarga concatenando `init.mp4` + segmentos fMP4/AAC y remuxeando a M4A con etiquetas (`remuxToM4a`; si falla se guarda el fMP4 tal cual) o segmentos MP3 sueltos. Sin recodificar. MP3 se guarda con etiquetas ID3v2.3 propias (`Id3Tags`) y carátula.
20. **v1.01 — Búsqueda solo musical.** Se eliminaron los filtros `VIDEOS/PLAYLISTS/ALL`; los recomendados pasan por `musicOnly()`. Los enlaces pegados de videos no musicales siguen abriéndose (decisión pendiente del usuario).
21. **v1.01 — Cierre al limpiar Recientes.** `onTaskRemoved` detiene y limpia la cola, y `PlaybackService.onAppClosed` (OmsApplication) mata el proceso salvo que haya un `DownloadWorker` en ejecución.
22. **v1.01 — Reordenar sin librerías.** `core:designsystem/Reorderable.kt` (arrastre tras pulsación larga, copia local de la lista y un único `onCommit` al soltar); lo usan la cola y las playlists propias.
23. **v1.01 — Room 2 (migración 1→2 con SQL idéntico al esquema).** Tablas `user_playlists`, `user_playlist_items`, `play_history` (tope 1 000 entradas).
24. **v1.02 — Historial sin duplicados.** `PlayHistoryRepository.record` borra las filas previas del mismo `itemId` antes de insertar, y la consulta agrupa por `itemId` (`MAX(id)`) para limpiar también los duplicados de la v1.01 sin migración. Borrar una entrada elimina todas las filas de esa canción.
25. **v1.02 — Artistas sobre `ChannelInfo`.** `SearchFilter.MUSIC_ARTISTS` (YouTube `music_artists`, SoundCloud `users`; Bandcamp no tiene filtros). En YouTube el perfil usa el canal (álbumes, playlists) y **busca por nombre** las canciones y videos filtrando por artista, porque el canal solo lista «videos». «Buscar dentro del artista» = búsqueda «artista + texto» filtrada al artista (NewPipe no ofrece búsqueda por canal).
26. **v1.02 — Ver artista/álbum desde el reproductor.** `MediaSummary.artistUrl` viaja en los extras del `MediaItem`; si falta (descargas, marcadores, historial) se busca el artista por nombre. YouTube no informa el álbum de una canción: se busca por nombre de álbum (descargadas) o por título+artista; puede fallar y se avisa con un mensaje. No se añadieron columnas a Room.
27. **v1.02 — Letras con LRCLIB (experimental).** `LyricsRepository` (`core:data`): `GET /api/get` por título/artista/álbum/duración y, si no hay, `/api/search` filtrando duración ±4 s; prefiere letra sincronizada (LRC, `Lyrics.parseLrc`). Se guarda en `cacheDir/lyrics` (se borra con «Limpiar caché»). User-Agent propio exigido por LRCLIB. La UI marca «Experimental».
28. **v1.02 — Aviso de actualización como NewPipe.** `UpdateRepository` consulta `releases/latest` de GitHub al abrir (una vez por inicio, `UpdatePrompt` en `app`); no descarga ni instala: «Actualizar» abre la página del release. «Ahora no» guarda `dismissedUpdateVersion` para no insistir; se desactiva con `checkForUpdates`.
29. **v1.02 — Limpieza.** «Limpiar caché»: Coil (memoria+disco), `cacheDir` (salvo `dl_*.tmp`), URLs resueltas. «Borrar datos»: `clearAllTables()` + ajustes por defecto + cancelar descargas y cola; **no toca los archivos de audio** de la carpeta del usuario. Sin cambios de esquema: Room sigue en v2.
