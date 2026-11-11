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
import jp.achi.homelauncher.media.MediaHelper
import jp.achi.homelauncher.ui.DrawerScreen
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
        // ホームボタンで戻ってきたら、一覧・設定を閉じる
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal.intValue++
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
        HomeScreen(
            vm = vm,
            widgets = widgets,
            media = media,
            resetSignal = homeSignal,
            onOpenDrawer = { showDrawer = true },
            onOpenSettings = { showSettings = true },
            onRequestNotificationAccess = onRequestNotificationAccess,
        )
        AnimatedVisibility(
            visible = showDrawer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            DrawerScreen(vm = vm, onClose = { showDrawer = false })
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
