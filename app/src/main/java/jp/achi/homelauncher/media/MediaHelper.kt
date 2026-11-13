package jp.achi.homelauncher.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** アクティブなメディアセッションを監視し、再生/一時停止/次/前を操作する。 */
class MediaHelper(context: Context) {
    data class State(
        val permitted: Boolean = false,
        val hasSession: Boolean = false,
        val title: String? = null,
        val artist: String? = null,
        val playing: Boolean = false,
        /** 再生中のアプリ名 */
        val appName: String? = null,
        /** 出力先(スピーカー / Bluetooth: 名前 / イヤホン 等) */
        val output: String? = null,
    )

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val component = ComponentName(appContext, MediaListenerService::class.java)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var listening = false

    private val deviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) = publish()
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) = publish()
    }
    private var deviceCallbackRegistered = false

    /** 現在の出力先。Bluetooth・有線イヤホン・USB が接続されていればそれを、なければスピーカー。 */
    private fun outputLabel(): String? = runCatching {
        val devices = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        val bt = devices.firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
        }
        val wired = devices.firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        }
        when {
            bt != null -> "Bluetooth" + (bt.productName?.toString()?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
            wired != null -> "イヤホン"
            else -> "スピーカー"
        }
    }.getOrNull()

    private fun appLabel(pkg: String?): String? = pkg?.let {
        runCatching {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
        }.getOrNull()
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = publish()
        override fun onSessionDestroyed() = attach(null)
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list) }

    fun isPermitted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)

    fun start() {
        stop()
        val mgr = manager
        if (!isPermitted() || mgr == null) {
            _state.value = State(permitted = false)
            return
        }
        runCatching {
            if (!deviceCallbackRegistered) {
                audio?.registerAudioDeviceCallback(deviceCallback, null)
                deviceCallbackRegistered = true
            }
            mgr.addOnActiveSessionsChangedListener(sessionsListener, component)
            listening = true
            attach(mgr.getActiveSessions(component))
        }.onFailure {
            // 許可の取り消し直後などで SecurityException になることがある
            _state.value = State(permitted = false)
        }
    }

    fun stop() {
        if (listening) {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
        }
        if (deviceCallbackRegistered) {
            runCatching { audio?.unregisterAudioDeviceCallback(deviceCallback) }
            deviceCallbackRegistered = false
        }
        runCatching { controller?.unregisterCallback(controllerCallback) }
        controller = null
    }

    private fun attach(list: List<MediaController>?) {
        runCatching { controller?.unregisterCallback(controllerCallback) }
        controller = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        runCatching { controller?.registerCallback(controllerCallback) }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = State(permitted = isPermitted(), output = outputLabel())
            return
        }
        val meta = runCatching { c.metadata }.getOrNull()
        _state.value = State(
            permitted = true,
            hasSession = true,
            title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
            artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            appName = appLabel(c.packageName),
            output = outputLabel(),
        )
    }

    fun playPause() {
        val c = controller
        if (c == null) {
            sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        runCatching {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        }
    }

    fun next() {
        val c = controller
        if (c == null) sendKey(KeyEvent.KEYCODE_MEDIA_NEXT) else runCatching { c.transportControls.skipToNext() }
    }

    fun previous() {
        val c = controller
        if (c == null) sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) else runCatching { c.transportControls.skipToPrevious() }
    }

    /** セッションを参照できないとき(通知アクセス未許可など)はメディアキーを送る。 */
    private fun sendKey(code: Int) {
        val a = audio ?: return
        runCatching {
            a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }
}
