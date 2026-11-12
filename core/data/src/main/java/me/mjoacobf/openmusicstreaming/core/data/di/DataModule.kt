package me.mjoacobf.openmusicstreaming.core.data.di

import me.mjoacobf.openmusicstreaming.core.data.BookmarkRepository
import me.mjoacobf.openmusicstreaming.core.data.LibraryRepository
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.database.AppDatabase
import me.mjoacobf.openmusicstreaming.core.datastore.SettingsDataSource
import me.mjoacobf.openmusicstreaming.core.extractor.NewPipeDownloader
import me.mjoacobf.openmusicstreaming.core.extractor.StreamResolver
import me.mjoacobf.openmusicstreaming.core.extractor.YoutubeService
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Dependencias de datos compartidas por todos los módulos. */
val dataModule = module {
    single { OkHttpClient.Builder().build() }
    single { NewPipeDownloader(get()) }
    single { StreamResolver() }
    single { YoutubeService(androidContext(), get(), get()) }

    single { AppDatabase.create(androidContext()) }
    single { SettingsDataSource(androidContext()) }

    single { SettingsRepository(androidContext(), get()) }
    single { LibraryRepository(androidContext(), get()) }
    single { BookmarkRepository(get()) }
    single { StreamingRepository(get(), get()) }
}
