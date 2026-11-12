package me.mjoacobf.openmusicstreaming.core.designsystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** URI inicial del selector: la carpeta pública "Music". */
private val MUSIC_FOLDER: Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music")

/**
 * Abre el selector de carpetas del sistema (empezando en "Music") y conserva el permiso
 * de escritura de forma persistente. Devuelve una función para lanzarlo.
 */
@Composable
fun rememberFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persist(context, uri)
            onPicked(uri)
        }
    }
    return { launcher.launch(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) MUSIC_FOLDER else null) }
}

private fun persist(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

/** Nombre legible de la carpeta elegida (p. ej. "Music/Descargas"). */
fun readableFolder(treeUri: String?): String {
    if (treeUri == null) return "Sin elegir"
    val docId = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)) }.getOrNull()
        ?: return treeUri
    val path = docId.substringAfter(':')
    val root = docId.substringBefore(':')
    return (if (root == "primary") "Almacenamiento interno" else root) + "/" + path
}
