package me.mjoacobf.openmusicstreaming.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import me.mjoacobf.openmusicstreaming.core.extractor.StreamResolver
import me.mjoacobf.openmusicstreaming.core.extractor.YoutubeDataSource

/** Envía `ytstream://` a [YoutubeDataSource] y el resto (archivos locales) a DefaultDataSource. */
@UnstableApi
class RoutingDataSource(
    private val youtube: DataSource,
    private val default: DataSource,
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        youtube.addTransferListener(transferListener)
        default.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target = if (dataSpec.uri.scheme == StreamResolver.SCHEME) youtube else default
        current = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: error("DataSource not opened")

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    class Factory(
        context: Context,
        private val youtubeFactory: YoutubeDataSource.Factory,
    ) : DataSource.Factory {
        private val defaultFactory = DefaultDataSource.Factory(context)
        override fun createDataSource(): DataSource =
            RoutingDataSource(youtubeFactory.createDataSource(), defaultFactory.createDataSource())
    }
}
