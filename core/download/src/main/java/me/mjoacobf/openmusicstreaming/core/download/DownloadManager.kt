package me.mjoacobf.openmusicstreaming.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.StateFlow
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.model.DownloadStatus
import java.util.concurrent.TimeUnit

/** Punto de entrada para encolar descargas, sincronizaciones y la actualización automática. */
class DownloadManager(
    context: Context,
    private val settings: SettingsRepository,
    private val tracker: DownloadTracker,
    private val storage: SafStorage,
) {
    private val workManager = WorkManager.getInstance(context)

    val statuses: StateFlow<Map<String, DownloadStatus>> = tracker.statuses

    fun hasFolderPermission(treeUri: String?): Boolean = treeUri != null && storage.hasPermission(treeUri)

    private suspend fun constraints(): Constraints {
        val s = settings.current()
        return Constraints.Builder()
            .setRequiredNetworkType(if (s.downloadOnlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
    }

    /** Descarga una playlist/álbum completo (y la registra para sincronizar). */
    suspend fun downloadCollection(url: String, title: String, treeUri: String?, autoUpdate: Boolean) {
        tracker.set(DownloadStatus(url, title, DownloadStatus.State.QUEUED, 0f))
        enqueue(
            "sync:$url",
            workDataOf(
                DownloadWorker.KEY_TYPE to DownloadWorker.TYPE_PLAYLIST,
                DownloadWorker.KEY_URL to url,
                DownloadWorker.KEY_TITLE to title,
                DownloadWorker.KEY_TREE to treeUri,
                DownloadWorker.KEY_AUTO_UPDATE to autoUpdate,
            ),
        )
    }

    /** Actualización manual de una playlist ya descargada. */
    suspend fun syncPlaylist(remoteUrl: String, title: String) =
        downloadCollection(remoteUrl, title, null, autoUpdate = true)

    suspend fun downloadSong(videoId: String, title: String, treeUri: String?) {
        tracker.set(DownloadStatus(videoId, title, DownloadStatus.State.QUEUED, 0f))
        enqueue(
            "song:$videoId",
            workDataOf(
                DownloadWorker.KEY_TYPE to DownloadWorker.TYPE_SONG,
                DownloadWorker.KEY_VIDEO_ID to videoId,
                DownloadWorker.KEY_TITLE to title,
                DownloadWorker.KEY_TREE to treeUri,
            ),
        )
    }

    private suspend fun enqueue(name: String, data: androidx.work.Data) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(key: String) {
        workManager.cancelUniqueWork("sync:$key")
        workManager.cancelUniqueWork("song:$key")
        tracker.remove(key)
    }

    fun dismiss(key: String) = tracker.remove(key)

    /** Cancela todas las descargas y sincronizaciones pendientes o en curso. */
    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    /** Programa (o reprograma) la actualización automática periódica. */
    fun scheduleAutoUpdate(intervalHours: Int, wifiOnly: Boolean) {
        if (intervalHours <= 0) {
            workManager.cancelUniqueWork(AUTO_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<DownloadWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_TYPE to DownloadWorker.TYPE_AUTO,
                    DownloadWorker.KEY_TITLE to "Actualizando playlists",
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(AUTO_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Ejecuta ahora la actualización de todas las playlists automáticas. */
    suspend fun syncAllNow() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_TYPE to DownloadWorker.TYPE_AUTO,
                    DownloadWorker.KEY_TITLE to "Actualizando playlists",
                ),
            )
            .setConstraints(constraints())
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork("sync:all", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        private const val TAG = "oms-downloads"
        private const val AUTO_WORK = "auto-update"
    }
}
