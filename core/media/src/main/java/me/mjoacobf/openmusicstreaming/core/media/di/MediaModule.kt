package me.mjoacobf.openmusicstreaming.core.media.di

import me.mjoacobf.openmusicstreaming.core.media.PlayerConnection
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val mediaModule = module {
    single { PlayerConnection(androidContext()) }
}
