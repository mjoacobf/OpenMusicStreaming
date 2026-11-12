package me.mjoacobf.openmusicstreaming.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import me.mjoacobf.openmusicstreaming.core.navigation.SettingsRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
}

fun EntryProviderScope<NavKey>.settingsEntries() {
    entry<SettingsRoute> { SettingsScreen(viewModel = koinViewModel()) }
}
