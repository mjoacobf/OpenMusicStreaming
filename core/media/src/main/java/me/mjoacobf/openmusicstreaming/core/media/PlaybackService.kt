package me.mjoacobf.openmusicstreaming.core.media

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext
import me.mjoacobf.openmusicstreaming.core.model.MusicService
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.PlayHistoryRepository
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.extractor.StreamResolver
import me.mjoacobf.openmusicstreaming.core.extractor.YoutubeDataSource
import me.mjoacobf.openmusicstreaming.core.model.AppSettings
import me.mjoacobf.openmusicstreaming.core.model.AudioQuality
import me.mjoacobf.openmusicstreaming.core.model.RepeatModeSetting
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject

/**
 * Servicio de reproducción en segundo plano (pantalla apagada incluida) con controles
 * en la notificación y en la pantalla de bloqueo. Gestiona:
 *  - archivos locales y streaming de YouTube (audio o video);
 *  - aleatorio / repetir todo / repetir una (persistidos en ajustes);
 *  - reproducción automática de recomendados al terminar la cola;
 *  - pausa al conectar o desconectar audífonos/Bluetooth;
 *  - historial de reproducción (si el usuario lo tiene activado).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val settingsRepository: SettingsRepository by inject()
    private val libraryRepository: LibraryRepository by inject()
    private val historyRepository: PlayHistoryRepository by inject()
    private val streamingRepository: StreamingRepository by inject()
    private val resolver: StreamResolver by inject()
    private val okHttpClient: OkHttpClient by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var deviceMonitor: AudioDeviceMonitor

    @Volatile
    private var settings = AppSettings()
    private var autoplayJob: kotlinx.coroutines.Job? = null
    private var lastHistoryKey: String? = null

    override fun onCreate() {
        super.onCreate()
        val youtubeFactory = YoutubeDataSource.Factory(okHttpClient, resolver)
        val dataSourceFactory = RoutingDataSource.Factory(this, youtubeFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(OmsMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                // Búfer moderado: menos memoria en equipos de gama baja
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20_000, 50_000, 1_500, 3_000)
                    .build(),
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(playerListener)

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?.putExtra(EXTRA_OPEN_PLAYER, true)
        val sessionActivity = PendingIntent.getActivity(
            this, 0, launchIntent ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(sessionActivity)
            .build()

        deviceMonitor = AudioDeviceMonitor(this) {
            if (settings.pauseOnAudioDeviceChange && player.isPlaying) player.pause()
        }
        deviceMonitor.start()

        scope.launch {
            settings = settingsRepository.current()
            player.shuffleModeEnabled = settings.shuffle
            player.repeatMode = settings.repeatMode.toPlayer()
            player.skipSilenceEnabled = settings.skipSilence
            updateButtons()
            settingsRepository.settings.collect { s ->
                settings = s
                if (player.skipSilenceEnabled != s.skipSilence) player.skipSilenceEnabled = s.skipSilence
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * La reproducción sigue con la app en "Recientes", pero al quitarla de ahí se pausa, se
     * cierra la sesión y se detiene el servicio. Las descargas (WorkManager) siguen aparte.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        player.stop()
        player.clearMediaItems()
        pauseAllPlayersAndStopSelf()
        onAppClosed?.invoke()
    }

    override fun onDestroy() {
        deviceMonitor.stop()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // region Resolución de elementos de la cola

    private fun streamQuality(): AudioQuality {
        val cm = getSystemService(ConnectivityManager::class.java)
        return if (settings.dataSaverOnMobile && cm?.isActiveNetworkMetered == true) {
            AudioQuality.LOW
        } else {
            settings.streamAudioQuality
        }
    }

    /** Asigna la URI real a cada elemento; prioriza la copia descargada si existe. */
    private suspend fun resolveItems(items: List<MediaItem>): MutableList<MediaItem> {
        val quality = streamQuality()
        return items.map { item ->
            val id = MediaIds.videoId(item.mediaId)
            when {
                item.mediaId.startsWith(MediaIds.LOCAL) -> {
                    val uri = item.requestMetadata.mediaUri
                        ?: libraryRepository.getSong(id)?.localUri?.let(Uri::parse)
                    item.buildUpon().setUri(uri).build()
                }

                item.mediaId.startsWith(MediaIds.STREAM) -> {
                    val local = libraryRepository.getSong(id)?.localUri
                    if (local != null) {
                        item.buildUpon().setUri(Uri.parse(local)).build()
                    } else {
                        streamItem(item, id, quality)
                    }
                }

                item.mediaId.startsWith(MediaIds.VIDEO) -> {
                    item.buildUpon()
                        .setUri(StreamResolver.videoUri(id, settings.videoQuality.height))
                        .setTag(VideoTag(StreamResolver.audioUri(id, quality)))
                        .build()
                }

                else -> item
            }
        }.toMutableList()
    }

    /**
     * Audio por streaming. YouTube y los servicios con archivo progresivo usan `ytstream://`;
     * si el servicio solo ofrece HLS (SoundCloud), se usa la lista .m3u8 con el módulo HLS.
     */
    private suspend fun streamItem(item: MediaItem, id: String, quality: AudioQuality): MediaItem {
        if (MusicService.ofId(id) != MusicService.YOUTUBE) {
            val hlsUrl = withContext(Dispatchers.IO) {
                val info = resolver.streamInfo(id)
                if (StreamResolver.hasProgressiveAudio(info)) null else StreamResolver.selectHls(info, quality)?.content
            }
            if (hlsUrl != null) {
                return item.buildUpon().setUri(hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            }
        }
        return item.buildUpon().setUri(StreamResolver.audioUri(id, quality)).build()
    }

    private data class VideoTag(val audioUri: Uri)

    /** Combina pista de solo video + pista de audio para los videos de YouTube. */
    private class OmsMediaSourceFactory(
        dataSourceFactory: androidx.media3.datasource.DataSource.Factory,
    ) : MediaSource.Factory by DefaultMediaSourceFactory(dataSourceFactory) {
        private val default = DefaultMediaSourceFactory(dataSourceFactory)
        private val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val tag = mediaItem.localConfiguration?.tag
            if (tag is VideoTag) {
                val video = progressive.createMediaSource(mediaItem)
                val audio = progressive.createMediaSource(
                    MediaItem.Builder().setMediaId(mediaItem.mediaId + "#audio").setUri(tag.audioUri).build(),
                )
                return MergingMediaSource(true, video, audio)
            }
            return default.createMediaSource(mediaItem)
        }
    }

    // endregion

    // region Reproducción automática (recomendados)

    private fun maybeQueueRelated() {
        if (!settings.autoplayRelated) return
        if (player.mediaItemCount == 0) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (player.currentMediaItemIndex != player.mediaItemCount - 1) return
        val current = player.currentMediaItem ?: return
        if (!MediaIds.isStreaming(current.mediaId)) return
        val isVideo = MediaIds.isVideo(current.mediaId)
        val videoId = MediaIds.videoId(current.mediaId)
        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            val related = runCatching { streamingRepository.related(videoId) }.getOrNull() ?: return@launch
            val queued = (0 until player.mediaItemCount).mapTo(HashSet()) {
                MediaIds.videoId(player.getMediaItemAt(it).mediaId)
            }
            val next = related.firstOrNull { it.id !in queued } ?: return@launch
            if (player.currentMediaItem?.mediaId != current.mediaId) return@launch
            player.addMediaItems(resolveItems(listOf(next.toMediaItem(asVideo = isVideo))))
        }
    }

    // endregion

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastHistoryKey = null
            if (player.playWhenReady) recordHistory()
            maybeQueueRelated()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordHistory()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            scope.launch { settingsRepository.update { it.copy(shuffle = shuffleModeEnabled) } }
            updateButtons()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            scope.launch { settingsRepository.update { it.copy(repeatMode = repeatMode.toSetting()) } }
            updateButtons()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) maybeQueueRelated()
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) lastHistoryKey = null
        }
    }

    /** Anota la pista actual en el historial (una vez por reproducción, no al reanudar tras una pausa). */
    private fun recordHistory() {
        if (!settings.savePlayHistory) return
        val item = player.currentMediaItem ?: return
        if (item.mediaId == lastHistoryKey) return
        lastHistoryKey = item.mediaId
        val summary = item.toSummary() ?: return
        scope.launch { runCatching { historyRepository.record(summary) } }
    }

    // region Botones personalizados (aleatorio / repetir) en la notificación

    private fun buttons(): List<CommandButton> {
        val shuffle = CommandButton.Builder(
            if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
        )
            .setDisplayName("Aleatorio")
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .build()
        val repeat = CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            },
        )
            .setDisplayName("Repetir")
            .setSessionCommand(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
            .build()
        return listOf(shuffle, repeat)
    }

    private fun updateButtons() {
        session?.setMediaButtonPreferences(buttons())
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setMediaButtonPreferences(buttons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                CMD_REPEAT -> player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }

                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future { resolveItems(mediaItems) }
    }

    // endregion

    companion object {
        const val EXTRA_OPEN_PLAYER = "oms.open_player"

        /** La app la usa para cerrar el proceso al quitar la tarea de Recientes si no hay descargas en curso. */
        @Volatile
        var onAppClosed: (() -> Unit)? = null
        private const val CMD_SHUFFLE = "oms.shuffle"
        private const val CMD_REPEAT = "oms.repeat"

        private fun RepeatModeSetting.toPlayer() = when (this) {
            RepeatModeSetting.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeSetting.ALL -> Player.REPEAT_MODE_ALL
            RepeatModeSetting.ONE -> Player.REPEAT_MODE_ONE
        }

        private fun Int.toSetting() = when (this) {
            Player.REPEAT_MODE_ALL -> RepeatModeSetting.ALL
            Player.REPEAT_MODE_ONE -> RepeatModeSetting.ONE
            else -> RepeatModeSetting.OFF
        }
    }
}
