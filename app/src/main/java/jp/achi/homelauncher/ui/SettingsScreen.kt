package jp.achi.homelauncher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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

private val TABS = listOf("ホーム", "ドック", "ウィジェット・バー", "アプリ一覧", "色・外観", "その他")

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

    var tab by rememberSaveable { mutableStateOf(0) }
    var showPreview by rememberSaveable { mutableStateOf(true) }
    var confirmImportUri by remember { mutableStateOf<Uri?>(null) }
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

    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) { importFont(context, uri) }
                if (name == null) {
                    toast(context, "フォントを読み込めません(ttf / otf を選んでください)")
                } else {
                    vm.updateSettings { it.copy(fontFile = name.first, fontName = name.second) }
                    toast(context, "フォント「${name.second}」を設定しました")
                }
            }
        }
    }
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current

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
            Column(Modifier.padding(padding)) {
                // プレビュー(設定の変更が即反映される)
                if (showPreview) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LauncherPreview(s, drawer = tab == 3, modifier = Modifier.size(width = 117.dp, height = 208.dp))
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(if (tab == 3) "プレビュー(アプリ一覧)" else "プレビュー(ホーム画面)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "設定の変更がすぐ反映されます。壁紙は仮の画像です。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                TextButton(onClick = { showPreview = !showPreview }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (showPreview) "プレビューを隠す" else "プレビューを表示")
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    TABS.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (tab) {
                        0 -> {
                            Section("グリッド")
                            IntSliderRow("列数", s.gridCols, 3, 8) { v -> update { it.copy(gridCols = v) } }
                            IntSliderRow("行数", s.gridRows, 3, 10) { v -> update { it.copy(gridRows = v) } }
                            IntSliderRow("ページ数", s.pageCount, 1, 8) { v -> update { it.copy(pageCount = v) } }
                            Section("アイコン")
                            IntSliderRow("アイコンサイズ", s.iconSizeDp, 32, 96, "dp") { v -> update { it.copy(iconSizeDp = v) } }
                            PercentSliderRow("アイコンの不透明度", s.iconAlpha, 0.1f, 1f) { v -> update { it.copy(iconAlpha = v) } }
                            SwitchRow("アプリ名を表示", s.showLabels) { v -> update { it.copy(showLabels = v) } }
                            IntSliderRow("アプリ名の文字サイズ", s.labelSizeSp, 8, 16, "sp") { v -> update { it.copy(labelSizeSp = v) } }
                            Section("配置")
                            Text("配置先に既にあるアイテムがあるとき", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "追加・移動した位置にアイテムがあると、お知らせを出して下の方法で配置します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(s.overlapPolicy == LauncherSettings.OVERLAP, { update { it.copy(overlapPolicy = LauncherSettings.OVERLAP) } }, { Text("そのまま被せる") })
                                FilterChip(s.overlapPolicy == LauncherSettings.PUSH_DOWN, { update { it.copy(overlapPolicy = LauncherSettings.PUSH_DOWN) } }, { Text("既存を下へずらす") })
                                FilterChip(s.overlapPolicy == LauncherSettings.PUSH_UP, { update { it.copy(overlapPolicy = LauncherSettings.PUSH_UP) } }, { Text("既存を上へずらす") })
                                FilterChip(s.overlapPolicy == LauncherSettings.SHIFT, { update { it.copy(overlapPolicy = LauncherSettings.SHIFT) } }, { Text("押し出し(A B C→C A B)") })
                            }
                            SwitchRow("レイアウトをロック(長押し編集を無効化)", s.lockLayout) { v -> update { it.copy(lockLayout = v) } }
                            SwitchRow("ホーム画面に「編集」ボタンを表示(長押し不要の編集モード)", s.showEditButton) { v -> update { it.copy(showEditButton = v) } }
                            SwitchRow("自由配置モード(マスに縛られず移動・サイズ変更)", s.freeMode) { v -> update { it.copy(freeMode = v) } }
                            Text(
                                "オフに戻して項目を動かすと、再びマスに揃います。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Section("自動調整(AIなし)")
                            val cellW = cfg.screenWidthDp.toFloat() / s.gridCols
                            val gridH = cfg.screenHeightDp - (if (s.showDock) s.dockHeightDp else 0) - 120f
                            val cellH = gridH / s.gridRows
                            val dens = context.resources.displayMetrics.density
                            val spanOf: (jp.achi.homelauncher.data.HomeItem) -> Pair<Int, Int>? = { w ->
                                widgets.findProvider(w.provider)?.let { widgets.suggestedSpan(it, cellW, cellH, dens) }
                            }
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = {
                                    vm.autoAdjust(cellW, cellH)
                                    vm.autoAdjustWidgets(spanOf)
                                    toast(context, "大きさと配置を自動調整しました")
                                }) { Text("すべて") }
                                OutlinedButton(onClick = {
                                    vm.autoAdjustWidgets(spanOf)
                                    toast(context, "ウィジェットの大きさを自動調整しました")
                                }) { Text("ウィジェットのみ") }
                                OutlinedButton(onClick = {
                                    vm.autoAdjustIcons(cellW, cellH)
                                    toast(context, "アイコン・文字・ドックの大きさを自動調整しました")
                                }) { Text("アプリアイコンのみ") }
                            }
                            Text(
                                "アイコン: アイコン・文字・ドックの大きさをセルに合わせます。ウィジェット: 推奨(最小)サイズに合わせ、はみ出し・重なりを整えます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Section("アイコンの形")
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ICON_SHAPE_LABELS.forEach { (key, label) ->
                                    FilterChip(s.iconShape == key, { update { it.copy(iconShape = key) } }, { Text(label) })
                                }
                            }
                            Section("通知バッジ")
                            SwitchRow("通知の数をアイコンに表示", s.showBadges) { v -> update { it.copy(showBadges = v) } }
                            Text(
                                "通知アクセスの許可が必要です。バッジのあるアプリを長押しすると通知の内容を見られます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            OutlinedButton(onClick = onRequestNotificationAccess) { Text("通知アクセスを設定") }
                        }

                        1 -> {
                            Section("ドック")
                            SwitchRow("ドックを表示", s.showDock) { v -> update { it.copy(showDock = v) } }
                            IntSliderRow("縦幅", s.dockHeightDp, 48, 160, "dp") { v -> update { it.copy(dockHeightDp = v) } }
                            IntSliderRow("1ページのアイコン数", s.dockCount, 3, 7) { v -> update { it.copy(dockCount = v) } }
                            IntSliderRow("アイコンサイズ(ドック専用)", s.dockIconSizeDp, 24, 120, "dp") { v -> update { it.copy(dockIconSizeDp = v) } }
                            Text(
                                "ドックのアイコンは、長押ししたまま左右に動かして並び替えできます(間のアイコンは1つずつずれます)。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            PercentSliderRow("アイコンの不透明度(ドック専用)", s.dockIconAlpha, 0.1f, 1f) { v -> update { it.copy(dockIconAlpha = v) } }
                            SwitchRow("最後のページの次は最初に戻る", s.dockLoop) { v -> update { it.copy(dockLoop = v) } }
                        }

                        2 -> {
                            Section("ウィジェット")
                            PercentSliderRow("全体の不透明度", s.widgetGlobalAlpha) { v -> update { it.copy(widgetGlobalAlpha = v) } }
                            Text(
                                "個別設定: ウィジェットを長押し → 上部バーの「詳細」",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Section("検索バー・メディアバー")
                            PercentSliderRow("検索バーの不透明度", s.searchBarAlpha, 0.1f, 1f) { v -> update { it.copy(searchBarAlpha = v) } }
                            PercentSliderRow("メディアバーの不透明度", s.mediaBarAlpha, 0.1f, 1f) { v -> update { it.copy(mediaBarAlpha = v) } }
                            SwitchRow("ボタンの大きさを自動にする", s.mediaButtonSp == 0) { v ->
                                update { it.copy(mediaButtonSp = if (v) 0 else 22) }
                            }
                            if (s.mediaButtonSp > 0) {
                                IntSliderRow("ボタンの大きさ", s.mediaButtonSp, 14, 44, "sp") { v -> update { it.copy(mediaButtonSp = v) } }
                            }
                            SwitchRow("ボタンを透かして曲名を重ねて表示", s.mediaOverlay) { v -> update { it.copy(mediaOverlay = v) } }
                            Section("メディア操作")
                            Text(
                                if (mediaState.permitted) "通知アクセス: 許可済み(曲名の表示と操作が可能)"
                                else "通知アクセス: 未許可(再生/停止などはメディアキーで動作。曲名は表示されません)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = onRequestNotificationAccess) { Text("通知アクセスの設定を開く") }
                        }

                        3 -> {
                            Section("表示")
                            IntSliderRow("列数", s.drawerCols, 3, 7) { v -> update { it.copy(drawerCols = v) } }
                            IntSliderRow("アイコンサイズ", s.drawerIconSizeDp, 32, 96, "dp") { v -> update { it.copy(drawerIconSizeDp = v) } }
                            Section("並び順")
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(s.drawerSort == SortMode.NAME, { update { it.copy(drawerSort = SortMode.NAME) } }, { Text("名前順") })
                                FilterChip(s.drawerSort == SortMode.LAST_LAUNCH, { update { it.copy(drawerSort = SortMode.LAST_LAUNCH) } }, { Text("最終起動順") })
                                FilterChip(s.drawerSort == SortMode.INSTALL, { update { it.copy(drawerSort = SortMode.INSTALL) } }, { Text("ダウンロード順") })
                                FilterChip(s.drawerSort == SortMode.FREQUENT, { update { it.copy(drawerSort = SortMode.FREQUENT) } }, { Text("よく使う順") })
                                FilterChip(s.drawerSort == SortMode.UPDATED, { update { it.copy(drawerSort = SortMode.UPDATED) } }, { Text("更新順") })
                            }
                            Section("背景")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(s.drawerBgMode == "theme", { update { it.copy(drawerBgMode = "theme") } }, { Text("テーマの色") })
                                FilterChip(s.drawerBgMode == "custom", { update { it.copy(drawerBgMode = "custom") } }, { Text("好きな色") })
                            }
                            if (s.drawerBgMode == "custom") {
                                ColorPickerRow("背景色", s.drawerBgColor) { c -> update { it.copy(drawerBgColor = c) } }
                            }
                            PercentSliderRow("背景の不透明度", s.drawerBgAlpha, 0.3f, 1f) { v -> update { it.copy(drawerBgAlpha = v) } }
                        }

                        4 -> {
                            Section("設定画面などの色(アプリ全体)")
                            SwitchRow("システムの色(Dynamic Color)を使う", s.useDynamicColor) { v -> update { it.copy(useDynamicColor = v) } }
                            Text("テーマ", style = MaterialTheme.typography.bodyLarge)
                            ThemeChips(s.darkTheme) { v -> update { it.copy(darkTheme = v) } }
                            Section("ホーム画面の色")
                            Text(
                                "検索バー・メディアバー・ドック・アプリ一覧などに使う色です。設定画面とは別にできます(例: 設定はダーク、ホームは明るいピンク)。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                FilterChip(s.homeColorMode == "system", { update { it.copy(homeColorMode = "system") } }, { Text("設定画面と同じ") })
                                FilterChip(s.homeColorMode == "custom", { update { it.copy(homeColorMode = "custom") } }, { Text("好きな色") })
                            }
                            if (s.homeColorMode == "custom") {
                                ColorPickerRow("ホームの基準色", s.homeColor) { c -> update { it.copy(homeColor = c) } }
                                Text("ホームの明るさ", style = MaterialTheme.typography.bodyLarge)
                                ThemeChips(s.homeDarkTheme) { v -> update { it.copy(homeDarkTheme = v) } }
                            }
                            PercentSliderRow("ホーム背景の暗さ", s.scrimAlpha, 0f, 0.8f) { v -> update { it.copy(scrimAlpha = v) } }
                            Section("フォント")
                            Text(
                                if (s.fontFile.isEmpty()) "現在: 標準" else "現在: ${s.fontName}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                OutlinedButton(onClick = { fontLauncher.launch(arrayOf("*/*")) }) { Text("フォントを追加(ttf / otf)") }
                                if (s.fontFile.isNotEmpty()) {
                                    TextButton(onClick = { update { it.copy(fontFile = "", fontName = "") } }) { Text("標準に戻す") }
                                }
                            }
                        }

                        else -> {
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
                            Text(
                                "ブラウザの「ホーム画面に追加」を使うには、このランチャーをデフォルトのホームにしてください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp),
                            )

                            Section("バックアップ・復元")
                            Text(
                                "設定・ホーム配置・ドック・フォルダ・起動履歴をJSONで保存/復元します。ウィジェットは復元後に再追加(再許可)が必要です。",
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
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    confirmImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImportUri = null },
            title = { Text("バックアップから復元") },
            text = { Text("現在の設定・ホーム配置・ドック・フォルダ・起動履歴は上書きされます。続けますか?") },
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
private fun ThemeChips(current: String, onChange: (String) -> Unit) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(current == "system", { onChange("system") }, { Text("システム") })
        FilterChip(current == "light", { onChange("light") }, { Text("ライト") })
        FilterChip(current == "dark", { onChange("dark") }, { Text("ダーク") })
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(10.dp))
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

/** 選んだフォントファイルをアプリ内に保存する。戻り値は (保存ファイル名, 表示名)。読み込めなければ null。 */
private fun importFont(context: android.content.Context, uri: Uri): Pair<String, String>? = runCatching {
    var display = "font"
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) display = it.getString(0) ?: display
    }
    val dir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
    val ext = if (display.lowercase().endsWith(".otf")) "otf" else "ttf"
    val file = java.io.File(dir, "font_${System.currentTimeMillis()}.$ext")
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
    // 壊れたファイルは弾く(Typeface を作れなければ不正)
    if (android.graphics.Typeface.Builder(file).build() == null) {
        file.delete()
        return null
    }
    dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
    file.name to display.substringBeforeLast('.')
}.getOrNull()
