package me.mjoacobf.openmusicstreaming

import android.app.ActivityManager
import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.di.dataModule
import me.mjoacobf.openmusicstreaming.core.download.DownloadManager
import me.mjoacobf.openmusicstreaming.core.download.DownloadWorker
import me.mjoacobf.openmusicstreaming.core.download.di.downloadModule
import me.mjoacobf.openmusicstreaming.core.extractor.YoutubeService
import me.mjoacobf.openmusicstreaming.core.media.di.mediaModule
import me.mjoacobf.openmusicstreaming.feature.bookmarks.bookmarksModule
import me.mjoacobf.openmusicstreaming.feature.library.libraryModule
import me.mjoacobf.openmusicstreaming.feature.player.playerModule
import me.mjoacobf.openmusicstreaming.feature.settings.settingsModule
import me.mjoacobf.openmusicstreaming.feature.streaming.streamingModule
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

class OmsApplication : Application(), SingletonImageLoader.Factory, KoinComponent {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OmsApplication)
            modules(
                dataModule,
                downloadModule,
                mediaModule,
                libraryModule,
                streamingModule,
                playerModule,
                bookmarksModule,
                settingsModule,
            )
        }

        val youtube: YoutubeService = get()
        youtube.init(countryCode = "")
        DownloadWorker.ensureChannel(this)

        appScope.launch {
            val settings = get<SettingsRepository>().current()
            if (settings.contentCountry.isNotBlank()) youtube.setContentCountry(settings.contentCountry)
            get<DownloadManager>().scheduleAutoUpdate(settings.autoUpdateIntervalHours, settings.autoUpdateWifiOnly)
        }
    }

    /** Coil con caché limitada (menos RAM en equipos de gama baja) y el mismo cliente OkHttp. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val lowRam = getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { get<OkHttpClient>() })) }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, if (lowRam) 0.10 else 0.20).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(lowRam)
            .crossfade(!lowRam)
            .build()
    }
}
