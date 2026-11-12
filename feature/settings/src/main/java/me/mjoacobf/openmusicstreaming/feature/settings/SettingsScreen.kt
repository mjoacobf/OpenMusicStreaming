package me.mjoacobf.openmusicstreaming.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mjoacobf.openmusicstreaming.core.designsystem.SectionHeader
import me.mjoacobf.openmusicstreaming.core.designsystem.readableFolder
import me.mjoacobf.openmusicstreaming.core.designsystem.rememberFolderPicker
import me.mjoacobf.openmusicstreaming.core.model.AudioQuality
import me.mjoacobf.openmusicstreaming.core.model.DownloadFormat
import me.mjoacobf.openmusicstreaming.core.model.ThemeMode
import me.mjoacobf.openmusicstreaming.core.model.VideoQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val update = viewModel::update

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val pickFolder = rememberFolderPicker { uri -> update { it.copy(downloadTreeUri = uri.toString()) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::import)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ajustes") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader("Apariencia")
            ChoiceSetting(
                "Tema",
                listOf(ThemeMode.SYSTEM to "Según el sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro"),
                s.themeMode,
            ) { v -> update { it.copy(themeMode = v) } }
            SwitchSetting("Colores dinámicos", "Usa los colores del fondo de pantalla (Android 12+)", s.dynamicColor) { v ->
                update { it.copy(dynamicColor = v) }
            }
            SwitchSetting("Negro puro", "Fondo negro en modo oscuro (ahorra batería en OLED)", s.pureBlack) { v ->
                update { it.copy(pureBlack = v) }
            }

            SectionHeader("Descargas")
            ClickSetting("Carpeta de descargas", readableFolder(s.downloadTreeUri), onClick = pickFolder)
            ChoiceSetting(
                "Formato de descarga",
                listOf(
                    DownloadFormat.OPUS to "Opus (menor tamaño, mejor calidad)",
                    DownloadFormat.M4A to "M4A/AAC (compatible con todo, como MP3)",
                ),
                s.downloadFormat,
            ) { v -> update { it.copy(downloadFormat = v) } }
            ChoiceSetting(
                "Calidad de descarga",
                listOf(
                    AudioQuality.HIGH to "Alta (~160 kbps)",
                    AudioQuality.MEDIUM to "Media (~70 kbps)",
                    AudioQuality.LOW to "Baja (~50 kbps)",
                ),
                s.downloadAudioQuality,
            ) { v -> update { it.copy(downloadAudioQuality = v) } }
            SwitchSetting("Incluir carátula en el archivo", "Añade unos 30 KB por canción", s.embedCoverArt) { v ->
                update { it.copy(embedCoverArt = v) }
            }
            SwitchSetting("Crear lista .m3u8", "Para ver las playlists en otros reproductores", s.writeM3uPlaylists) { v ->
                update { it.copy(writeM3uPlaylists = v) }
            }
            ChoiceSetting(
                "Descargas simultáneas",
                listOf(1 to "1 (menor consumo)", 2 to "2", 3 to "3", 4 to "4"),
                s.maxParallelDownloads,
            ) { v -> update { it.copy(maxParallelDownloads = v) } }
            SwitchSetting("Descargar solo con Wi-Fi", null, s.downloadOnlyOnWifi) { v ->
                update { it.copy(downloadOnlyOnWifi = v) }
            }

            SectionHeader("Actualización automática de playlists")
            ChoiceSetting(
                "Frecuencia",
                listOf(0 to "Desactivada", 6 to "Cada 6 horas", 12 to "Cada 12 horas", 24 to "Una vez al día", 168 to "Una vez a la semana"),
                s.autoUpdateIntervalHours,
            ) { v -> update { it.copy(autoUpdateIntervalHours = v) } }
            SwitchSetting("Solo con Wi-Fi", null, s.autoUpdateWifiOnly) { v -> update { it.copy(autoUpdateWifiOnly = v) } }
            ClickSetting("Actualizar ahora", "Sincroniza todas las playlists con actualización automática", onClick = viewModel::syncNow)

            SectionHeader("Streaming")
            ChoiceSetting(
                "Calidad de audio",
                listOf(AudioQuality.HIGH to "Alta", AudioQuality.MEDIUM to "Media", AudioQuality.LOW to "Baja"),
                s.streamAudioQuality,
            ) { v -> update { it.copy(streamAudioQuality = v) } }
            ChoiceSetting(
                "Calidad máxima de video",
                VideoQuality.entries.map { it to "${it.height}p" },
                s.videoQuality,
            ) { v -> update { it.copy(videoQuality = v) } }
            SwitchSetting("Ahorro de datos móviles", "Usa calidad baja con datos móviles", s.dataSaverOnMobile) { v ->
                update { it.copy(dataSaverOnMobile = v) }
            }
            SwitchSetting("Reproducción automática", "Continúa con recomendados al terminar", s.autoplayRelated) { v ->
                update { it.copy(autoplayRelated = v) }
            }
            SwitchSetting("Guardar historial de búsqueda", null, s.saveSearchHistory) { v ->
                update { it.copy(saveSearchHistory = v) }
            }
            ClickSetting("Borrar historial de búsqueda", null, onClick = viewModel::clearHistory)
            CountrySetting(s.contentCountry) { v -> update { it.copy(contentCountry = v) } }

            SectionHeader("Reproducción")
            SwitchSetting(
                "Pausar al conectar o desconectar audífonos",
                "Cableados, USB o Bluetooth",
                s.pauseOnAudioDeviceChange,
            ) { v -> update { it.copy(pauseOnAudioDeviceChange = v) } }
            SwitchSetting("Saltar silencios", null, s.skipSilence) { v -> update { it.copy(skipSilence = v) } }
            SwitchSetting(
                "Guardar historial de reproducción",
                "Registra las canciones que escuchas (se ve desde Biblioteca)",
                s.savePlayHistory,
            ) { v -> update { it.copy(savePlayHistory = v) } }
            ClickSetting("Borrar historial de reproducción", null, onClick = viewModel::clearPlayHistory)

            SectionHeader("Copia de seguridad")
            ClickSetting("Exportar configuración", "Guarda los ajustes en un archivo JSON") {
                exportLauncher.launch("openmusicstreaming-ajustes.json")
            }
            ClickSetting("Importar configuración", "Restaura los ajustes desde un archivo JSON") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }

            SectionHeader("Acerca de")
            Text(
                "Open Music Streaming usa NewPipeExtractor (GPLv3) y código adaptado de NewPipe para YouTube Music, " +
                    "SoundCloud y Bandcamp. " +
                    "No requiere cuenta ni inicio de sesión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun SwitchSetting(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ClickSetting(title: String, summary: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        summary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> ChoiceSetting(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ClickSetting(title, options.firstOrNull { it.first == selected }?.second) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(value == selected) { onSelect(value); open = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(value == selected, { onSelect(value); open = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun CountrySetting(current: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ClickSetting("País del contenido", current.ifBlank { "Automático (según el sistema)" }) { open = true }
    if (open) {
        var text by remember { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("País del contenido") },
            text = {
                Column {
                    Text("Código de 2 letras (p. ej. PE, MX, ES). Vacío = automático.")
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(text, { text = it.uppercase().take(2) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { onChange(text.trim()); open = false }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        )
    }
}
