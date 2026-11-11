package jp.achi.homelauncher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * インストール済みの起動可能アプリを列挙し、パッケージの増減に追従する。
 * PackageManager を使うので、デフォルトのホームに設定する前でも動作する。
 */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    fun start() {
        refresh()
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
    }

    fun refresh() {
        executor.execute {
            val list = runCatching { queryApps() }.getOrNull() ?: return@execute
            _apps.value = list
        }
    }

    @Suppress("DEPRECATION")
    private fun queryApps(): List<AppInfo> {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        return resolved
            .filter { it.activityInfo.packageName != appContext.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val updated = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
                AppInfo(
                    label = ri.loadLabel(pm)?.toString().orEmpty().ifEmpty { pkg },
                    packageName = pkg,
                    className = ri.activityInfo.name,
                    updatedAt = updated,
                )
            }
            .distinctBy { it.key }
    }

    /** アプリを起動。失敗(アンインストール直後など)しても例外は外に出さない。 */
    fun launch(context: Context, app: AppInfo): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
