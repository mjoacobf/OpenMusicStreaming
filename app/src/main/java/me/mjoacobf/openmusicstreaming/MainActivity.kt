package me.mjoacobf.openmusicstreaming

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import me.mjoacobf.openmusicstreaming.core.data.ParsedLink
import me.mjoacobf.openmusicstreaming.core.data.SettingsRepository
import me.mjoacobf.openmusicstreaming.core.data.StreamingRepository
import me.mjoacobf.openmusicstreaming.core.designsystem.OmsTheme
import me.mjoacobf.openmusicstreaming.core.media.PlaybackService
import me.mjoacobf.openmusicstreaming.core.model.AppSettings
import me.mjoacobf.openmusicstreaming.core.navigation.NowPlayingRoute
import me.mjoacobf.openmusicstreaming.core.navigation.RemoteCollectionRoute
import me.mjoacobf.openmusicstreaming.core.navigation.StreamDetailRoute
import me.mjoacobf.openmusicstreaming.navigation.OmsApp
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by inject()
    private val streamingRepository: StreamingRepository by inject()
    private val navigationRequests = Channel<NavKey>(Channel.BUFFERED)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        requestNotificationPermission()

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            OmsTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack,
            ) {
                OmsApp(navigationRequests.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        // Al tocar la notificación del reproductor se abre la pantalla de reproducción
        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false)) {
            navigationRequests.trySend(NowPlayingRoute)
            return
        }
        // Enlace de YouTube compartido desde otra app
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = Regex("https?://\\S+").find(text)?.value ?: return
            when (val link = streamingRepository.parseLink(url)) {
                is ParsedLink.Stream -> navigationRequests.trySend(StreamDetailRoute(link.url))
                is ParsedLink.Collection -> navigationRequests.trySend(RemoteCollectionRoute(link.url))
                null -> Unit
            }
        }
    }

    /** Android 13+: necesario para la notificación de reproducción y de descargas. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
