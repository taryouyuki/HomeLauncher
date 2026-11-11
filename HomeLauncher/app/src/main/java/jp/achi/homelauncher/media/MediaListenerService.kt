package jp.achi.homelauncher.media

import android.service.notification.NotificationListenerService

/**
 * 通知アクセスの許可を得るためだけの空のサービス。
 * 許可されると MediaSessionManager.getActiveSessions() でこのコンポーネントを指定できる。
 */
class MediaListenerService : NotificationListenerService()
