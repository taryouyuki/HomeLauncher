package jp.achi.homelauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.achi.homelauncher.data.PlaceResult
import jp.achi.homelauncher.media.MediaHelper
import jp.achi.homelauncher.ui.DrawerScreen
import jp.achi.homelauncher.ui.HomeColorTheme
import jp.achi.homelauncher.ui.HomeLauncherTheme
import jp.achi.homelauncher.ui.HomeScreen
import jp.achi.homelauncher.ui.SettingsScreen
import jp.achi.homelauncher.ui.WidgetController
import jp.achi.homelauncher.ui.toast

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgets: WidgetController
    private lateinit var media: MediaHelper
    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgets = WidgetController(this)
        media = MediaHelper(this)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            HomeLauncherTheme(settings) {
                LauncherRoot(
                    vm = vm,
                    widgets = widgets,
                    media = media,
                    homeSignal = homeSignal.intValue,
                    onRequestNotificationAccess = ::openNotificationAccessSettings,
                )
            }
        }
        handlePinShortcut(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.repo.start()
        widgets.start()
    }

    override fun onResume() {
        super.onResume()
        // 通知アクセス設定から戻った直後に許可状態を反映する
        media.start()
    }

    override fun onPause() {
        media.stop()
        super.onPause()
    }

    override fun onStop() {
        widgets.stop()
        vm.repo.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // ホームボタンで戻ってきたら、一覧・設定を閉じる
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal.intValue++
        handlePinShortcut(intent)
    }

    /** PinShortcutActivity から渡された「ホーム画面に追加」を配置する。 */
    private fun handlePinShortcut(intent: Intent?) {
        val pkg = intent?.getStringExtra(EXTRA_PIN_PKG) ?: return
        val id = intent.getStringExtra(EXTRA_PIN_ID) ?: return
        val label = intent.getStringExtra(EXTRA_PIN_LABEL).orEmpty()
        intent.removeExtra(EXTRA_PIN_PKG)
        intent.removeExtra(EXTRA_PIN_ID)
        intent.removeExtra(EXTRA_PIN_LABEL)
        val r = vm.addShortcut(pkg, id, label)
        toast(this, if (r == PlaceResult.FAILED) "ホーム画面に空きがありません" else "ホーム画面に追加しました")
    }

    companion object {
        const val EXTRA_PIN_PKG = "pin_pkg"
        const val EXTRA_PIN_ID = "pin_id"
        const val EXTRA_PIN_LABEL = "pin_label"
    }

    @Deprecated("AppWidgetHost のバインド/設定結果の受け取りに使用")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onActivityResult(this, requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun openNotificationAccessSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.onFailure { toast(this, "通知アクセスの設定を開けません") }
    }
}

@Composable
private fun LauncherRoot(
    vm: LauncherViewModel,
    widgets: WidgetController,
    media: MediaHelper,
    homeSignal: Int,
    onRequestNotificationAccess: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // ホームボタン押下で閉じる
    androidx.compose.runtime.LaunchedEffect(homeSignal) {
        showDrawer = false
        showSettings = false
    }

    BackHandler(enabled = showSettings || showDrawer) {
        if (showSettings) showSettings = false else showDrawer = false
    }

    Box(Modifier.fillMaxSize()) {
        // ホーム画面とアプリ一覧は、設定画面とは別の色(好きな色)にできる
        HomeColorTheme(settings) {
            HomeScreen(
                vm = vm,
                widgets = widgets,
                media = media,
                resetSignal = homeSignal,
                onOpenDrawer = { showDrawer = true },
                onOpenSettings = { showSettings = true },
                onRequestNotificationAccess = onRequestNotificationAccess,
            )
        }
        AnimatedVisibility(
            visible = showDrawer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            HomeColorTheme(settings) {
                DrawerScreen(vm = vm, onClose = { showDrawer = false })
            }
        }
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            SettingsScreen(
                vm = vm,
                widgets = widgets,
                media = media,
                onClose = { showSettings = false },
                onRequestNotificationAccess = onRequestNotificationAccess,
            )
        }
    }
}
