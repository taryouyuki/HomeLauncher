package jp.achi.homelauncher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
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

    private val _workApps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** 仕事用プロファイル(Island など)のアプリ。LauncherApps で取得する。 */
    val workApps: StateFlow<List<AppInfo>> = _workApps.asStateFlow()

    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    private val profileCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refreshWork()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refreshWork()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refreshWork()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refreshWork()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refreshWork()
    }

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    fun start() {
        refresh()
        runCatching { launcherApps?.registerCallback(profileCallback) }
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
        runCatching { launcherApps?.unregisterCallback(profileCallback) }
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
    }

    fun refreshWork() {
        executor.execute {
            _workApps.value = runCatching { queryWorkApps() }.getOrDefault(emptyList())
        }
    }

    /** 自分以外のプロファイル(仕事用)の起動可能アプリ。 */
    private fun queryWorkApps(): List<AppInfo> {
        val la = launcherApps ?: return emptyList()
        val um = userManager ?: return emptyList()
        val me = Process.myUserHandle()
        return la.profiles.filter { it != me }.flatMap { user ->
            val serial = um.getSerialNumberForUser(user)
            la.getActivityList(null, user).map { info ->
                AppInfo(
                    label = info.label?.toString().orEmpty().ifEmpty { info.componentName.packageName },
                    packageName = info.componentName.packageName,
                    className = info.componentName.className,
                    updatedAt = 0L,
                    installedAt = info.firstInstallTime,
                    work = true,
                    userSerial = serial,
                )
            }
        }.distinctBy { it.key }
    }

    fun refresh() {
        refreshWork()
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
                val pkgInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
                val updated = pkgInfo?.lastUpdateTime ?: 0L
                val installed = pkgInfo?.firstInstallTime ?: 0L
                AppInfo(
                    label = ri.loadLabel(pm)?.toString().orEmpty().ifEmpty { pkg },
                    packageName = pkg,
                    className = ri.activityInfo.name,
                    updatedAt = updated,
                    installedAt = installed,
                )
            }
            .distinctBy { it.key }
    }

    /** アプリを起動。失敗(アンインストール直後など)しても例外は外に出さない。 */
    fun launch(context: Context, app: AppInfo): Boolean = runCatching {
        if (app.work) {
            val user = userManager?.getUserForSerialNumber(app.userSerial) ?: return false
            launcherApps?.startMainActivity(ComponentName(app.packageName, app.className), user, null, null)
            return true
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
