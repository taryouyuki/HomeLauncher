package jp.achi.homelauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle

/**
 * ブラウザなどが ShortcutManager.requestPinShortcut で「ホーム画面に追加」を要求したときに、
 * システムから呼ばれる受け口(デフォルトのホームアプリのみ)。受け付けて MainActivity に配置を依頼する。
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val la = getSystemService(LauncherApps::class.java)
            val request = la?.getPinItemRequest(intent)
            if (request != null && request.isValid &&
                request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
            ) {
                val info = request.shortcutInfo
                if (info != null && request.accept()) {
                    val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty()
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(MainActivity.EXTRA_PIN_PKG, info.`package`)
                            .putExtra(MainActivity.EXTRA_PIN_ID, info.id)
                            .putExtra(MainActivity.EXTRA_PIN_LABEL, label),
                    )
                }
            }
        }
        finish()
    }
}
