@file:OptIn(ExperimentalFoundationApi::class)

package jp.achi.homelauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.achi.homelauncher.CellAt
import jp.achi.homelauncher.LauncherViewModel
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppSorter
import jp.achi.homelauncher.data.DockEntry
import jp.achi.homelauncher.data.Folder
import jp.achi.homelauncher.data.GridLogic
import jp.achi.homelauncher.data.HomeItem
import jp.achi.homelauncher.data.LauncherSettings
import jp.achi.homelauncher.data.PlaceResult
import jp.achi.homelauncher.data.SortMode
import jp.achi.homelauncher.media.MediaHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** グリッド上の項目を描画するのに必要な共有状態。 */
private class HomeEnv(
    val settings: LauncherSettings,
    val appMap: Map<String, AppInfo>,
    val folders: List<Folder>,
    val items: List<HomeItem>,
    val widgets: WidgetController,
    val mediaState: MediaHelper.State,
    val media: MediaHelper,
    val onSelect: (String) -> Unit,
    val onLaunch: (AppInfo) -> Unit,
    val onLaunchShortcut: (HomeItem) -> Unit,
    val onOpenFolder: (String) -> Unit,
    val onOpenSearch: () -> Unit,
    val onRequestNotificationAccess: () -> Unit,
)

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
    val scope = rememberCoroutineScope()

    val settings by vm.settings.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val dock by vm.dock.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val pending by vm.pendingRebind.collectAsStateWithLifecycle()
    val mediaState by media.state.collectAsStateWithLifecycle()
    val appMap = remember(apps) { apps.associateBy { it.key } }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var carrying by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var emptyMenu by remember { mutableStateOf(false) }
    /** 空き地を長押しした位置(ページ, 列, 行)。メニューで追加するものはここに置く。 */
    var pendingAt by remember { mutableStateOf<CellAt?>(null) }
    var editItem by remember { mutableStateOf<HomeItem?>(null) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    /** ウィジェット追加先。空文字ならホーム直置き、それ以外はスタックの item id。 */
    var widgetTarget by remember { mutableStateOf("") }
    var widgetAt by remember { mutableStateOf<CellAt?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var folderPickItem by remember { mutableStateOf<HomeItem?>(null) }
    /** フォルダ作成先。-1 ならホーム、0以上ならドックの位置。null は非表示。 */
    var newFolderAt by remember { mutableStateOf<Int?>(null) }
    var stackManageId by remember { mutableStateOf<String?>(null) }
    var dockEmptyIndex by remember { mutableStateOf<Int?>(null) }
    var dockAppPickIndex by remember { mutableStateOf<Int?>(null) }
    var dockMenuIndex by remember { mutableStateOf<Int?>(null) }
    var cellDp by remember { mutableStateOf(80f to 80f) }

    val pagerState = rememberPagerState(pageCount = { settings.pageCount })
    val selectedItem = items.firstOrNull { it.id == selectedId }

    LaunchedEffect(resetSignal) {
        // ホームボタンで戻ってきたら、検索・編集モード・メニューを閉じて1ページ目へ
        searchOpen = false
        selectedId = null
        carrying = false
        emptyMenu = false
        openFolderId = null
        pagerState.scrollToPage(0)
    }

    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = selectedId != null && !searchOpen) { selectedId = null }

    fun buzz() {
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun select(id: String) {
        if (vm.settings.value.lockLayout) return
        buzz()
        selectedId = id
    }

    fun launchApp(app: AppInfo) {
        vm.launch(context, app)
    }

    /** 配置結果をお知らせする(空きなし/重ねた/ずらした)。 */
    fun report(r: PlaceResult) {
        when (r) {
            PlaceResult.FAILED -> toast(context, "ホーム画面に空きがありません")
            PlaceResult.OVERLAPPED -> toast(context, vm.overlapMessage())
            PlaceResult.PLACED -> Unit
        }
    }

    /** ウィジェット追加。結果はアクティビティ結果経由で非同期に返る。 */
    fun addWidget(info: AppWidgetProviderInfo, parent: String, at: CellAt?) {
        val activity = context.findActivity()
        if (activity == null) {
            toast(context, "ウィジェットを追加できません")
            return
        }
        widgets.add(activity, info) { id, inf ->
            val provider = inf.provider.flattenToString()
            if (parent.isNotEmpty()) {
                vm.addWidget(
                    HomeItem(
                        type = HomeItem.WIDGET, page = 0, x = 0, y = 0,
                        widgetId = id, provider = provider, parent = parent,
                    ),
                )
                return@add
            }
            val s = vm.settings.value
            val d = density.density
            val sx = ceil((inf.minWidth / d) / cellDp.first).toInt().coerceIn(1, s.gridCols)
            val sy = ceil((inf.minHeight / d) / cellDp.second).toInt().coerceIn(1, s.gridRows)
            if (at != null) {
                // 長押しした場所に置く。既存の項目があれば設定に従って被せる/ずらす
                val item = HomeItem(
                    type = HomeItem.WIDGET, page = at.first, x = at.second, y = at.third,
                    spanX = sx, spanY = sy, widgetId = id, provider = provider,
                )
                if (vm.placeAndResolve(item)) toast(context, vm.overlapMessage())
            } else {
                val cell = GridLogic.findFreeCell(vm.items.value, s.pageCount, s.gridCols, s.gridRows, sx, sy)
                if (cell == null) {
                    widgets.delete(id)
                    toast(context, "ホーム画面に空きがありません")
                } else {
                    vm.addWidget(
                        HomeItem(
                            type = HomeItem.WIDGET, page = cell.first, x = cell.second, y = cell.third,
                            spanX = sx, spanY = sy, widgetId = id, provider = provider,
                        ),
                    )
                }
            }
        }
    }

    fun deleteItem(item: HomeItem) {
        selectedId = null
        if (item.type == HomeItem.FOLDER) {
            vm.deleteFolder(item.ref)
            return
        }
        vm.removeItem(item.id)
            .filter { it.type == HomeItem.WIDGET && it.widgetId >= 0 }
            .forEach { widgets.delete(it.widgetId) }
    }

    /** ドラッグ移動・リサイズの確定。アイコンを別のアイコン/フォルダに重ねた場合はフォルダにまとめる。 */
    fun commitMove(sel: HomeItem, page: Int, nx: Int, ny: Int, nsx: Int, nsy: Int) {
        if (sel.type == HomeItem.APP && nsx == 1 && nsy == 1) {
            val target = vm.items.value.firstOrNull { t ->
                t.id != sel.id && t.parent.isEmpty() && t.page == page &&
                    (t.type == HomeItem.APP || t.type == HomeItem.FOLDER) &&
                    GridLogic.overlaps(t, nx, ny, 1, 1)
            }
            if (target != null && vm.mergeIntoFolder(sel.id, target.id)) {
                selectedId = null
                toast(context, "フォルダにまとめました")
                return
            }
        }
        val stored = vm.items.value.firstOrNull { it.id == sel.id } ?: return
        val collided = vm.placeAndResolve(stored.copy(page = page, x = nx, y = ny, spanX = nsx, spanY = nsy))
        if (collided) toast(context, vm.overlapMessage())
    }

    val env = HomeEnv(
        settings = settings,
        appMap = appMap,
        folders = folders,
        items = items,
        widgets = widgets,
        mediaState = mediaState,
        media = media,
        onSelect = { select(it) },
        onLaunch = { launchApp(it) },
        onLaunchShortcut = {
            if (!ShortcutHelper.launch(context, it.pkg, it.cls)) {
                toast(context, "ショートカットを開けません(デフォルトのホームに設定されているか確認してください)")
            }
        },
        onOpenFolder = { openFolderId = it },
        onOpenSearch = { searchOpen = true },
        onRequestNotificationAccess = onRequestNotificationAccess,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = settings.scrimAlpha))
            .pointerInput(Unit) {
                // 下から上へのスワイプでアプリ一覧。ウィジェットに触れて始めたスクロールは
                // ウィジェット内の動きを優先し、アプリ一覧は開かない。
                var total = 0f
                var ignore = false
                detectVerticalDragGestures(
                    onDragStart = {
                        total = 0f
                        ignore = SwipeGuard.widgetTouched
                    },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (!ignore && total < -120f) onOpenDrawer() },
                )
            },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val cols = settings.gridCols
                val rows = settings.gridRows
                val cellW = maxWidth / cols
                val cellH = maxHeight / rows
                val cellWpx = with(density) { cellW.toPx() }
                val cellHpx = with(density) { cellH.toPx() }
                val viewportWpx = with(density) { maxWidth.toPx() }
                LaunchedEffect(cellW, cellH) { cellDp = cellW.value to cellH.value }

                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val shown = items
                        .filter { it.page == page && it.parent.isEmpty() }
                        .map { GridLogic.clamp(it, cols, rows) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(page, cellWpx, cellHpx, cols, rows) {
                                detectTapGestures(
                                    onTap = { selectedId = null },
                                    onLongPress = { off ->
                                        val s = vm.settings.value
                                        if (s.lockLayout) return@detectTapGestures
                                        val cx = (off.x / cellWpx).toInt().coerceIn(0, cols - 1)
                                        val cy = (off.y / cellHpx).toInt().coerceIn(0, rows - 1)
                                        // アイテムの上の長押しは、そのアイテム側(編集モード)で処理する
                                        val onItem = vm.items.value.any {
                                            it.parent.isEmpty() && it.page == page &&
                                                GridLogic.overlaps(GridLogic.clamp(it, s.gridCols, s.gridRows), cx, cy, 1, 1)
                                        }
                                        if (onItem) return@detectTapGestures
                                        buzz()
                                        selectedId = null
                                        pendingAt = Triple(page, cx, cy)
                                        emptyMenu = true
                                    },
                                )
                            },
                    ) {
                        for (item in shown) {
                            key(item.id) { GridItem(item, env, cellW, cellH) }
                        }
                    }
                }

                // 編集枠はページの外(ビューポート基準)に置き、ページをまたいでドラッグできるようにする
                val sel = selectedItem?.takeIf { it.parent.isEmpty() }
                if (sel != null && (sel.page == pagerState.currentPage || carrying)) {
                    EditFrame(
                        item = GridLogic.clamp(sel, cols, rows),
                        cellWpx = cellWpx,
                        cellHpx = cellHpx,
                        cols = cols,
                        rows = rows,
                        viewportWpx = viewportWpx,
                        carrying = carrying,
                        onCarry = { carrying = it },
                        onEdge = { dir ->
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + dir).coerceIn(0, vm.settings.value.pageCount - 1),
                                )
                            }
                        },
                    ) { nx, ny, nsx, nsy -> commitMove(sel, pagerState.currentPage, nx, ny, nsx, nsy) }
                }
            }

            if (settings.pageCount > 1) {
                PageDots(count = settings.pageCount, current = pagerState.currentPage)
            }

            if (settings.showDock) {
                val perPage = settings.dockCount
                val dockPages = (dock.size + perPage) / perPage // ceil((size + 1) / perPage)
                val loop = settings.dockLoop && dockPages > 1
                val virtualCount = if (loop) dockPages * 1000 else dockPages
                key(dockPages, loop) {
                    val dockPager = rememberPagerState(
                        initialPage = if (loop) dockPages * 500 else 0,
                        pageCount = { virtualCount },
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                            .padding(vertical = 8.dp),
                    ) {
                        HorizontalPager(
                            state = dockPager,
                            modifier = Modifier.fillMaxWidth().height(settings.dockHeightDp.dp),
                        ) { vp ->
                            val p = vp % dockPages
                            Row(
                                Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                for (i in 0 until perPage) {
                                    val idx = p * perPage + i
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        DockSlot(
                                            entry = dock.getOrNull(idx),
                                            env = env,
                                            onEmpty = { if (!settings.lockLayout) dockEmptyIndex = idx },
                                            onLong = {
                                                if (!settings.lockLayout) {
                                                    buzz()
                                                    dockMenuIndex = idx
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (dockPages > 1) {
                            PageDots(count = dockPages, current = dockPager.currentPage % dockPages)
                        }
                    }
                }
            }
        }

        // 編集モードの操作バー
        if (selectedItem != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    TextButton(onClick = { editItem = selectedItem }) { Text("詳細") }
                    if (selectedItem.type == HomeItem.APP) {
                        TextButton(onClick = { folderPickItem = selectedItem }) { Text("フォルダへ") }
                    }
                    if (selectedItem.type == HomeItem.STACK) {
                        TextButton(onClick = { stackManageId = selectedItem.id }) { Text("スタック管理") }
                    }
                    TextButton(onClick = { deleteItem(selectedItem) }) { Text("削除") }
                    TextButton(onClick = { selectedId = null }) { Text("完了") }
                }
            }
        }

        if (searchOpen) {
            SearchOverlay(
                apps = apps,
                searchUrl = settings.searchUrl,
                onLaunch = {
                    searchOpen = false
                    launchApp(it)
                },
                onClose = { searchOpen = false },
            )
        }
    }

    // ---- メニュー・ダイアログ ----
    if (emptyMenu) {
        val at = pendingAt
        val entries = buildList<Pair<String, () -> Unit>> {
            add("ここにウィジェットを追加" to {
                emptyMenu = false
                widgetTarget = ""
                widgetAt = at
                showWidgetPicker = true
            })
            add("ここにウィジェットスタックを作成" to {
                emptyMenu = false
                report(vm.createStack(at))
            })
            add("ここにアプリを追加" to {
                emptyMenu = false
                showAppPicker = true
            })
            add("ここにフォルダを作成" to {
                emptyMenu = false
                newFolderAt = -1
            })
            if (items.none { it.type == HomeItem.SEARCH }) {
                add("ここに検索バーを追加" to {
                    emptyMenu = false
                    report(vm.addSpecial(HomeItem.SEARCH, at))
                })
            }
            if (items.none { it.type == HomeItem.MEDIA }) {
                add("ここにメディアバーを追加" to {
                    emptyMenu = false
                    report(vm.addSpecial(HomeItem.MEDIA, at))
                })
            }
            add("壁紙を変更" to {
                emptyMenu = false
                runCatching {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "壁紙を変更")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { toast(context, "壁紙設定を開けません") }
            })
            add("ランチャー設定" to {
                emptyMenu = false
                onOpenSettings()
            })
        }
        MenuDialog(title = "ホーム画面", entries = entries, onDismiss = { emptyMenu = false })
    }

    editItem?.let { item ->
        ItemEditDialog(
            item = item,
            settings = settings,
            onApply = { updated ->
                editItem = null
                if (vm.placeAndResolve(updated)) toast(context, vm.overlapMessage())
            },
            onDismiss = { editItem = null },
        )
    }

    if (showWidgetPicker) {
        WidgetPickerDialog(
            widgets = widgets,
            onPick = {
                showWidgetPicker = false
                addWidget(it, widgetTarget, widgetAt)
            },
            onDismiss = { showWidgetPicker = false },
        )
    }

    if (showAppPicker) {
        val at = pendingAt
        AppPickerDialog(
            apps = AppSorter.sort(apps, SortMode.NAME, emptyMap()),
            onPick = {
                showAppPicker = false
                report(vm.addAppAuto(it, at))
            },
            onDismiss = { showAppPicker = false },
        )
    }

    openFolderId?.let { fid ->
        val folder = folders.firstOrNull { it.id == fid }
        if (folder == null) {
            openFolderId = null
        } else {
            FolderDialog(
                folder = folder,
                appMap = appMap,
                allApps = apps,
                iconSizeDp = settings.drawerIconSizeDp,
                labelSizeSp = settings.labelSizeSp,
                onLaunch = {
                    openFolderId = null
                    launchApp(it)
                },
                onPutOnHome = { app ->
                    if (vm.addAppByKey(app.key) != PlaceResult.FAILED) vm.removeFromFolder(folder.id, app.key)
                    else toast(context, "ホーム画面に空きがありません")
                },
                onRemoveFromFolder = { vm.removeFromFolder(folder.id, it.key) },
                onAddApp = { vm.addToFolder(folder.id, it.key) },
                onRename = { vm.renameFolder(folder.id, it) },
                onDelete = {
                    openFolderId = null
                    vm.deleteFolder(folder.id)
                },
                onDismiss = { openFolderId = null },
            )
        }
    }

    folderPickItem?.let { item ->
        FolderPickDialog(
            folders = folders,
            onPick = { f ->
                folderPickItem = null
                selectedId = null
                vm.moveHomeAppToFolder(item.id, f.id)
            },
            onDismiss = { folderPickItem = null },
        )
    }

    newFolderAt?.let { at ->
        val cell = pendingAt
        TextInputDialog(
            title = "フォルダ名",
            initial = "",
            onConfirm = { name ->
                newFolderAt = null
                val created = vm.createFolder(name, if (at >= 0) at else null, cell)
                if (created == null) report(PlaceResult.FAILED)
                else if (at < 0 && vm.lastPlace == PlaceResult.OVERLAPPED) report(PlaceResult.OVERLAPPED)
            },
            onDismiss = { newFolderAt = null },
        )
    }

    stackManageId?.let { sid ->
        val children = items.filter { it.parent == sid }
        StackManageDialog(
            children = children,
            widgets = widgets,
            onAdd = {
                stackManageId = null
                widgetTarget = sid
                widgetAt = null
                showWidgetPicker = true
            },
            onRemove = { child ->
                vm.removeItem(child.id).forEach { if (it.widgetId >= 0) widgets.delete(it.widgetId) }
            },
            onDismiss = { stackManageId = null },
        )
    }

    dockEmptyIndex?.let { idx ->
        MenuDialog(
            title = "ドック",
            entries = listOf(
                "アプリを追加" to {
                    dockEmptyIndex = null
                    dockAppPickIndex = idx
                },
                "フォルダを作成" to {
                    dockEmptyIndex = null
                    newFolderAt = idx
                },
            ),
            onDismiss = { dockEmptyIndex = null },
        )
    }

    dockAppPickIndex?.let { idx ->
        AppPickerDialog(
            apps = AppSorter.sort(apps, SortMode.NAME, emptyMap()),
            onPick = {
                dockAppPickIndex = null
                vm.setDockEntry(idx, it.key)
            },
            onDismiss = { dockAppPickIndex = null },
        )
    }

    dockMenuIndex?.let { idx ->
        val entry = dock.getOrNull(idx)
        if (entry == null) {
            dockMenuIndex = null
        } else {
            val folderId = DockEntry.folderId(entry)
            MenuDialog(
                title = if (folderId != null) folders.firstOrNull { it.id == folderId }?.name ?: "フォルダ" else appMap[entry]?.label ?: "ドック",
                entries = if (folderId != null) {
                    listOf(
                        "フォルダを開く" to {
                            dockMenuIndex = null
                            openFolderId = folderId
                        },
                        "フォルダを削除" to {
                            dockMenuIndex = null
                            vm.deleteFolder(folderId)
                        },
                    )
                } else {
                    listOf(
                        "ドックから外す" to {
                            dockMenuIndex = null
                            vm.removeDockEntry(entry)
                        },
                    )
                },
                onDismiss = { dockMenuIndex = null },
            )
        }
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

/** グリッド上の1項目。セル座標をDpに変換して配置する。 */
@Composable
private fun GridItem(item: HomeItem, env: HomeEnv, cellW: Dp, cellH: Dp) {
    val s = env.settings
    val w = cellW * item.spanX
    val h = cellH * item.spanY
    val single = item.spanX == 1 && item.spanY == 1
    val iconDp = if (single) min(s.iconSizeDp, (cellW.value - 8f).toInt()).coerceAtLeast(24)
    else (min(w.value, h.value) * 0.6f).toInt().coerceAtLeast(24)
    val iconAlpha = if (item.alpha >= 0f) item.alpha else s.iconAlpha

    Box(
        Modifier
            .offset(cellW * item.x, cellH * item.y)
            .size(w, h),
    ) {
        when (item.type) {
            HomeItem.APP -> {
                val app = env.appMap[item.appKey]
                if (app != null) {
                    AppCell(
                        app = app,
                        iconSizeDp = iconDp,
                        showLabel = s.showLabels,
                        labelSizeSp = s.labelSizeSp,
                        onWallpaper = true,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { env.onLaunch(app) },
                        onLongClick = { env.onSelect(item.id) },
                        iconAlpha = iconAlpha,
                    )
                }
            }
            HomeItem.SHORTCUT -> ShortcutCell(
                item = item,
                iconSizeDp = iconDp,
                showLabel = s.showLabels,
                labelSizeSp = s.labelSizeSp,
                iconAlpha = iconAlpha,
                modifier = Modifier.fillMaxSize(),
                onClick = { env.onLaunchShortcut(item) },
                onLongClick = { env.onSelect(item.id) },
            )
            HomeItem.FOLDER -> {
                val folder = env.folders.firstOrNull { it.id == item.ref }
                if (folder != null) {
                    FolderTile(
                        folder = folder,
                        appMap = env.appMap,
                        iconSizeDp = iconDp,
                        showLabel = s.showLabels,
                        labelSizeSp = s.labelSizeSp,
                        iconAlpha = iconAlpha,
                        onWallpaper = true,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { env.onOpenFolder(folder.id) },
                        onLongClick = { env.onSelect(item.id) },
                    )
                }
            }
            HomeItem.WIDGET -> WidgetBox(
                item = item,
                widgets = env.widgets,
                alphaValue = if (item.alpha >= 0f) item.alpha else s.widgetGlobalAlpha,
                widthDp = w.value.toInt(),
                heightDp = h.value.toInt(),
                onSelect = { env.onSelect(item.id) },
            )
            HomeItem.STACK -> StackBox(
                children = env.items.filter { it.parent == item.id },
                widgets = env.widgets,
                settings = s,
                widthDp = w.value.toInt(),
                heightDp = h.value.toInt(),
                onSelect = { env.onSelect(item.id) },
            )
            HomeItem.SEARCH -> SearchBarTile(
                bgAlpha = if (item.alpha >= 0f) item.alpha else s.searchBarAlpha,
                onClick = env.onOpenSearch,
                onLongClick = { env.onSelect(item.id) },
            )
            HomeItem.MEDIA -> MediaBar(
                state = env.mediaState,
                media = env.media,
                bgAlpha = if (item.alpha >= 0f) item.alpha else s.mediaBarAlpha,
                onRequestPermission = env.onRequestNotificationAccess,
                onLongClick = { env.onSelect(item.id) },
            )
        }
    }
}

/**
 * 選択中の項目の枠。本体をドラッグで移動、右下のつまみをドラッグでリサイズ(離すとマスにスナップ)。
 * 左右の端に寄せたままにすると隣のページへ送り、ページをまたいで移動できる。
 */
@Composable
private fun EditFrame(
    item: HomeItem,
    cellWpx: Float,
    cellHpx: Float,
    cols: Int,
    rows: Int,
    viewportWpx: Float,
    carrying: Boolean,
    onCarry: (Boolean) -> Unit,
    onEdge: (Int) -> Unit,
    onCommit: (x: Int, y: Int, sx: Int, sy: Int) -> Unit,
) {
    val density = LocalDensity.current
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var dw by remember { mutableFloatStateOf(0f) }
    var dh by remember { mutableFloatStateOf(0f) }
    val baseW = cellWpx * item.spanX
    val baseH = cellHpx * item.spanY
    val shape = RoundedCornerShape(12.dp)
    val primary = MaterialTheme.colorScheme.primary

    // 端に寄せている間、一定間隔でページを送る
    val edgePx = with(density) { 40.dp.toPx() }
    val centerX = item.x * cellWpx + dx + (baseW + dw) / 2f
    val edgeDir = if (!carrying) 0 else when {
        centerX < edgePx -> -1
        centerX > viewportWpx - edgePx -> 1
        else -> 0
    }
    LaunchedEffect(edgeDir) {
        if (edgeDir != 0) {
            while (true) {
                delay(700)
                onEdge(edgeDir)
            }
        }
    }

    Box(
        Modifier
            .offset { IntOffset((item.x * cellWpx + dx).roundToInt(), (item.y * cellHpx + dy).roundToInt()) }
            .size(
                with(density) { (baseW + dw).coerceAtLeast(cellWpx * 0.5f).toDp() },
                with(density) { (baseH + dh).coerceAtLeast(cellHpx * 0.5f).toDp() },
            )
            .border(2.dp, primary, shape)
            .background(primary.copy(alpha = 0.15f), shape)
            .pointerInput(item.id, item.x, item.y, item.spanX, item.spanY, cols, rows) {
                detectDragGestures(
                    onDragStart = { onCarry(true) },
                    onDragEnd = {
                        val nx = ((item.x * cellWpx + dx) / cellWpx).roundToInt().coerceIn(0, cols - item.spanX)
                        val ny = ((item.y * cellHpx + dy) / cellHpx).roundToInt().coerceIn(0, rows - item.spanY)
                        dx = 0f
                        dy = 0f
                        onCarry(false)
                        onCommit(nx, ny, item.spanX, item.spanY)
                    },
                    onDragCancel = {
                        dx = 0f
                        dy = 0f
                        onCarry(false)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                    },
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(44.dp)
                .pointerInput(item.id, item.x, item.y, item.spanX, item.spanY, cols, rows) {
                    detectDragGestures(
                        onDragEnd = {
                            val nsx = ((baseW + dw) / cellWpx).roundToInt().coerceIn(1, cols - item.x)
                            val nsy = ((baseH + dh) / cellHpx).roundToInt().coerceIn(1, rows - item.y)
                            dw = 0f
                            dh = 0f
                            onCommit(item.x, item.y, nsx, nsy)
                        },
                        onDragCancel = {
                            dw = 0f
                            dh = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dw += amount.x
                            dh += amount.y
                        },
                    )
                }
                .padding(10.dp)
                .clip(CircleShape)
                .background(primary),
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(3.dp)
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.45f)),
            )
        }
    }
}

/** ドックの1スロット。アプリ・フォルダ・空きのいずれか。アイコンの不透明度・大きさはドック専用の設定を使う。 */
@Composable
private fun DockSlot(entry: String?, env: HomeEnv, onEmpty: () -> Unit, onLong: () -> Unit) {
    val s = env.settings
    val iconDp = min(s.iconSizeDp, s.dockHeightDp - 8).coerceAtLeast(24)
    val folderId = entry?.let { DockEntry.folderId(it) }
    val folder = folderId?.let { id -> env.folders.firstOrNull { it.id == id } }
    val app = if (folderId == null && entry != null) env.appMap[entry] else null
    when {
        folder != null -> FolderTile(
            folder = folder,
            appMap = env.appMap,
            iconSizeDp = iconDp,
            showLabel = false,
            labelSizeSp = s.labelSizeSp,
            iconAlpha = s.dockIconAlpha,
            onWallpaper = false,
            onClick = { env.onOpenFolder(folder.id) },
            onLongClick = onLong,
        )
        app != null -> AppCell(
            app = app,
            iconSizeDp = iconDp,
            showLabel = false,
            labelSizeSp = s.labelSizeSp,
            onWallpaper = false,
            onClick = { env.onLaunch(app) },
            onLongClick = onLong,
            iconAlpha = s.dockIconAlpha,
        )
        else -> Box(
            Modifier
                .size(iconDp.dp)
                .clip(CircleShape)
                .clickable(onClick = onEmpty),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SearchBarTile(bgAlpha: Float, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = bgAlpha))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Text(
                "アプリ・Webを検索",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** メディア操作バー。文字色を明示し、背景の不透明度は設定(個別設定)で変えられる。 */
@Composable
private fun MediaBar(
    state: MediaHelper.State,
    media: MediaHelper,
    bgAlpha: Float,
    onRequestPermission: () -> Unit,
    onLongClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = bgAlpha))
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                if (!state.permitted) {
                    Text(
                        "曲名を表示するには通知アクセスを許可",
                        color = onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onRequestPermission),
                    )
                } else {
                    Text(
                        state.title ?: if (state.hasSession) "再生中のメディア" else "メディアなし",
                        color = onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.artist?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val colors = ButtonDefaults.textButtonColors(contentColor = onSurface)
            TextButton(onClick = { media.previous() }, colors = colors) { Text("⏮", fontSize = 20.sp) }
            TextButton(onClick = { media.playPause() }, colors = colors) { Text(if (state.playing) "⏸" else "▶", fontSize = 20.sp) }
            TextButton(onClick = { media.next() }, colors = colors) { Text("⏭", fontSize = 20.sp) }
        }
    }
}

/** 検索バーをタップしたときの全画面検索。アプリ名の部分一致とWeb検索。 */
@Composable
private fun SearchOverlay(
    apps: List<AppInfo>,
    searchUrl: String,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val matches = remember(apps, query) {
        AppSorter.sort(AppSorter.filter(apps, query), SortMode.NAME, emptyMap()).take(50)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("アプリ・Webを検索") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { openWebSearch(context, searchUrl, query) }),
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                TextButton(onClick = onClose) { Text("閉じる") }
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (query.isNotBlank()) {
                    item {
                        Text(
                            "「${query.trim()}」をWebで検索",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openWebSearch(context, searchUrl, query) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                items(matches, key = { it.key }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onLaunch(app) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, 40)
                        Text(
                            app.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 位置・サイズ・不透明度を数値で編集する(ドラッグ操作の補助)。重なる場合は設定の動作で配置する。 */
@Composable
private fun ItemEditDialog(
    item: HomeItem,
    settings: LauncherSettings,
    onApply: (HomeItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasAlpha = item.type != HomeItem.STACK
    val globalAlpha = when (item.type) {
        HomeItem.WIDGET -> settings.widgetGlobalAlpha
        HomeItem.SEARCH -> settings.searchBarAlpha
        HomeItem.MEDIA -> settings.mediaBarAlpha
        else -> settings.iconAlpha
    }
    var page by remember { mutableIntStateOf(item.page + 1) }
    var x by remember { mutableIntStateOf(item.x + 1) }
    var y by remember { mutableIntStateOf(item.y + 1) }
    var sx by remember { mutableIntStateOf(item.spanX) }
    var sy by remember { mutableIntStateOf(item.spanY) }
    var useGlobal by remember { mutableStateOf(item.alpha < 0f) }
    var alphaV by remember { mutableFloatStateOf(if (item.alpha < 0f) globalAlpha else item.alpha) }

    val cols = settings.gridCols
    val rows = settings.gridRows
    val spanX = sx.coerceIn(1, cols)
    val spanY = sy.coerceIn(1, rows)
    val xMax = cols - spanX + 1
    val yMax = rows - spanY + 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("位置・サイズ・不透明度") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                IntSliderRow("ページ", page.coerceIn(1, settings.pageCount), 1, settings.pageCount) { page = it }
                IntSliderRow("幅(マス)", spanX, 1, cols) { sx = it }
                IntSliderRow("高さ(マス)", spanY, 1, rows) { sy = it }
                IntSliderRow("横位置", x.coerceIn(1, xMax), 1, xMax) { x = it }
                IntSliderRow("縦位置", y.coerceIn(1, yMax), 1, yMax) { y = it }
                if (hasAlpha) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("全体の不透明度設定を使う", Modifier.weight(1f))
                        Switch(checked = useGlobal, onCheckedChange = { useGlobal = it })
                    }
                    if (!useGlobal) {
                        PercentSliderRow("この項目の不透明度", alphaV, 0.1f, 1f) { alphaV = it }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(
                    item.copy(
                        page = page.coerceIn(1, settings.pageCount) - 1,
                        x = x.coerceIn(1, xMax) - 1,
                        y = y.coerceIn(1, yMax) - 1,
                        spanX = spanX,
                        spanY = spanY,
                        alpha = if (!hasAlpha || useGlobal) -1f else alphaV,
                    ),
                )
            }) { Text("適用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
