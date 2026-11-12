package me.mjoacobf.openmusicstreaming.core.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Detecta la conexión o desconexión de audífonos cableados, USB o Bluetooth
 * (similar a Simple Music Player) y notifica para pausar la reproducción.
 * La desconexión también la cubre ACTION_AUDIO_BECOMING_NOISY en ExoPlayer.
 */
class AudioDeviceMonitor(
    context: Context,
    private val onChange: () -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var initialized = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            // La primera llamada lista los dispositivos ya conectados: se ignora
            if (!initialized) {
                initialized = true
                return
            }
            if (addedDevices.any { it.isHeadset() }) onChange()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.isHeadset() }) onChange()
        }
    }

    fun start() {
        initialized = false
        audioManager.registerAudioDeviceCallback(callback, handler)
        // Si no hay dispositivos, Android no invoca el callback inicial
        handler.postDelayed({ initialized = true }, 500)
    }

    fun stop() = audioManager.unregisterAudioDeviceCallback(callback)

    private fun AudioDeviceInfo.isHeadset(): Boolean {
        if (!isSink) return false
        val types = mutableSetOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            types += AudioDeviceInfo.TYPE_BLE_HEADSET
            types += AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        return type in types
    }
}
