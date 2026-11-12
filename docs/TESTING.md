# Pruebas

## Unitarias
`./gradlew :core:download:testDebugUnitTest` → 6 pruebas en `core/download/src/test/.../DownloadLogicTest.kt`
(`TrackNames`, `cleanCollectionTitle`, `sanitizeFileName`, estructura de la caja `udta` de `Mp4Tags`).
No hay pruebas instrumentadas ni de UI propias (`ExampleInstrumentedTest`/`ExampleUnitTest` de la plantilla siguen en `app`).
Candidatos a cubrir: `PlaylistSyncer` (orden/ausentes/duplicados con repositorios falsos), `StreamResolver.select*`, `AppSettings` (round-trip JSON), `TopLevelBackStack`.

## Emulador (flujo usado en la sesión de verificación)
```bash
# 1. Arrancar (AVD: Medium_Phone_API_36.1, también Pixel_9a)
~/AppData/Local/Android/Sdk/emulator/emulator.exe -avd Medium_Phone_API_36.1 -no-snapshot-save -no-boot-anim -no-audio -gpu swiftshader_indirect &
export MSYS_NO_PATHCONV=1; ADB=~/AppData/Local/Android/Sdk/platform-tools/adb.exe
$ADB wait-for-device   # y esperar sys.boot_completed=1
# 2. Instalar y conceder notificaciones
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell pm grant me.mjoacobf.openmusicstreaming android.permission.POST_NOTIFICATIONS
$ADB shell am start -n me.mjoacobf.openmusicstreaming/.MainActivity
# 3. Interactuar sin ojos: scripts/adb-ui.sh  (dump | tap "<texto>" [n] | shot <nombre> | type "<texto>" | prep | offline | online)
scripts/adb-ui.sh prep; scripts/adb-ui.sh tap "Ajustes"; scripts/adb-ui.sh shot ajustes   # → build/shots/ajustes.png (se puede leer con la herramienta Read)
```
- Primera vez: Ajustes → «Carpeta de descargas» → «USE THIS FOLDER» → «ALLOW» (el selector abre en `Music`).
- Reinicio limpio: `$ADB shell pm clear me.mjoacobf.openmusicstreaming` (borra BD, ajustes y carátulas; **no** borra los archivos de `/sdcard/Music`).
- Estado de reproducción: `$ADB shell dumpsys media_session | grep -o "state=PlaybackState {state=[A-Z]*([0-9]), position=[0-9]*"` (`PLAYING(3)`).
- Trabajos: `$ADB logcat -d | grep "Worker result"`; notificaciones: `$ADB shell dumpsys notification --noredact | grep -A3 "pkg=me.mjoacobf"`.
- Archivos descargados: `$ADB shell ls -R /sdcard/Music`; traerlos: `$ADB pull "/sdcard/Music/Canciones/<archivo>" <destino>`.
- Validar audio/etiquetas: `~/PATH/ffmpeg.exe -hide_banner -i archivo` (etiquetas + pista `attached pic`) y `ffmpeg -v error -i archivo -f null -` (decodifica sin errores).
- Release: firmar con la clave del proyecto ya lo hace Gradle (`keystore.properties`). Para probar un release sin esa clave: `zipalign` + `apksigner sign --ks C:/Users/User/.android/debug.keystore --ks-pass pass:android`.
  Cambiar de firma exige `adb uninstall` antes.
- Contenido de prueba usado: «daft punk» (búsqueda), `https://www.youtube.com/watch?v=dQw4w9WgXcQ` (video/Opus), álbum «Whenever You Need Somebody» de Rick Astley (10 pistas, sincronización),
  `https://www.youtube.com/watch?v=djV11Xbc914` (video musical).
- Trampas del emulador y de `adb`: ver «Trampas conocidas» en `CLAUDE.md`.

## Matriz verificada (v1.0, emulador API 36, debug y release)
| Área | Resultado |
|---|---|
| Arranque en Biblioteca; barra inferior de 4 pestañas | OK |
| Búsqueda con filtros, sugerencias, paginación; miniaturas | OK |
| Enlace pegado (`music.youtube.com`, `youtube.com`) → detalle con recomendados | OK |
| Streaming de audio (poToken + bloques) y de video (video+audio combinados) | OK, también en **release/R8** |
| Controles de notificación (5 acciones) y reproducción con pantalla apagada | OK |
| Autoplay: se añade un recomendado a la cola | OK |
| Descarga suelta Opus (~3,4 MB, ~1 s): decodifica, etiquetas, carátula 360×360 | OK |
| Descarga suelta M4A: AAC 128 kbps, etiquetas iTunes, carátula; Android la indexa | OK (debug y release) |
| Álbum de 10 pistas: carpeta, orden, `.m3u8` con rutas relativas | OK |
| Sin duplicados: pistas ya bajadas se reutilizan (incl. `../Canciones/...` en el m3u8) | OK |
| Archivo borrado a mano + «Actualizar» → solo se re-descarga ese archivo | OK |
| Reproducción totalmente sin conexión (Wi‑Fi y datos apagados) | OK |
| «Actualizar» sin red queda en espera y termina al volver la red | OK |
| Exportar/importar ajustes JSON (cambio restaurado) | OK |
| Trabajo periódico `auto-update` registrado en JobScheduler | OK |
| Carátulas locales visibles sin conexión | OK |

## Deuda de verificación (no probado)
- **Pausa al conectar/desconectar audífonos o Bluetooth:** el emulador no puede simularlo; hace falta un teléfono real.
- **Playlist/canción eliminada o privada en YouTube** (conservación local + `missingFromRemote` + icono de nube tachada): la lógica existe, falta contenido real para probarla.
- Ejecución real del trabajo periódico `auto-update` (solo se comprobó que está programado y con sus restricciones).
- Rendimiento en un equipo de gama baja real; baseline profile propio (la skill `jetpack-compose-performance` lo recomienda).
- Cambio de tema/colores dinámicos en dispositivo real; modo oscuro/negro puro solo se revisó en código.

## v1.01 (emulador API 36, release firmado instalado sobre la v1.0)
Verificado: migración Room 1→2 (la app abre), búsqueda y reproducción SoundCloud (HLS), búsqueda Bandcamp, descarga de álbum Bandcamp (18 MP3; `ffmpeg -i` muestra título/artista/álbum/pista y carátula 544×544),
notificación de descarga («10 % · 1/18 canciones»), historial, crear playlist propia, cola reordenable (`adb shell input draganddrop x y x2 y2 1500`), limpiar Recientes (`KEYCODE_APP_SWITCH` + swipe → sin sesión ni proceso).
Pruebas unitarias: 14 (`DownloadLogicTest` 6 + `MultiServiceTest` 8). Descarga SoundCloud (HLS AAC fMP4 → M4A) verificada con ffmpeg. Pendiente: enlaces SC/BC pegados, sincronizar playlists de esos servicios, pausa con audífonos (dispositivo real).
