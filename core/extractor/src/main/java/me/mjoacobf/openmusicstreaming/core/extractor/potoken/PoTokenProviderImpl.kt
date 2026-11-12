/*
 * Adaptado de NewPipe (org.schabi.newpipe.util.potoken.PoTokenProviderImpl), GPLv3.
 */
package me.mjoacobf.openmusicstreaming.core.extractor.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import me.mjoacobf.openmusicstreaming.core.extractor.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Genera poTokens de BotGuard en un WebView oculto para que YouTube no bloquee
 * las peticiones de reproducción ("Sign in to confirm you're not a bot").
 */
class PoTokenProviderImpl(
    private val context: Context,
    private val downloader: NewPipeDownloader,
) : PoTokenProvider {

    private val webViewSupported by lazy {
        context.packageManager.hasSystemFeature("android.software.webview")
    }

    @Volatile
    private var webViewBadImpl = false

    private val lock = Any()
    private var visitorData: String? = null
    private var streamingPot: String? = null
    private var generator: PoTokenGenerator? = null

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) return null
        return try {
            getWebClientPoToken(videoId, forceRecreate = false)
        } catch (e: BadWebViewException) {
            Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
            webViewBadImpl = true
            null
        }
    }

    private fun getWebClientPoToken(videoId: String, forceRecreate: Boolean): PoTokenResult {
        val recreated: Boolean
        val gen: PoTokenGenerator
        val vData: String
        val sPot: String
        synchronized(lock) {
            val shouldRecreate = generator == null || forceRecreate || generator!!.isExpired()
            if (shouldRecreate) {
                val clientInfo = InnertubeClientRequestInfo.ofWebClient()
                clientInfo.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
                visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                    clientInfo,
                    NewPipe.getPreferredLocalization(),
                    NewPipe.getPreferredContentCountry(),
                    YoutubeParsingHelper.getYouTubeHeaders(),
                    YoutubeParsingHelper.YOUTUBEI_V1_URL,
                    null,
                    false,
                )
                generator?.let { old -> Handler(Looper.getMainLooper()).post { old.close() } }
                generator = await(PoTokenWebView.newPoTokenGenerator(context, downloader), 60)
                // El poToken de streaming debe generarse exactamente una vez antes que los demás
                streamingPot = await(generator!!.generatePoToken(visitorData!!))
            }
            recreated = shouldRecreate
            gen = generator!!
            vData = visitorData!!
            sPot = streamingPot!!
        }

        val playerPot = try {
            await(gen.generatePoToken(videoId))
        } catch (t: Throwable) {
            if (recreated) throw t
            Log.w(TAG, "Failed to obtain poToken, retrying", t)
            return getWebClientPoToken(videoId, forceRecreate = true)
        }
        return PoTokenResult(vData, playerPot, sPot)
    }

    private fun <T> await(future: CompletableFuture<T>, seconds: Long = 30): T = try {
        future.get(seconds, TimeUnit.SECONDS)
    } catch (e: ExecutionException) {
        throw (e.cause as? Exception) ?: e
    }

    companion object {
        private const val TAG = "PoTokenProvider"
    }
}
