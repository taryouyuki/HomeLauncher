package jp.achi.homelauncher.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知アクセスの許可を得るためのサービス。
 * 許可されると MediaSessionManager.getActiveSessions() でこのコンポーネントを指定でき、
 * 通知の数(アイコンのバッジ)と内容も取得できる。
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        BadgeStore.service = this
        BadgeStore.update(this)
    }

    override fun onListenerDisconnected() {
        BadgeStore.service = null
        BadgeStore.clear()
    }

    // 通知が連続して来ても、まとめて1回だけ集計する(電池・CPUの節約)
    override fun onNotificationPosted(sbn: StatusBarNotification?) = BadgeStore.requestUpdate(this)

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = BadgeStore.requestUpdate(this)
}
