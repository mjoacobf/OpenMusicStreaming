/*
 * Adaptado de NewPipe (org.schabi.newpipe.util.potoken.PoTokenWebView), GPLv3.
 * Se reemplazó RxJava por CompletableFuture para no añadir dependencias.
 */
package me.mjoacobf.openmusicstreaming.core.extractor.potoken

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import me.mjoacobf.openmusicstreaming.core.extractor.NewPipeDownloader
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

interface PoTokenGenerator : AutoCloseable {
    fun generatePoToken(identifier: String): CompletableFuture<String>
    fun isExpired(): Boolean
}

class PoTokenWebView private constructor(
    context: Context,
    private val downloader: NewPipeDownloader,
    private val generatorFuture: CompletableFuture<PoTokenGenerator>,
) : PoTokenGenerator {
    private val webView = WebView(context)
    private val poTokenFutures = mutableListOf<Pair<String, CompletableFuture<String>>>()
    private lateinit var expirationInstant: Instant

    init {
        val settings = webView.settings
        @SuppressLint("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, false)
        }
        settings.userAgentString = USER_AGENT
        settings.blockNetworkLoads = true // el WebView no necesita internet
        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.message().contains("Uncaught")) {
                    val fmt = "\"${m.message()}\", source: ${m.sourceId()} (${m.lineNumber()})"
                    val exception = BadWebViewException(fmt)
                    Log.e(TAG, "This WebView implementation is broken: $fmt")
                    onInitializationErrorCloseAndCancel(exception)
                    popAll().forEach { (_, f) -> f.completeExceptionally(exception) }
                }
                return super.onConsoleMessage(m)
            }
        }
    }

    private fun loadHtmlAndObtainBotguard(context: Context) {
        ioExecutor.execute {
            try {
                val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                mainHandler.post {
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        html.replaceFirst(
                            "</script>",
                            "\n$JS_INTERFACE.downloadAndRunBotguard()</script>",
                        ),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            } catch (e: Exception) {
                onInitializationErrorCloseAndCancel(e)
            }
        }
    }

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            "[ \"$REQUEST_KEY\" ]",
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null,
            )
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(error))
    }

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)
            // 10 minutos de margen
            expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds - 600)
            webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                generatorFuture.complete(this)
            }
        }
    }

    override fun generatePoToken(identifier: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val posted = mainHandler.post {
            synchronized(poTokenFutures) { poTokenFutures.add(identifier to future) }
            val u8Identifier = stringToU8(identifier)
            webView.evaluateJavascript(
                """try {
                    identifier = "$identifier"
                    u8Identifier = $u8Identifier
                    poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                    poTokenU8String = ""
                    for (i = 0; i < poTokenU8.length; i++) {
                        if (i != 0) poTokenU8String += ","
                        poTokenU8String += poTokenU8[i]
                    }
                    $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                } catch (error) {
                    $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                }""",
            ) {}
        }
        if (!posted) future.completeExceptionally(PoTokenException("Could not run on main thread"))
        return future
    }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        pop(identifier)?.completeExceptionally(buildExceptionForJsError(error))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val future = pop(identifier) ?: return
        try {
            future.complete(u8ToBase64(poTokenU8))
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }

    override fun isExpired(): Boolean = Instant.now().isAfter(expirationInstant)

    private fun pop(identifier: String): CompletableFuture<String>? = synchronized(poTokenFutures) {
        poTokenFutures.indexOfFirst { it.first == identifier }
            .takeIf { it >= 0 }
            ?.let { poTokenFutures.removeAt(it).second }
    }

    private fun popAll(): List<Pair<String, CompletableFuture<String>>> = synchronized(poTokenFutures) {
        poTokenFutures.toList().also { poTokenFutures.clear() }
    }

    /** POST a los servicios de BotGuard; solo se usa durante la inicialización. */
    private fun makeBotguardServiceRequest(url: String, data: String, handle: (String) -> Unit) {
        ioExecutor.execute {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(data.toByteArray().toRequestBody("application/json+protobuf".toMediaType()))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("x-goog-api-key", GOOGLE_API_KEY)
                    .header("x-user-agent", "grpc-web-javascript/0.1")
                    .build()
                downloader.client.newCall(request).execute().use { response ->
                    if (response.code != 200) {
                        onInitializationErrorCloseAndCancel(
                            PoTokenException("Invalid response code: ${response.code}"),
                        )
                        return@execute
                    }
                    val body = response.body.string()
                    mainHandler.post {
                        try {
                            handle(body)
                        } catch (e: Exception) {
                            onInitializationErrorCloseAndCancel(e)
                        }
                    }
                }
            } catch (e: Exception) {
                onInitializationErrorCloseAndCancel(e)
            }
        }
    }

    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        mainHandler.post {
            close()
            generatorFuture.completeExceptionally(error)
        }
    }

    @MainThread
    override fun close() {
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    companion object {
        private const val TAG = "PoTokenWebView"
        // Clave pública usada por BotGuard (obtenida por NewPipe observando sus peticiones)
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        private val mainHandler = Handler(Looper.getMainLooper())
        private val ioExecutor = Executors.newCachedThreadPool()

        fun newPoTokenGenerator(
            context: Context,
            downloader: NewPipeDownloader,
        ): CompletableFuture<PoTokenGenerator> {
            val future = CompletableFuture<PoTokenGenerator>()
            val posted = mainHandler.post {
                try {
                    val potWv = PoTokenWebView(context, downloader, future)
                    potWv.loadHtmlAndObtainBotguard(context)
                } catch (e: Throwable) {
                    // p. ej. WebView no instalado / deshabilitado
                    future.completeExceptionally(BadWebViewException(e.toString()))
                }
            }
            if (!posted) future.completeExceptionally(PoTokenException("Could not run on main thread"))
            return future
        }
    }
}
