# Especificaciones: OpenMusicStreaming

La siguiente lista de especificaciones está dedicada a la guía para la construcción de la aplicación móvil **OpenMusicStreaming**.

OpenMusicStreaming se presenta como una aplicación Android que es una biblioteca que permite descargar playlists completas y canciones de YouTube Music (o YouTube) en el almacenamiento del celular para luego reproducirlas sin conexión y en segundo plano, así como también presentará una opción de actualización automática o manual de playlists descargadas. Del mismo modo, se puede optar por escuchar por streaming en vez de tenerlo descargado. No se empleará inicio de sesión en esta aplicación.

## Especificaciones de diseño

- Debe ser un proyecto Android que emplee el lenguaje Kotlin sobre Gradle.
- Como interfaz gráfica debe emplear Jetpack Compose en Material 3, no se debe emplear alguna otra interfaz que ralentice el funcionamiento en dispositivos lentos.
- Optimizar en lo más posible el espacio de almacenamiento y el consumo.

## Características del programa

- El usuario al ingresar a la aplicación inmediatamente aparecerá en el menú de música descargada.
- El usuario desde el menú de música descargada puede reproducir playlists o canciones individuales en segundo plano.
- Si se llega a borrar o colocar en privado una playlist en YouTube, o bien no está disponible una canción luego de haberla descargado, no debe eso impedirle al usuario a reproducirla.
- El usuario podrá acceder a un menú de streaming en el cual la búsqueda se comportará de una forma similar a NewPipe ([TeamNewPipe/NewPipe: A libre lightweight streaming front-end for Android.](https://github.com/TeamNewPipe/NewPipe)) respecto a la búsqueda de contenido. Se pueden reproducir tanto videos como música empleando los controles cotidianos que tiene un reproductor de música o video (incluye también aleatorio, repetición y repetición de la misma canción, guiarse del repositorio que se mencionará después). Luego se da la opción de descargar álbumes, playlists enteras (sin tener que descargar uno por uno) y canciones (o videos musicales convertidos a mp3) con sus respectivos metadatos desde ese mismo menú empleando opciones de descarga similares a NewPipe.
- El usuario podrá acceder también al contenido oculto colocando el enlace en el buscador.
- El usuario podrá guardar canciones (o videos musicales), playlists y álbumes en un menú de marcadores, así como también removerlas de marcadores.
- El usuario al ver una canción o video musical en streaming podrá ver una sección de música recomendada (funcionamiento similar al que viene integrado con NewPipe).
- Para streaming, el usuario tendrá la opción de reproducción automática para continuar reproduciendo más canciones recomendadas una vez la canción termine. Esta opción de reproducción automática puede ser desactivada fácilmente por el usuario a través de un switch.
- La reproducción de música y de videos debe realizarse en segundo plano, en donde se pueda escuchar la música con la pantalla apagada y controles que debe tener un reproductor común tanto en el menú de notificaciones como dentro de la aplicación. Guíate de Simple Music Player ([Anrimian/music-player: Music player for Android with optional cloud sync. This repository holds the source of the Offline version.](https://github.com/Anrimian/music-player)) sobre el funcionamiento de la reproducción y de la pantalla de reproducción.
- Dentro de la sección de música descargada, el usuario podrá actualizar las playlists de las canciones o que se actualicen automáticamente (esto provocará que se descarguen las nuevas canciones y se reordenen), esta configuración debe estar fácilmente visible a través de un switch en cada playlist. Una funcionalidad similar a la que tiene este proyecto: [onnowhere/youtube_music_playlist_downloader: YouTube Music Playlist Downloader](https://github.com/onnowhere/youtube_music_playlist_downloader). Claro que no quiero que lo hagas en Python sino en Kotlin y basándote en la forma en la que NewPipe descarga el contenido.
- Al descargar las playlist, el usuario puede optar por descargarlo especificando su ruta a descargar (por defecto que sea en Music). La carpeta debe ser fácilmente accesible por el usuario. Emplea la funcionalidad integrada de Android para hacerle decidir al usuario elegir la carpeta.
- Al conectar/desconectar el dispositivo Bluetooth o audífonos cableados, la música debe pausarse.
- Se busca eficiencia y funcionamiento del programa en dispositivos de bajo consumo, por lo que se recomienda la máxima eficiencia empleando Jetpack Compose con Material 3.
- Debe seguir una arquitectura modular para un fácil mantenimiento y reutilización.
- Todas las configuraciones de la app deben estar guardadas automáticamente y deben tener además una opción para importar y exportar configuraciones.
