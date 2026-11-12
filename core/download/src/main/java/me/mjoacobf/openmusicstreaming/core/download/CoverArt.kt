package me.mjoacobf.openmusicstreaming.core.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.Image
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Genera la carátula embebida: recorta la miniatura al centro en formato cuadrado
 * (en los videos "Topic" de YouTube Music coincide con la portada del álbum),
 * la reduce a 360 px y la codifica como bloque FLAC `METADATA_BLOCK_PICTURE`.
 */
internal class CoverArt(private val client: OkHttpClient) {

    fun pickThumbnail(images: List<Image>): String? {
        val wide = images.filter { it.width > 0 && it.height > 0 && it.width * 9 == it.height * 16 }
        return (wide.filter { it.width >= 640 }.minByOrNull { it.width }
            ?: wide.maxByOrNull { it.width }
            ?: images.maxByOrNull { it.width })?.url
    }

    fun download(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body.bytes()
        }
    }.getOrNull()

    /** Devuelve el JPEG cuadrado o null si falla. */
    fun squareJpeg(source: ByteArray, size: Int = 360, quality: Int = 80): ByteArray? = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        val side = min(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - side) / 2,
            (bitmap.height - side) / 2,
            side,
            side,
        )
        val scaled = if (side > size) Bitmap.createScaledBitmap(cropped, size, size, true) else cropped
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== cropped) scaled.recycle()
        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()
        out.toByteArray()
    }.getOrNull()

    /** Valor de la etiqueta Vorbis `METADATA_BLOCK_PICTURE` (base64). */
    fun pictureBlockBase64(jpeg: ByteArray, size: Int): String {
        val mime = "image/jpeg".toByteArray()
        val buffer = ByteBuffer.allocate(32 + mime.size + jpeg.size)
        buffer.putInt(3) // portada frontal
        buffer.putInt(mime.size)
        buffer.put(mime)
        buffer.putInt(0) // sin descripción
        buffer.putInt(size)
        buffer.putInt(size)
        buffer.putInt(24)
        buffer.putInt(0)
        buffer.putInt(jpeg.size)
        buffer.put(jpeg)
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }
}
