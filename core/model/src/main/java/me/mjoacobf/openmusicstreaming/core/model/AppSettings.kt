package me.mjoacobf.openmusicstreaming.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Calidad de audio. En descargas: HIGH = Opus ~160 kbps, MEDIUM = ~70 kbps, LOW = ~50 kbps. */
enum class AudioQuality { HIGH, MEDIUM, LOW }

/** Formato de descarga: Opus (menor tamaño) o M4A/AAC (máxima compatibilidad, como MP3). */
enum class DownloadFormat { OPUS, M4A }

enum class VideoQuality(val height: Int) { P144(144), P360(360), P480(480), P720(720), P1080(1080) }

enum class RepeatModeSetting { OFF, ALL, ONE }

/**
 * Todas las preferencias de la app. Se guardan automáticamente (DataStore) en cada cambio
 * y se pueden exportar/importar como JSON.
 */
@Serializable
data class AppSettings(
    // Apariencia
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,

    // Descargas
    val downloadTreeUri: String? = null,
    val downloadFormat: DownloadFormat = DownloadFormat.OPUS,
    val downloadAudioQuality: AudioQuality = AudioQuality.HIGH,
    val embedCoverArt: Boolean = true,
    val writeM3uPlaylists: Boolean = true,
    val maxParallelDownloads: Int = 2,
    val downloadOnlyOnWifi: Boolean = false,

    // Actualización automática de playlists
    val autoUpdateIntervalHours: Int = 12,
    val autoUpdateWifiOnly: Boolean = true,

    // Streaming
    val streamAudioQuality: AudioQuality = AudioQuality.HIGH,
    val videoQuality: VideoQuality = VideoQuality.P480,
    val dataSaverOnMobile: Boolean = true,
    val autoplayRelated: Boolean = true,
    val saveSearchHistory: Boolean = true,
    val contentCountry: String = "",

    // Reproducción
    val pauseOnAudioDeviceChange: Boolean = true,
    val savePlayHistory: Boolean = true,
    val skipSilence: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatModeSetting = RepeatModeSetting.OFF,
    val previousRestartsAfterSec: Int = 3,

    // Actualizaciones de la app
    val checkForUpdates: Boolean = true,
    /** Última versión que el usuario rechazó (no se vuelve a avisar de esa). */
    val dismissedUpdateVersion: String = "",
) {
    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            coerceInputValues = true
        }

        fun fromJson(text: String): AppSettings = json.decodeFromString(serializer(), text)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)
}
