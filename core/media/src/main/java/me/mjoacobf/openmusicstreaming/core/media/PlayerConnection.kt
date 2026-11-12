package me.mjoacobf.openmusicstreaming.core.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.model.MediaSummary
import me.mjoacobf.openmusicstreaming.core.model.Song

/** Estado del reproductor expuesto a la UI. */
data class PlayerState(
    val currentItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val hasVideo: Boolean = false,
    val error: String? = null,
)

/**
 * Conexión de la UI con [PlaybackService] mediante un MediaController.
 * Las órdenes se encolan hasta que el controlador está listo.
 */
class PlayerConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    private var positionJob: Job? = null

    /** El Player (para la superficie de video); null hasta conectar. */
    val player: Player? get() = controller

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
            pending.forEach { it(c) }
            pending.clear()
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else {
            pending += action
            connect()
        }
    }

    // region Órdenes

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean? = null) =
        playItems(songs.filter { it.localUri != null }.map { it.toMediaItem() }, startIndex, shuffle)

    fun playRemote(items: List<MediaSummary>, startIndex: Int = 0, asVideo: Boolean = false) =
        playItems(items.filter { it.isPlayable }.map { it.toMediaItem(asVideo) }, startIndex, null)

    fun playItems(items: List<MediaItem>, startIndex: Int, shuffle: Boolean?) = withController { c ->
        if (items.isEmpty()) return@withController
        shuffle?.let { c.shuffleModeEnabled = it }
        val index = if (shuffle == true) items.indices.random() else startIndex.coerceIn(items.indices)
        c.setMediaItems(items, index, 0)
        c.prepare()
        c.play()
    }

    fun playNext(item: MediaItem) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item); c.prepare(); c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, item)
        }
    }

    fun addToQueue(item: MediaItem) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item); c.prepare(); c.play()
        } else {
            c.addMediaItem(item)
        }
    }

    fun togglePlay() = withController { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) = withController { c ->
        c.seekTo(positionMs)
        _position.value = positionMs
    }

    fun next() = withController { it.seekToNext() }

    /** Como Simple Music Player: si avanzó unos segundos, reinicia la canción. */
    fun previous(restartAfterSec: Int = 3) = withController { c ->
        if (c.currentPosition > restartAfterSec * 1000L) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun skipTo(index: Int) = withController { it.seekToDefaultPosition(index); it.play() }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** Desactivado → repetir todo → repetir una. */
    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }

    fun move(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }

    fun clearQueue() = withController { it.stop(); it.clearMediaItems() }

    fun setVideoEnabled(enabled: Boolean) = withController { c ->
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    // endregion

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.value = _state.value.copy(error = error.message ?: error.errorCodeName)
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val queue = List(c.mediaItemCount) { c.getMediaItemAt(it) }
        val tracks: Tracks = c.currentTracks
        _state.value = PlayerState(
            currentItem = c.currentMediaItem,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
            hasVideo = tracks.isTypeSupported(androidx.media3.common.C.TRACK_TYPE_VIDEO),
            error = if (c.playerError != null) _state.value.error else null,
        )
        _position.value = c.currentPosition
        if (c.isPlaying) startPositionUpdates() else positionJob?.cancel()
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(500)
            }
        }
    }
}
