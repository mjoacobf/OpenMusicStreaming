package me.mjoacobf.openmusicstreaming.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Worker único para todas las descargas en segundo plano:
 *  - `playlist`: descarga/sincroniza una playlist o álbum;
 *  - `song`: descarga una canción suelta;
 *  - `auto`: sincroniza todas las playlists con actualización automática activada.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val syncer: PlaylistSyncer by inject()
    private val library: LibraryRepository by inject()

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        promoteToForeground(title, 0, 0, 0f)
        return try {
            when (type) {
                TYPE_PLAYLIST -> {
                    val url = inputData.getString(KEY_URL) ?: return Result.failure()
                    syncer.sync(
                        url = url,
                        newTreeUri = inputData.getString(KEY_TREE),
                        newAutoUpdate = inputData.getBoolean(KEY_AUTO_UPDATE, true),
                    ) { done, total, fraction -> updateNotification(title, done, total, fraction) }
                }

                TYPE_SONG -> {
                    val id = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
                    syncer.downloadSingle(id, title, inputData.getString(KEY_TREE)) { p ->
                        updateNotification(title, 0, 0, p)
                    }
                }

                TYPE_AUTO -> {
                    library.autoUpdatePlaylists().forEach { playlist ->
                        runCatching {
                            syncer.sync(playlist.remoteUrl, null, true) { d, t, f ->
                                updateNotification(playlist.title, d, t, f)
                            }
                        }.onFailure { if (it is CancellationException) throw it }
                    }
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlaylistSyncer.NoFolderException) {
            Result.failure(workDataOf(KEY_ERROR to e.message))
        } catch (e: Exception) {
            android.util.Log.w("DownloadWorker", "Download failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf(KEY_ERROR to e.message))
        }
    }

    private suspend fun promoteToForeground(title: String, done: Int, total: Int, fraction: Float) {
        try {
            setForeground(foregroundInfo(title, done, total, fraction))
        } catch (_: Exception) {
            // En Android 12+ puede no permitirse desde segundo plano; la tarea sigue igual
        }
    }

    /** Actualiza la notificación de progreso (limitado para no saturar el sistema). */
    private var lastUpdate = 0L

    private fun updateNotification(title: String, done: Int, total: Int, fraction: Float) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < 800) return
        lastUpdate = now
        try {
            setForegroundAsync(foregroundInfo(title, done, total, fraction))
        } catch (_: Exception) {
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(inputData.getString(KEY_TITLE).orEmpty(), 0, 0, 0f)

    /** Notificación con porcentaje y, si es una playlist, canciones ya descargadas («37 % · 4/10 canciones»). */
    private fun foregroundInfo(title: String, done: Int, total: Int, fraction: Float): ForegroundInfo {
        ensureChannel(applicationContext)
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        val indeterminate = total == 0 && fraction <= 0f
        val text = when {
            indeterminate -> "Preparando…"
            total > 0 -> "$percent % · $done/$total canciones"
            else -> "$percent %"
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { "Descargando música" })
            .setContentText(text)
            .setSubText(if (indeterminate) null else "$percent %")
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        val notificationId = id.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_TREE = "tree"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_ERROR = "error"
        const val TYPE_PLAYLIST = "playlist"
        const val TYPE_SONG = "song"
        const val TYPE_AUTO = "auto"
        const val CHANNEL_ID = "downloads"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Descargas", NotificationManager.IMPORTANCE_LOW),
                    )
                }
            }
        }
    }
}
