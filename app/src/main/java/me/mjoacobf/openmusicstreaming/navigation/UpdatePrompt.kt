package me.mjoacobf.openmusicstreaming.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.UpdateRepository
import me.mjoacobf.openmusicstreaming.core.model.AppRelease
import org.koin.compose.koinInject

/** Solo se consulta una vez por inicio de la app (no al girar la pantalla). */
private var checkedThisLaunch = false

/**
 * Aviso de nueva versión al abrir la app (como NewPipe): si hay un release más nuevo en GitHub se
 * ofrece abrir su página; actualizar es opcional y "Ahora no" no vuelve a avisar de esa versión.
 */
@Composable
fun UpdatePrompt(
    updates: UpdateRepository = koinInject(),
    settings: SettingsRepository = koinInject(),
) {
    var release by remember { mutableStateOf<AppRelease?>(null) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (checkedThisLaunch) return@LaunchedEffect
        checkedThisLaunch = true
        val current = settings.current()
        if (!current.checkForUpdates) return@LaunchedEffect
        release = updates.checkForUpdate().getOrNull()?.takeIf { it.version != current.dismissedUpdateVersion }
    }

    release?.let { r ->
        AlertDialog(
            onDismissRequest = { release = null },
            title = { Text("Nueva versión disponible") },
            text = {
                Text("Open Music Streaming ${r.version} ya está publicada (tienes ${updates.installedVersion}). Actualizar es opcional.")
            },
            confirmButton = {
                TextButton(onClick = { uriHandler.openUri(r.url); release = null }) { Text("Actualizar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    release = null
                    scope.launch { settings.update { it.copy(dismissedUpdateVersion = r.version) } }
                }) { Text("Ahora no") }
            },
        )
    }
}
