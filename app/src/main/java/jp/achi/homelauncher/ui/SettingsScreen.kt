package jp.achi.homelauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.achi.homelauncher.BuildConfig
import jp.achi.homelauncher.LauncherViewModel
import jp.achi.homelauncher.data.LauncherSettings
import jp.achi.homelauncher.data.SortMode
import jp.achi.homelauncher.media.MediaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: LauncherViewModel,
    widgets: WidgetController,
    media: MediaHelper,
    onClose: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by vm.settings.collectAsStateWithLifecycle()
    val mediaState by media.state.collectAsStateWithLifecycle()

    var confirmImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var searchUrlText by remember(s.searchUrl) { mutableStateOf(s.searchUrl) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = vm.exportBackup()
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        } ?: error("open failed")
                    }.isSuccess
                }
                toast(context, if (ok) "バックアップを保存しました" else "バックアップの保存に失敗しました")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImportUri = uri
    }

    fun update(t: (LauncherSettings) -> LauncherSettings) = vm.updateSettings(t)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text("ランチャー設定") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Section("ホーム画面")
                IntSliderRow("列数", s.gridCols, 3, 8) { v -> update { it.copy(gridCols = v) } }
                IntSliderRow("行数", s.gridRows, 3, 10) { v -> update { it.copy(gridRows = v) } }
                IntSliderRow("ページ数", s.pageCount, 1, 8) { v -> update { it.copy(pageCount = v) } }
                IntSliderRow("アイコンサイズ", s.iconSizeDp, 32, 96, "dp") { v -> update { it.copy(iconSizeDp = v) } }
                PercentSliderRow("アイコンの不透明度", s.iconAlpha, 0.1f, 1f) { v -> update { it.copy(iconAlpha = v) } }
                SwitchRow("アプリ名を表示", s.showLabels) { v -> update { it.copy(showLabels = v) } }
                IntSliderRow("アプリ名の文字サイズ", s.labelSizeSp, 8, 16, "sp") { v -> update { it.copy(labelSizeSp = v) } }
                SwitchRow("ドックを表示", s.showDock) { v -> update { it.copy(showDock = v) } }
                IntSliderRow("ドック1ページのアイコン数", s.dockCount, 3, 7) { v -> update { it.copy(dockCount = v) } }
                SwitchRow("レイアウトをロック(長押し編集を無効化)", s.lockLayout) { v -> update { it.copy(lockLayout = v) } }

                Section("アプリ一覧")
                IntSliderRow("列数", s.drawerCols, 3, 7) { v -> update { it.copy(drawerCols = v) } }
                IntSliderRow("アイコンサイズ", s.drawerIconSizeDp, 32, 96, "dp") { v -> update { it.copy(drawerIconSizeDp = v) } }
                Text("並び順", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(s.drawerSort == SortMode.NAME, { update { it.copy(drawerSort = SortMode.NAME) } }, { Text("名前順") })
                    FilterChip(s.drawerSort == SortMode.UPDATED, { update { it.copy(drawerSort = SortMode.UPDATED) } }, { Text("更新順") })
                    FilterChip(s.drawerSort == SortMode.LAUNCH, { update { it.copy(drawerSort = SortMode.LAUNCH) } }, { Text("起動順") })
                }

                Section("ウィジェット")
                PercentSliderRow("全体の不透明度", s.widgetGlobalAlpha) { v -> update { it.copy(widgetGlobalAlpha = v) } }
                Text(
                    "個別設定: ホームでウィジェット右上の「⋮」→「サイズ・透明度・位置を編集」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Section("外観")
                SwitchRow("システムの色(Dynamic Color)を使う", s.useDynamicColor) { v -> update { it.copy(useDynamicColor = v) } }
                Text("テーマ", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(s.darkTheme == "system", { update { it.copy(darkTheme = "system") } }, { Text("システム") })
                    FilterChip(s.darkTheme == "light", { update { it.copy(darkTheme = "light") } }, { Text("ライト") })
                    FilterChip(s.darkTheme == "dark", { update { it.copy(darkTheme = "dark") } }, { Text("ダーク") })
                }
                PercentSliderRow("ホーム背景の暗さ", s.scrimAlpha, 0f, 0.8f) { v -> update { it.copy(scrimAlpha = v) } }

                Section("操作")
                SwitchRow("長押しの振動", s.haptics) { v -> update { it.copy(haptics = v) } }
                OutlinedTextField(
                    value = searchUrlText,
                    onValueChange = { searchUrlText = it },
                    label = { Text("Web検索URL(%s が検索語に置換)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = {
                        if (searchUrlText.startsWith("http") && searchUrlText.contains("%s")) {
                            update { it.copy(searchUrl = searchUrlText) }
                            toast(context, "検索URLを保存しました")
                        } else {
                            toast(context, "http で始まり %s を含むURLにしてください")
                        }
                    }) { Text("保存") }
                    TextButton(onClick = {
                        searchUrlText = LauncherSettings.DEFAULT_SEARCH_URL
                        update { it.copy(searchUrl = LauncherSettings.DEFAULT_SEARCH_URL) }
                    }) { Text("既定に戻す") }
                }

                Section("メディア操作")
                Text(
                    if (mediaState.permitted) "通知アクセス: 許可済み(曲名の表示と操作が可能)"
                    else "通知アクセス: 未許可(再生/停止などはメディアキーで動作。曲名は表示されません)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onRequestNotificationAccess) { Text("通知アクセスの設定を開く") }

                Section("ホームアプリ")
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure { toast(context, "設定画面を開けません") }
                    }
                }) { Text("デフォルトのホームアプリに設定") }

                Section("バックアップ・復元")
                Text(
                    "設定・ホーム配置・ドック・起動回数をJSONで保存/復元します。ウィジェットは復元後に再追加(再許可)が必要です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        exportLauncher.launch("homelauncher-backup-$stamp.json")
                    }) { Text("書き出し") }
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }) { Text("復元") }
                }

                Section("その他")
                OutlinedButton(onClick = { confirmReset = true }) { Text("設定を初期化") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "HomeLauncher ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    confirmImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImportUri = null },
            title = { Text("バックアップから復元") },
            text = { Text("現在の設定・ホーム配置・ドック・起動回数は上書きされます。続けますか?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImportUri = null
                    scope.launch {
                        val text = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                            }.getOrNull()
                        }
                        if (text == null) {
                            toast(context, "ファイルを読み込めません")
                        } else {
                            vm.importBackup(text)
                                .onSuccess { oldIds ->
                                    oldIds.forEach { widgets.delete(it) }
                                    toast(context, "復元しました")
                                }
                                .onFailure { toast(context, "復元に失敗: ${it.message}") }
                        }
                    }
                }) { Text("復元する") }
            },
            dismissButton = { TextButton(onClick = { confirmImportUri = null }) { Text("キャンセル") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("設定を初期化") },
            text = { Text("設定値を既定に戻します(ホーム配置は変わりません)。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.updateSettings { LauncherSettings() }
                }) { Text("初期化") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
