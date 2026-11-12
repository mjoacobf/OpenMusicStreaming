package me.mjoacobf.openmusicstreaming.core.download.di

import me.mjoacobf.openmusicstreaming.core.download.DownloadManager
import me.mjoacobf.openmusicstreaming.core.download.DownloadTracker
import me.mjoacobf.openmusicstreaming.core.download.LibraryScanner
import me.mjoacobf.openmusicstreaming.core.download.PlaylistSyncer
import me.mjoacobf.openmusicstreaming.core.download.SafStorage
import me.mjoacobf.openmusicstreaming.core.download.SongDownloader
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val downloadModule = module {
    single { DownloadTracker() }
    single { SafStorage(androidContext()) }
    single { SongDownloader(androidContext(), get(), get(), get()) }
    single { LibraryScanner(androidContext(), get(), get()) }
    single { PlaylistSyncer(get(), get(), get(), get(), get(), get()) }
    single { DownloadManager(androidContext(), get(), get(), get()) }
}
