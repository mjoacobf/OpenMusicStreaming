package me.mjoacobf.openmusicstreaming.core.extractor

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import kotlin.math.min

/**
 * DataSource para URIs `ytstream://...`:
 *  1. Resuelve la URL real de googlevideo con [StreamResolver] (con caché).
 *  2. Pide el contenido en bloques usando el parámetro `range` y POST, igual que los
 *     clientes oficiales y que `YoutubeHttpDataSource` de NewPipe, evitando el
 *     estrangulamiento de velocidad de YouTube.
 *  3. Si la URL caducó (HTTP 403) vuelve a resolverla una vez.
 *
 * Lo usan tanto el reproductor (streaming) como el gestor de descargas.
 */
@UnstableApi
class YoutubeDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val resolver: StreamResolver,
    private val chunkSize: Long,
) : BaseDataSource(true) {

    private var upstream: DataSource? = null
    private var dataSpec: DataSpec? = null
    private var resolved: ResolvedStream? = null
    private var position = 0L
    private var end = C.LENGTH_UNSET.toLong()
    private var requestNumber = 0
    private var refreshed = false
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        refreshed = false
        transferInitializing(dataSpec)
        val target = resolver.resolve(dataSpec.uri, forceRefresh = false)
        resolved = target
        position = dataSpec.position
        end = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> position + dataSpec.length
            target.contentLength > 0 -> target.contentLength
            else -> C.LENGTH_UNSET.toLong()
        }
        openChunk()
        opened = true
        transferStarted(dataSpec)
        return if (end > 0) end - position else C.LENGTH_UNSET.toLong()
    }

    private fun openChunk() {
        val target = resolved ?: error("Not resolved")
        val chunkEnd = if (end > 0) min(end, position + chunkSize) else C.LENGTH_UNSET.toLong()
        var url = target.url
        if (!url.contains(RN_PARAMETER)) {
            url += RN_PARAMETER + requestNumber++
        }
        if (position > 0 || chunkEnd > 0) {
            url += "&range=$position-" + if (chunkEnd > 0) (chunkEnd - 1).toString() else ""
        }
        val spec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(POST_BODY)
            .setHttpRequestHeaders(headersFor(url))
            .build()
        val source = upstreamFactory.createDataSource()
        try {
            source.open(spec)
            upstream = source
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            runCatching { source.close() }
            if (!refreshed && (e.responseCode == 403 || e.responseCode == 410)) {
                // La URL caducó o fue invalidada: se vuelve a extraer una sola vez
                refreshed = true
                resolved = resolver.resolve(dataSpec!!.uri, forceRefresh = true)
                openChunk()
            } else {
                throw e
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (end > 0 && position >= end) return C.RESULT_END_OF_INPUT
        var source = upstream ?: return C.RESULT_END_OF_INPUT
        val toRead = if (end > 0) min(length.toLong(), end - position).toInt() else length
        var read = source.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            if (end > 0 && position < end) {
                // Fin del bloque actual: se abre el siguiente
                source.close()
                upstream = null
                openChunk()
                source = upstream ?: return C.RESULT_END_OF_INPUT
                read = source.read(buffer, offset, toRead)
                if (read == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT
            } else {
                return C.RESULT_END_OF_INPUT
            }
        }
        position += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        try {
            upstream?.close()
        } finally {
            upstream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(
        client: OkHttpClient,
        private val resolver: StreamResolver,
        private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    ) : DataSource.Factory {
        private val upstream = OkHttpDataSource.Factory(client).setUserAgent(null)
        override fun createDataSource(): DataSource = YoutubeDataSource(upstream, resolver, chunkSize)
    }

    companion object {
        /** Bloques < 10 MB: YouTube limita la velocidad de peticiones más grandes. */
        const val DEFAULT_CHUNK_SIZE = 8L * 1024 * 1024
        private const val RN_PARAMETER = "&rn="
        private val POST_BODY = byteArrayOf(0x78, 0)
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com"

        private fun headersFor(url: String): Map<String, String> {
            val headers = HashMap<String, String>()
            if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
                headers["Origin"] = YOUTUBE_BASE_URL
                headers["Referer"] = YOUTUBE_BASE_URL
                headers["Sec-Fetch-Dest"] = "empty"
                headers["Sec-Fetch-Mode"] = "cors"
                headers["Sec-Fetch-Site"] = "cross-site"
            }
            headers["TE"] = "trailers"
            headers["User-Agent"] = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
                YoutubeParsingHelper.getVisionOsUserAgent(null)
            } else {
                NewPipeDownloader.USER_AGENT
            }
            headers["Accept-Encoding"] = "identity"
            return headers
        }
    }
}
