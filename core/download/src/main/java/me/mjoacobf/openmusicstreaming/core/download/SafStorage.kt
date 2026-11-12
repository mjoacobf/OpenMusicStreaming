package me.mjoacobf.openmusicstreaming.core.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Acceso a la carpeta elegida por el usuario mediante el selector del sistema
 * (Storage Access Framework). Cada playlist se guarda en una subcarpeta con su nombre.
 */
class SafStorage(private val context: Context) {

    fun hasPermission(treeUri: String): Boolean {
        val uri = Uri.parse(treeUri)
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
    }

    /** Obtiene la carpeta guardada o la crea dentro del árbol. */
    fun ensureFolder(folderUri: String?, treeUri: String, name: String): DocumentFile {
        folderUri?.let { uri ->
            val existing = DocumentFile.fromTreeUri(context, Uri.parse(uri))
            if (existing != null && existing.exists() && existing.isDirectory) return existing
        }
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: error("Carpeta de descargas no válida")
        if (!root.exists()) error("La carpeta de descargas ya no existe")
        root.findFile(name)?.takeIf { it.isDirectory }?.let { return it }
        return root.createDirectory(name) ?: error("No se pudo crear la carpeta $name")
    }

    fun listNames(folder: DocumentFile): MutableSet<String> =
        folder.listFiles().mapNotNull { it.name }.toMutableSet()

    fun createFile(folder: DocumentFile, displayName: String): DocumentFile =
        // octet-stream evita que el proveedor añada otra extensión al nombre
        folder.createFile("application/octet-stream", displayName)
            ?: error("No se pudo crear $displayName")

    fun exists(uri: String): Boolean =
        runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uri))?.exists() == true }
            .getOrDefault(false)

    fun delete(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    /**
     * Escribe/actualiza `<playlist>.m3u8` para que otros reproductores vean el orden.
     * Las rutas son relativas a la carpeta de la playlist.
     */
    fun writeM3u(folder: DocumentFile, title: String, entries: List<Pair<String, Long>>, fileUris: List<String>) {
        val folderDocId = runCatching { DocumentsContract.getDocumentId(folder.uri) }.getOrNull() ?: return
        val sb = StringBuilder("#EXTM3U\n")
        fileUris.forEachIndexed { i, uri ->
            val docId = runCatching { DocumentsContract.getDocumentId(Uri.parse(uri)) }.getOrNull()
                ?: return@forEachIndexed
            val relative = relativePath(folderDocId, docId) ?: return@forEachIndexed
            val (name, duration) = entries[i]
            sb.append("#EXTINF:").append(duration).append(',').append(name).append('\n')
            sb.append(relative).append('\n')
        }
        val fileName = sanitizeFileName(title) + ".m3u8"
        val file = folder.findFile(fileName) ?: folder.createFile("application/octet-stream", fileName) ?: return
        context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(sb.toString()) }
    }

    /** Calcula la ruta relativa entre IDs de documento tipo `primary:Music/Lista/archivo.opus`. */
    private fun relativePath(fromDirId: String, toFileId: String): String? {
        val fromRoot = fromDirId.substringBefore(':')
        val toRoot = toFileId.substringBefore(':')
        if (fromRoot != toRoot) return null
        val from = fromDirId.substringAfter(':').split('/').filter { it.isNotEmpty() }
        val to = toFileId.substringAfter(':').split('/').filter { it.isNotEmpty() }
        var common = 0
        while (common < from.size && common < to.size - 1 && from[common] == to[common]) common++
        val ups = List(from.size - common) { ".." }
        return (ups + to.drop(common)).joinToString("/")
    }
}

private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

fun sanitizeFileName(name: String): String =
    name.replace(INVALID_CHARS, "_").trim().trimEnd('.').take(120).ifBlank { "Sin título" }
