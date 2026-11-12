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

    override fun onNotificationPosted(sbn: StatusBarNotification?) = BadgeStore.update(this)

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = BadgeStore.update(this)
}
