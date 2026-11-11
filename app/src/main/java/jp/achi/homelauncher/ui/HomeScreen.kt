package jp.achi.homelauncher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.achi.homelauncher.LauncherViewModel
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppSorter
import jp.achi.homelauncher.data.GridLogic
import jp.achi.homelauncher.data.HomeItem
import jp.achi.homelauncher.data.LauncherSettings
import jp.achi.homelauncher.data.SortMode
import jp.achi.homelauncher.media.MediaHelper
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    widgets: WidgetController,
    media: MediaHelper,
    resetSignal: Int,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val dock by vm.dock.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val pending by vm.pendingRebind.collectAsStateWithLifecycle()
    val mediaState by media.state.collectAsStateWithLifecycle()
    val appMap = remember(apps) { apps.associateBy { it.key } }

    var query by remember { mutableStateOf("") }
    var emptyMenu by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<HomeItem?>(null) }
    var editItem by remember { mutableStateOf<HomeItem?>(null) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var dockPickerIndex by remember { mutableStateOf<Int?>(null) }
    var dockMenuKey by remember { mutableStateOf<String?>(null) }
    var cellDp by remember { mutableStateOf(80f to 80f) }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { settings.pageCount })

    LaunchedEffect(resetSignal) {
        // ホームボタンで戻ってきたら検索・メニューを閉じて1ページ目へ
        query = ""
        emptyMenu = false
        itemMenu = null
        pagerState.scrollToPage(0)
    }

    fun buzz() {
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** ウィジェット追加。結果はアクティビティ結果経由で非同期に返る。 */
    fun addWidget(info: AppWidgetProviderInfo) {
        val activity = context.findActivity()
        if (activity == null) {
            toast(context, "ウィジェットを追加できません")
            return
        }
        widgets.add(activity, info) { id, inf ->
            val s = vm.settings.value
            val d = density.density
            val sx = ceil((inf.minWidth / d) / cellDp.first).toInt().coerceIn(1, s.gridCols)
            val sy = ceil((inf.minHeight / d) / cellDp.second).toInt().coerceIn(1, s.gridRows)
            val cell = GridLogic.findFreeCell(vm.items.value, s.pageCount, s.gridCols, s.gridRows, sx, sy)
            if (cell == null) {
                widgets.delete(id)
                toast(context, "ホーム画面に空きがありません")
            } else {
                vm.addWidget(
                    HomeItem(
                        type = HomeItem.WIDGET,
                        page = cell.first,
                        x = cell.second,
                        y = cell.third,
                        spanX = sx,
                        spanY = sy,
                        widgetId = id,
                        provider = inf.provider.flattenToString(),
                    ),
                )
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = settings.scrimAlpha))
            .pointerInput(Unit) {
                // 下から上へのスワイプでアプリ一覧
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total < -120f) onOpenDrawer() },
                )
            },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            if (settings.showSearchBar) {
                HomeSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSubmit = { openWebSearch(context, settings.searchUrl, query) },
                )
            }

            if (query.isNotBlank()) {
                SearchResults(
                    query = query,
                    apps = apps,
                    onLaunch = {
                        vm.launch(context, it)
                        query = ""
                    },
                    onWeb = { openWebSearch(context, settings.searchUrl, query) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                if (settings.showMediaPanel) {
                    MediaPanel(mediaState, media, onRequestNotificationAccess)
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val cols = settings.gridCols
                    val rows = settings.gridRows
                    val cellW = maxWidth / cols
                    val cellH = maxHeight / rows
                    LaunchedEffect(cellW, cellH) { cellDp = cellW.value to cellH.value }

                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onLongPress = {
                                        if (!vm.settings.value.lockLayout) {
                                            buzz()
                                            emptyMenu = true
                                        }
                                    })
                                },
                        ) {
                            for (raw in items) {
                                if (raw.page != page) continue
                                val item = GridLogic.clamp(raw, cols, rows)
                                key(item.id) {
                                    Box(
                                        Modifier
                                            .offset(cellW * item.x, cellH * item.y)
                                            .size(cellW * item.spanX, cellH * item.spanY),
                                    ) {
                                        if (item.type == HomeItem.APP) {
                                            val app = appMap[item.appKey]
                                            if (app != null) {
                                                AppCell(
                                                    app = app,
                                                    iconSizeDp = min(settings.iconSizeDp, (cellW.value - 8f).toInt()).coerceAtLeast(24),
                                                    showLabel = settings.showLabels,
                                                    labelSizeSp = settings.labelSizeSp,
                                                    onWallpaper = true,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onClick = { vm.launch(context, app) },
                                                    onLongClick = {
                                                        if (!settings.lockLayout) {
                                                            buzz()
                                                            itemMenu = item
                                                        }
                                                    },
                                                )
                                            }
                                        } else {
                                            WidgetBox(
                                                item = item,
                                                widgets = widgets,
                                                alphaValue = if (item.alpha >= 0f) item.alpha else settings.widgetGlobalAlpha,
                                                widthDp = (cellW * item.spanX).value.toInt(),
                                                heightDp = (cellH * item.spanY).value.toInt(),
                                                showHandle = !settings.lockLayout,
                                                onHandle = {
                                                    buzz()
                                                    itemMenu = item
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (settings.pageCount > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(settings.pageCount) { i ->
                            Box(
                                Modifier
                                    .padding(3.dp)
                                    .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (i == pagerState.currentPage) 0.95f else 0.45f)),
                            )
                        }
                    }
                }
            }

            if (settings.showDock && query.isBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until settings.dockCount) {
                        val dockKey = dock.getOrNull(i)
                        val app = dockKey?.let { appMap[it] }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (app != null) {
                                AppCell(
                                    app = app,
                                    iconSizeDp = settings.iconSizeDp,
                                    showLabel = false,
                                    labelSizeSp = settings.labelSizeSp,
                                    onWallpaper = false,
                                    onClick = { vm.launch(context, app) },
                                    onLongClick = {
                                        if (!settings.lockLayout) {
                                            buzz()
                                            dockMenuKey = dockKey
                                        }
                                    },
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(settings.iconSizeDp.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !settings.lockLayout) { dockPickerIndex = i },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("+", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- メニュー・ダイアログ ----
    if (emptyMenu) {
        MenuDialog(
            title = "ホーム画面",
            entries = listOf(
                "ウィジェットを追加" to {
                    emptyMenu = false
                    showWidgetPicker = true
                },
                "アプリをホームに追加" to {
                    emptyMenu = false
                    showAppPicker = true
                },
                "壁紙を変更" to {
                    emptyMenu = false
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "壁紙を変更")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { toast(context, "壁紙設定を開けません") }
                },
                "ランチャー設定" to {
                    emptyMenu = false
                    onOpenSettings()
                },
            ),
            onDismiss = { emptyMenu = false },
        )
    }

    itemMenu?.let { item ->
        val title = if (item.type == HomeItem.WIDGET) "ウィジェット" else appMap[item.appKey]?.label ?: "アプリ"
        MenuDialog(
            title = title,
            entries = listOf(
                (if (item.type == HomeItem.WIDGET) "サイズ・透明度・位置を編集" else "位置を編集") to {
                    itemMenu = null
                    editItem = item
                },
                "ホームから削除" to {
                    itemMenu = null
                    val removed = vm.removeItem(item.id)
                    if (removed != null && removed.type == HomeItem.WIDGET && removed.widgetId >= 0) {
                        widgets.delete(removed.widgetId)
                    }
                },
            ),
            onDismiss = { itemMenu = null },
        )
    }

    editItem?.let { item ->
        ItemEditDialog(
            item = item,
            allItems = items,
            settings = settings,
            onApply = { updated ->
                vm.updateItem(item.id) { updated }
                editItem = null
            },
            onDismiss = { editItem = null },
        )
    }

    if (showWidgetPicker) {
        WidgetPickerDialog(
            widgets = widgets,
            onPick = {
                showWidgetPicker = false
                addWidget(it)
            },
            onDismiss = { showWidgetPicker = false },
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            apps = AppSorter.sort(apps, SortMode.NAME, emptyMap()),
            onPick = {
                showAppPicker = false
                if (!vm.addAppAuto(it)) toast(context, "ホーム画面に空きがありません")
            },
            onDismiss = { showAppPicker = false },
        )
    }

    dockPickerIndex?.let { index ->
        AppPickerDialog(
            apps = AppSorter.sort(apps, SortMode.NAME, emptyMap()),
            onPick = {
                dockPickerIndex = null
                vm.setDockApp(index, it.key)
            },
            onDismiss = { dockPickerIndex = null },
        )
    }

    dockMenuKey?.let { k ->
        MenuDialog(
            title = appMap[k]?.label ?: "ドック",
            entries = listOf(
                "ドックから削除" to {
                    dockMenuKey = null
                    vm.removeDockApp(k)
                },
            ),
            onDismiss = { dockMenuKey = null },
        )
    }

    // バックアップ復元後のウィジェット再追加(ウィジェットIDは端末ごとに発行されるため再バインドが必要)
    if (pending.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("ウィジェットの再追加") },
            text = { Text("バックアップから復元したウィジェットが${pending.size}件あります。1件ずつ再追加します。") },
            confirmButton = {
                TextButton(onClick = {
                    val p = pending.first()
                    val info = widgets.findProvider(p.provider)
                    val activity = context.findActivity()
                    if (info == null) {
                        vm.dropPending(p.id)
                        toast(context, "ウィジェットが見つかりません(スキップ)")
                    } else if (activity != null) {
                        widgets.add(activity, info, skipConfigure = true) { id, _ -> vm.completeRebind(p.id, id) }
                    }
                }) { Text("再追加") }
            },
            dismissButton = {
                TextButton(onClick = { pending.forEach { vm.dropPending(it.id) } }) { Text("破棄") }
            },
        )
    }
}

@Composable
private fun WidgetBox(
    item: HomeItem,
    widgets: WidgetController,
    alphaValue: Float,
    widthDp: Int,
    heightDp: Int,
    showHandle: Boolean,
    onHandle: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(2.dp)) {
        AndroidView(
            factory = { ctx -> widgets.createView(ctx, item.widgetId) ?: View(ctx) },
            update = { v ->
                if (v is AppWidgetHostView) {
                    runCatching { v.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp) }
                }
            },
            modifier = Modifier.fillMaxSize().alpha(alphaValue),
        )
        if (showHandle) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onHandle),
                contentAlignment = Alignment.Center,
            ) {
                Text("⋮", color = Color.White)
            }
        }
    }
}

@Composable
private fun HomeSearchBar(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("アプリ・Webを検索") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { TextButton(onClick = { onQueryChange("") }) { Text("✕") } }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SearchResults(
    query: String,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val matches = remember(apps, query) {
        AppSorter.sort(AppSorter.filter(apps, query), SortMode.NAME, emptyMap()).take(40)
    }
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Text(
                "「${query.trim()}」をWebで検索",
                color = Color.White,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onWeb).padding(vertical = 12.dp),
            )
        }
        items(matches, key = { it.key }) { app ->
            Row(
                Modifier.fillMaxWidth().clickable { onLaunch(app) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app, 40)
                Text(
                    app.label,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaPanel(state: MediaHelper.State, media: MediaHelper, onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                if (!state.permitted) {
                    Text(
                        "曲名を表示するには通知アクセスを許可",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable(onClick = onRequestPermission),
                    )
                } else {
                    Text(
                        state.title ?: if (state.hasSession) "再生中のメディア" else "メディアなし",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.artist?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            TextButton(onClick = { media.previous() }) { Text("⏮") }
            TextButton(onClick = { media.playPause() }) { Text(if (state.playing) "⏸" else "▶") }
            TextButton(onClick = { media.next() }) { Text("⏭") }
        }
    }
}

@Composable
private fun WidgetPickerDialog(
    widgets: WidgetController,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers = remember {
        widgets.providers().map { it to widgets.labelOf(it) }.sortedBy { it.second.lowercase() }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(providers, query) {
        if (query.isBlank()) providers else providers.filter { it.second.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text("ウィジェットを選択") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("検索") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.first.provider.flattenToString() }) { (info, label) ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(info) }.padding(vertical = 8.dp)) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                info.provider.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 位置・(ウィジェットは)サイズと透明度を数値で編集する。 */
@Composable
private fun ItemEditDialog(
    item: HomeItem,
    allItems: List<HomeItem>,
    settings: LauncherSettings,
    onApply: (HomeItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isWidget = item.type == HomeItem.WIDGET
    var page by remember { mutableIntStateOf(item.page + 1) }
    var x by remember { mutableIntStateOf(item.x + 1) }
    var y by remember { mutableIntStateOf(item.y + 1) }
    var sx by remember { mutableIntStateOf(item.spanX) }
    var sy by remember { mutableIntStateOf(item.spanY) }
    var useGlobal by remember { mutableStateOf(item.alpha < 0f) }
    var alphaV by remember { mutableFloatStateOf(if (item.alpha < 0f) settings.widgetGlobalAlpha else item.alpha) }

    val cols = settings.gridCols
    val rows = settings.gridRows
    val spanX = if (isWidget) sx.coerceIn(1, cols) else 1
    val spanY = if (isWidget) sy.coerceIn(1, rows) else 1
    val xMax = cols - spanX + 1
    val yMax = rows - spanY + 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isWidget) "ウィジェットの編集" else "アプリ位置の編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                IntSliderRow("ページ", page.coerceIn(1, settings.pageCount), 1, settings.pageCount) { page = it }
                if (isWidget) {
                    IntSliderRow("幅(マス)", spanX, 1, cols) { sx = it }
                    IntSliderRow("高さ(マス)", spanY, 1, rows) { sy = it }
                }
                IntSliderRow("横位置", x.coerceIn(1, xMax), 1, xMax) { x = it }
                IntSliderRow("縦位置", y.coerceIn(1, yMax), 1, yMax) { y = it }
                if (isWidget) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("全体の透明度設定を使う", Modifier.weight(1f))
                        Switch(checked = useGlobal, onCheckedChange = { useGlobal = it })
                    }
                    if (!useGlobal) {
                        PercentSliderRow("このウィジェットの不透明度", alphaV) { alphaV = it }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val candidate = item.copy(
                    page = page.coerceIn(1, settings.pageCount) - 1,
                    x = x.coerceIn(1, xMax) - 1,
                    y = y.coerceIn(1, yMax) - 1,
                    spanX = spanX,
                    spanY = spanY,
                    alpha = if (!isWidget || useGlobal) -1f else alphaV,
                )
                val others = allItems.filter { it.id != item.id }
                if (GridLogic.isFree(others, candidate.page, candidate.x, candidate.y, candidate.spanX, candidate.spanY)) {
                    onApply(candidate)
                } else {
                    toast(context, "その位置は他のアイコン・ウィジェットと重なります")
                }
            }) { Text("適用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
