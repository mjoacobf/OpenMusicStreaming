# Open Music Streaming — contexto para Claude Code

App Android (Kotlin + Jetpack Compose Material 3) que descarga canciones, álbumes y playlists de
YouTube / YouTube Music para escucharlos sin conexión, y también los reproduce por streaming.
Sin cuentas ni inicio de sesión. Licencia **GPLv3** (usa NewPipeExtractor y código adaptado de NewPipe).

**Idioma:** UI, documentación, comentarios y commits en **español**; identificadores de código en inglés.
**Usuario:** hispanohablante; prefiere respuestas directas y verificadas (probó todo en emulador antes de dar por bueno).

## Estado
- **v1.02** (`versionCode 3`): artistas, letras (LRCLIB, experimental), ver artista/álbum, búsqueda en biblioteca, limpiar caché/datos, aviso de actualización, historial sin duplicados. Antes: **v1.01** (`versionCode 2`), probada en emulador API 36 (release firmado ~4,3 MB). Detalle y huecos de verificación en `docs/ROADMAP.md`.
- La v1.01 ya está fusionada en `main`. La v1.02 se trabaja en la rama `feat/v1.02`. No fusionar a `main` sin que lo pidan.
- Novedades v1.01: SoundCloud/Bandcamp (ids `sc:`/`bc:`), playlists propias, historial, cola arrastrable, notificación con %, cierre al limpiar Recientes, Room v2.
- **Del usuario (no tocar ni commitear)**: `obs.md` (notas de trabajo), `.idea/`, `gradle.properties` con cambios locales.

## Documentación (leer bajo demanda, no al inicio)
| Archivo | Contenido |
|---|---|
| `especificaciones.md` | Requisitos originales del usuario (fuente de verdad funcional) |
| `docs/ARCHITECTURE.md` | Módulos, flujos de reproducción y descarga, esquema de BD, DI, navegación |
| `docs/DECISIONS.md` | Decisiones tomadas y su motivo (no reabrir sin avisar al usuario) |
| `docs/ROADMAP.md` | Trabajo pendiente (v1.01) con análisis técnico y deuda de verificación |
| `docs/TESTING.md` | Cómo probar en emulador, qué se verificó y cómo |
| `docs/REFERENCES.md` | Repos de referencia, qué se tomó de cada uno y en qué archivos mirar |

## Grafo de conocimiento (graphify): úsalo ANTES de grep/lectura de archivos
El grafo indexa el código propio (≈1 200 nodos). Responde preguntas de arquitectura con ~1–2 k tokens
en vez de leer archivos enteros.
```
graphify query "¿cómo se evitan duplicados al descargar?" --budget 1500   # recorrido por palabras clave
graphify explain "PlaylistSyncer"            # qué es un nodo y sus vecinos
graphify path "PlaybackService" "StreamResolver"   # camino más corto entre dos conceptos
graphify affected "AppSettings"              # qué se rompe si cambio X
graphify god-nodes --top 15                  # núcleos de la arquitectura
```
- Salida en `graphify-out/` (ignorada por git). Si falta o está vieja: `graphify update .` (≈3 s, sin LLM ni API key).
  Reconstrucción completa: `graphify extract . --code-only` y luego `graphify cluster-only . --no-label`.
- Comprueba frescura: compara `git rev-parse HEAD` con "Built from commit" de `graphify-out/GRAPH_REPORT.md`.
- Límites: solo código (`.kt`/`.java`); no modela `.kts`, manifiestos ni recursos; las comunidades se llaman
  "Community N" (sin etiquetas LLM). `.graphifyignore` excluye skills, `build/` y los muxers copiados de NewPipe.
- Instalación (otra máquina): `pip install graphifyy && graphify install --platform claude` (skill `/graphify`).
  `python` real: `/c/Users/User/AppData/Local/Programs/Python/Python314/python.exe`.

## Comandos
```
./gradlew :app:assembleDebug                  # APK debug (~29 MB) → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease                # release + R8 firmado → app/build/outputs/apk/release/app-release.apk
./gradlew :core:download:testDebugUnitTest    # pruebas unitarias (19)
./gradlew :<modulo>:compileDebugKotlin -q     # compilación rápida de un módulo
```
Entorno: Windows 11, Git Bash. JDK 25, Gradle 9.5.0, AGP 9.3.3 (Kotlin integrado: **no** aplicar `kotlin-android`),
Kotlin 2.4.21, compileSdk/targetSdk 37, minSdk 24. Versiones en `gradle/libs.versions.toml`.
APK distribuible exportado en `dist/OpenMusicStreaming-v1.0.apk` (ignorado por git).
Pruebas en emulador (AVD `Medium_Phone_API_36.1` o `Pixel_9a`): ver `docs/TESTING.md` y `scripts/adb-ui.sh`.

## Arquitectura (resumen; detalle en docs/ARCHITECTURE.md)
Módulos Gradle con plugins de convención en `build-logic/` (`oms.android.library|compose|feature|application`).
```
app ─► feature:{library,streaming,player,bookmarks,settings} ─► core:{data,media,download} ─► core:{extractor,database,datastore}
        todos ─► core:model (modelos puros + AppSettings) · core:navigation (rutas NavKey) · core:designsystem (tema/componentes)
```
- **UI:** Compose M3 puro + Navigation 3 (una pila por pestaña, `app/.../TopLevelBackStack.kt`). ViewModels con Koin.
- **Streaming:** URIs internas `ytstream://audio|video/<id>` → `StreamResolver` → `YoutubeDataSource` (bloques de 8 MB, `range`, POST) →
  ExoPlayer en `PlaybackService` (Media3 `MediaSessionService`). PoToken por WebView oculto (`core/extractor/potoken`).
- **Descarga:** WorkManager → `PlaylistSyncer` → `SongDownloader` (sin recodificar: Opus→Ogg o AAC→M4A con etiquetas y carátula) →
  carpeta del usuario por SAF. Una canción compartida entre playlists se guarda **una sola vez**.
- **Datos:** Room (`core:database`, versión 2) + DataStore con un único JSON `AppSettings` (se exporta/importa tal cual).
- **Offline:** lo descargado nunca depende de YouTube: si la playlist/canción desaparece, se conserva y se marca `missingFromRemote`.

## Convenciones
- **Commits:** `tipo(ámbito): descripción` en español (`feat` para funcionalidades; también `build`, `docs`, `chore`, `fix`). Un commit por
  módulo/funcionalidad. Trabajar en rama, no en `main`. Cerrar con la línea `Co-Authored-By` que indique el harness.
- **Rendimiento (equipos de gama baja):** Compose sin librerías extra de UI; `key` + `contentType` en LazyColumn; leer estados rápidos
  (posición de reproducción) en composables aislados; `collectAsStateWithLifecycle`; no añadir `@Stable/@Immutable` ni `ImmutableList`
  (Strong Skipping). Seguir la skill `jetpack-compose-performance`. Buffers moderados, Coil con caché limitada, descargas sin recodificar.
- **Almacenamiento:** solo SAF (`OpenDocumentTree` empezando en `Music`) con permiso persistente. Nada de permisos de almacenamiento ni rutas absolutas.
- **BD:** al cambiar entidades subir `version` en `AppDatabase` (hoy 2, `MIGRATION_1_2`) y escribir una `Migration` con el SQL de `schemas/.../N.json` (hay datos de usuarios; nunca `fallbackToDestructiveMigration`).
- **Ajustes nuevos:** campo con valor por defecto en `AppSettings` (el JSON tolera claves desconocidas/ausentes) + control en `SettingsScreen`. Se guardan solos.
- **Atribución GPL:** el código adaptado de NewPipe conserva su cabecera ("Adaptado de NewPipe ... GPLv3"). Los muxers en `core/download/.../mux/` son copias casi literales.
- **Verificar de verdad:** compilar no basta; probar en emulador (ver `docs/TESTING.md`) y, para archivos, validar con `ffmpeg`.

## Trampas conocidas (ya costaron tiempo)
- **Shell:** heredocs con comillas y `sed` rompen barras invertidas. Para código con `\` (p. ej. `Regex("\\s+")`) usar Write/Edit, no sed.
  Ya pasó 3 veces con escapes de Kotlin.
- `python` es el alias de la Microsoft Store (no funciona): usar la ruta completa de arriba (`-I` si lee archivos descargados).
- `adb`: `export MSYS_NO_PATHCONV=1`; binario en `~/AppData/Local/Android/Sdk/platform-tools/adb.exe`;
  `input text` con `?`/`&` requiere comillas simples dentro de comillas dobles. `apksigner --ks` necesita ruta estilo `C:/Users/...`.
- Emulador: un tutorial de stylus del teclado captura el texto → `scripts/adb-ui.sh prep`. Etiquetas repetidas ("Explorar" es botón y pestaña) → `tap "Explorar" 2`.
- `ffmpeg` está en `~/PATH/ffmpeg.exe`; no hay `ffprobe`. `ffmpeg -i archivo` muestra etiquetas y carátula.
- Un hook del entorno ("Red de seguridad") puede bloquear comandos con `rm -rf`. Si ocurre: no buscar rodeos, avisar al usuario.
- NewPipeExtractor se resuelve desde JitPack (`v0.27.1`). Si JitPack borra el artefacto, usar el hash del commit en el catálogo.
- Las URLs de googlevideo caducan (~6 h) y exigen poToken: `StreamResolver` cachea 4 h y `YoutubeDataSource` re-resuelve ante 403/410.
- YouTube Music antepone "Album – " a los títulos de álbum (`cleanCollectionTitle` lo quita). El graphify avisa de un error de sintaxis en
  `SettingsScreen.kt:223`: es una limitación del parser (lambda con desestructurado), el código compila.

- **Scripts Python/shell:** los heredocs con comillas triples fallan en el shell; escribir el script con Write y ejecutarlo.
- Descargas de SoundCloud: casi todas las pistas solo tienen HLS (AAC fMP4); ya soportado. Las URLs se resuelven al vuelo, no hay tokens que guardar.
- **Emulador con v1.0 firmada con release:** para probar la migración instalar el release firmado (`install -r`), no el debug.

## Secretos
`release.jks` y `keystore.properties` (raíz, **fuera de git**) firman el release; la contraseña está en `keystore.properties`.
**Respaldar fuera del repo**: sin esa clave no se pueden publicar actualizaciones. Sin ellos, `assembleRelease` genera un APK sin firmar.

## Skills del proyecto (`.claude/skills/`) y cuándo usarlas
`jetpack-compose-performance` (revisar UI/recomposición) · `navigation-3` (rutas, escenas, resultados) · `edge-to-edge` (insets) ·
`r8-analyzer` (reglas keep) · `agp-9-upgrade` (build/AGP) · `android-clean-architecture` (capas) · `android-cli` (SDK/emulador/docs oficiales).
Buscar más con skill-finder: `npx skills find <tema>`; solo fuentes con >1 K instalaciones o repos oficiales.

**Clave nueva (v1.02):** la clave original se perdió y se generó otra (`oms`, RSA 4096, PKCS12). Las instalaciones firmadas con la clave anterior (v1.0/v1.01)
**no se pueden actualizar** sobre la v1.02 (Android exige la misma firma): hay que desinstalar e instalar de nuevo. Desde la v1.02 en adelante, **respaldar `release.jks` y `keystore.properties`**.
