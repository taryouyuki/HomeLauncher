package jp.achi.homelauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import jp.achi.homelauncher.data.LauncherPrefs

/**
 * ブラウザなどが ShortcutManager.requestPinShortcut で「ホーム画面に追加」を要求したときに、
 * システムから呼ばれる受け口(デフォルトのホームアプリのみ)。
 * 受け付けた内容は一度ストレージに積み、MainActivity が配置する(画面が閉じていても取りこぼさない)。
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var message: String? = null
        try {
            val la = getSystemService(LauncherApps::class.java)
            val request = la?.getPinItemRequest(intent)
            when {
                request == null -> message = "追加の要求を受け取れませんでした"
                !request.isValid -> message = "追加の要求の有効期限が切れました。もう一度お試しください"
                request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                    message = "このアイテムはホーム画面に追加できません"
                else -> {
                    val info = request.shortcutInfo
                    if (info == null) {
                        message = "ショートカット情報を取得できませんでした"
                    } else if (!request.accept()) {
                        message = "ショートカットを追加できませんでした"
                    } else {
                        val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty()
                        LauncherPrefs(this).pushPin(info.`package`, info.id, label)
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            message = "追加に失敗しました: ${e.javaClass.simpleName}"
        }
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        finish()
    }
}
