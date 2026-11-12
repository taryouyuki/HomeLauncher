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
    )

    private val _notifs = MutableStateFlow<Map<String, List<Notif>>>(emptyMap())
    val notifs: StateFlow<Map<String, List<Notif>>> = _notifs.asStateFlow()

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
        _notifs.value = active
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
                )
            }
            .sortedByDescending { it.time }
            .groupBy { it.pkg }
    }

    fun dismiss(key: String) {
        runCatching { service?.cancelNotification(key) }
    }
}
