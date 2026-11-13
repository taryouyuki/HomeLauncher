package jp.achi.homelauncher.media

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 通知アクセスで取得した通知を、パッケージごとに保持する(アイコンのバッジ・通知の表示用)。 */
object BadgeStore {
    data class Notif(
        val key: String,
        val pkg: String,
        val title: String,
        val text: String,
        val time: Long,
        val intent: PendingIntent?,
        val clearable: Boolean,
        /** 返信できる通知なら、その返信アクション(通知を消した後は使えないことがある) */
        val reply: Notification.Action? = null,
    )

    private val _notifs = MutableStateFlow<Map<String, List<Notif>>>(emptyMap())
    val notifs: StateFlow<Map<String, List<Notif>>> = _notifs.asStateFlow()

    /** 通知を消しても残す履歴(クイックパネル用)。「クリア」を押すまで残る。 */
    private val _history = MutableStateFlow<Map<String, List<Notif>>>(emptyMap())
    val history: StateFlow<Map<String, List<Notif>>> = _history.asStateFlow()

    fun clearHistory(pkg: String? = null) {
        _history.value = if (pkg == null) emptyMap() else _history.value - pkg
    }

    /** 返信できる通知に、入力した文章を送る。成功したら true。 */
    fun reply(context: android.content.Context, n: Notif, text: String): Boolean {
        val action = n.reply ?: return false
        val ri = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return false
        val intent = android.content.Intent()
        val bundle = android.os.Bundle().apply { putCharSequence(ri.resultKey, text) }
        android.app.RemoteInput.addResultsToIntent(arrayOf(ri), intent, bundle)
        return runCatching { action.actionIntent.send(context, 0, intent) }.isSuccess
    }

    @Volatile
    var service: NotificationListenerService? = null

    fun refresh() {
        service?.let { update(it) }
    }

    fun clear() {
        _notifs.value = emptyMap()
    }

    fun update(svc: NotificationListenerService) {
        val active = runCatching { svc.activeNotifications }.getOrNull() ?: return
        val fresh = active
            .filter { sbn ->
                !sbn.isOngoing &&
                    (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    sbn.packageName != svc.packageName
            }
            .map { sbn ->
                val ex = sbn.notification.extras
                Notif(
                    key = sbn.key,
                    pkg = sbn.packageName,
                    title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = (ex?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex?.getCharSequence(Notification.EXTRA_TEXT))
                        ?.toString().orEmpty(),
                    time = sbn.postTime,
                    intent = sbn.notification.contentIntent,
                    clearable = sbn.isClearable,
                    reply = sbn.notification.actions?.firstOrNull { a ->
                        a.remoteInputs?.any { it.allowFreeFormInput } == true
                    },
                )
            }
            .sortedByDescending { it.time }
        _notifs.value = fresh.groupBy { it.pkg }
        // 履歴に追加(同じ通知は新しい内容で置き換え。1アプリ30件まで)
        if (fresh.isNotEmpty()) {
            val h = _history.value.toMutableMap()
            for ((pkg, list) in fresh.groupBy { it.pkg }) {
                val old = h[pkg].orEmpty().filter { o -> list.none { it.key == o.key } }
                h[pkg] = (list + old).sortedByDescending { it.time }.take(30)
            }
            _history.value = h
        }
    }

    fun dismiss(key: String) {
        runCatching { service?.cancelNotification(key) }
    }
}
