package me.mjoacobf.openmusicstreaming.feature.streaming

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.mjoacobf.openmusicstreaming.core.designsystem.readableFolder
import me.mjoacobf.openmusicstreaming.core.designsystem.rememberFolderPicker
import me.mjoacobf.openmusicstreaming.core.model.AudioQuality
import me.mjoacobf.openmusicstreaming.core.model.DownloadFormat

data class DownloadChoice(
    val treeUri: String,
    val format: DownloadFormat,
    val quality: AudioQuality,
    val autoUpdate: Boolean,
)

/**
 * Diálogo de descarga (similar al de NewPipe): carpeta de destino (selector del sistema,
 * por defecto "Music"), calidad del audio y, para playlists, actualización automática.
 */
@Composable
fun DownloadDialog(
    title: String,
    isCollection: Boolean,
    defaultTreeUri: String?,
    defaultFormat: DownloadFormat,
    defaultQuality: AudioQuality,
    onDismiss: () -> Unit,
    onConfirm: (DownloadChoice) -> Unit,
) {
    var treeUri by remember { mutableStateOf(defaultTreeUri) }
    var format by remember { mutableStateOf(defaultFormat) }
    var quality by remember { mutableStateOf(defaultQuality) }
    var autoUpdate by remember { mutableStateOf(true) }
    val pickFolder = rememberFolderPicker { treeUri = it.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descargar") },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                Text("Carpeta", style = MaterialTheme.typography.labelLarge)
                Text(
                    readableFolder(treeUri),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = pickFolder, modifier = Modifier.padding(top = 4.dp)) {
                    Text(if (treeUri == null) "Elegir carpeta" else "Cambiar carpeta")
                }
                Spacer(Modifier.height(12.dp))
                Text("Formato (sin recodificar)", style = MaterialTheme.typography.labelLarge)
                QualityOption("Opus · menor tamaño, mejor calidad", format == DownloadFormat.OPUS) {
                    format = DownloadFormat.OPUS
                }
                QualityOption("M4A (AAC) · compatible con todo, como MP3", format == DownloadFormat.M4A) {
                    format = DownloadFormat.M4A
                }
                Spacer(Modifier.height(8.dp))
                Text("Calidad", style = MaterialTheme.typography.labelLarge)
                if (format == DownloadFormat.OPUS) {
                    QualityOption("Alta (~160 kbps)", quality == AudioQuality.HIGH) { quality = AudioQuality.HIGH }
                    QualityOption("Media (~70 kbps, ahorra espacio)", quality == AudioQuality.MEDIUM) { quality = AudioQuality.MEDIUM }
                    QualityOption("Baja (~50 kbps)", quality == AudioQuality.LOW) { quality = AudioQuality.LOW }
                } else {
                    // YouTube solo ofrece AAC a ~128 kbps y, a veces, ~48 kbps
                    QualityOption("Alta (~128 kbps)", quality != AudioQuality.LOW) { quality = AudioQuality.HIGH }
                    QualityOption("Baja (~48 kbps, si está disponible)", quality == AudioQuality.LOW) { quality = AudioQuality.LOW }
                }
                if (isCollection) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { autoUpdate = !autoUpdate },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Actualizar automáticamente", Modifier.weight(1f))
                        Switch(autoUpdate, { autoUpdate = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = treeUri != null,
                onClick = { treeUri?.let { onConfirm(DownloadChoice(it, format, quality, autoUpdate)) } },
            ) { Text("Descargar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun QualityOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
