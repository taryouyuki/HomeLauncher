package jp.achi.homelauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * AppWidgetHost のラッパー。バインド許可 → 設定画面 → 追加 の流れを Activity の結果コードで処理する。
 * すべての呼び出しは例外を握りつぶし、クラッシュしないようにしている。
 */
class WidgetController(context: Context) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)

    private class Pending(
        val id: Int,
        val info: AppWidgetProviderInfo,
        val skipConfigure: Boolean,
        val onDone: (Int, AppWidgetProviderInfo) -> Unit,
        val onCancel: () -> Unit,
    )

    private var pending: Pending? = null

    fun start() {
        runCatching { host.startListening() }
    }

    fun stop() {
        runCatching { host.stopListening() }
    }

    fun providers(): List<AppWidgetProviderInfo> =
        runCatching { manager.installedProviders }.getOrNull().orEmpty()

    fun labelOf(info: AppWidgetProviderInfo): String =
        runCatching { info.loadLabel(appContext.packageManager) }.getOrNull().orEmpty()
            .ifEmpty { info.provider.packageName }

    fun delete(widgetId: Int) {
        runCatching { host.deleteAppWidgetId(widgetId) }
    }

    fun createView(context: Context, widgetId: Int): AppWidgetHostView? = runCatching {
        val info = manager.getAppWidgetInfo(widgetId) ?: return null
        host.createView(context, widgetId, info)
    }.getOrNull()

    /**
     * ウィジェットを追加する。結果は onDone(widgetId, info) で返る。
     * skipConfigure=true は復元時の再バインド用(設定画面を出さない)。
     */
    fun add(
        activity: Activity,
        info: AppWidgetProviderInfo,
        skipConfigure: Boolean = false,
        onCancel: () -> Unit = {},
        onDone: (Int, AppWidgetProviderInfo) -> Unit,
    ) {
        if (pending != null) {
            onCancel()
            return
        }
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull()
        if (id == null) {
            onCancel()
            return
        }
        pending = Pending(id, info, skipConfigure, onDone, onCancel)
        val allowed = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.provider) }.getOrDefault(false)
        if (allowed) {
            proceedConfigure(activity)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            runCatching { @Suppress("DEPRECATION") activity.startActivityForResult(intent, REQ_BIND) }
                .onFailure { cancelPending() }
        }
    }

    /** Activity.onActivityResult から呼ぶ。処理したら true。 */
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int): Boolean = when (requestCode) {
        REQ_BIND -> {
            if (resultCode == Activity.RESULT_OK) proceedConfigure(activity) else cancelPending()
            true
        }
        REQ_CONFIGURE -> {
            if (resultCode == Activity.RESULT_OK) finish() else cancelPending()
            true
        }
        else -> false
    }

    private fun proceedConfigure(activity: Activity) {
        val p = pending ?: return
        if (p.skipConfigure || p.info.configure == null) {
            finish()
            return
        }
        runCatching {
            host.startAppWidgetConfigureActivityForResult(activity, p.id, 0, REQ_CONFIGURE, null)
        }.onFailure {
            // 設定画面を開けないウィジェットは、設定なしで追加する
            finish()
        }
    }

    private fun finish() {
        val p = pending ?: return
        pending = null
        p.onDone(p.id, p.info)
    }

    private fun cancelPending() {
        val p = pending ?: return
        pending = null
        delete(p.id)
        p.onCancel()
    }

    fun findProvider(flattened: String): AppWidgetProviderInfo? =
        providers().firstOrNull { it.provider.flattenToString() == flattened }

    companion object {
        private const val HOST_ID = 1024
        private const val REQ_BIND = 7001
        private const val REQ_CONFIGURE = 7002

        fun component(flattened: String): ComponentName? = ComponentName.unflattenFromString(flattened)
    }
}
