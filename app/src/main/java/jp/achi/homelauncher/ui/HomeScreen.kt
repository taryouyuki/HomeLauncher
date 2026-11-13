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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
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
import jp.achi.homelauncher.media.BadgeStore
import jp.achi.homelauncher.media.MediaHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    val editing: Boolean,
    val badges: Map<String, List<BadgeStore.Notif>>,
    val onDoubleTap: (String) -> Unit,
    val onBadgeMenu: (HomeItem) -> Unit,
    val dockDtap: Map<String, String>,
    val onDockDtap: (String) -> Unit,
    val onAction: (String) -> Unit,
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
    val badges by BadgeStore.notifs.collectAsStateWithLifecycle()
    val yomi by vm.yomi.collectAsStateWithLifecycle()
    val dockDtap by vm.dockDtap.collectAsStateWithLifecycle()
    val searchHistory by vm.searchHistory.collectAsStateWithLifecycle()
    val adjustSnap by vm.adjust.collectAsStateWithLifecycle()
    var quickOpen by remember { mutableStateOf(false) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var carrying by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var emptyMenu by remember { mutableStateOf(false) }
    var emptyMenuPage by remember { mutableStateOf("") }
    var scPickApp by remember { mutableStateOf(false) }
    var scApp by remember { mutableStateOf<AppInfo?>(null) }
    var scAt by remember { mutableStateOf<CellAt?>(null) }
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
    /** アプリのショートカット一覧・アプリ情報メニュー(編集バーから) */
    var appMenuItem by remember { mutableStateOf<HomeItem?>(null) }
    /** フォルダ作成先。-1 ならホーム、0以上ならドックの位置。null は非表示。 */
    var newFolderAt by remember { mutableStateOf<Int?>(null) }
    var stackManageId by remember { mutableStateOf<String?>(null) }
    var dockEmptyIndex by remember { mutableStateOf<Int?>(null) }
    var dockAppPickIndex by remember { mutableStateOf<Int?>(null) }
    var dockMenuIndex by remember { mutableStateOf<Int?>(null) }
    var cellDp by remember { mutableStateOf(80f to 80f) }
    /** 通知を見る対象のアプリ項目(メニュー → 通知一覧) */
    var badgeMenuItem by remember { mutableStateOf<HomeItem?>(null) }
    var notifPkg by remember { mutableStateOf<String?>(null) }
    /** ダブルタップ設定: 対象アプリ選択 → ショートカット選択 */
    var dtapItemId by remember { mutableStateOf<String?>(null) }
    var dtapApp by remember { mutableStateOf<AppInfo?>(null) }
    /** アイコンセットの管理ダイアログ / アプリ追加ピッカー、ウィジェット単位の透明度ダイアログ */
    var iconSetManageId by remember { mutableStateOf<String?>(null) }
    var iconSetPickId by remember { mutableStateOf<String?>(null) }
    var alphaItemId by remember { mutableStateOf<String?>(null) }
    /** 値が変わるとウィジェット等を作り直して画面を更新する */
    var refreshTick by remember { mutableIntStateOf(0) }

    var editMode by remember { mutableStateOf(false) }
    val editingNow = selectedId != null || editMode
    val editingRef by rememberUpdatedState(editingNow)
    val editModeRef by rememberUpdatedState(editMode)
    val selectedRef by rememberUpdatedState(selectedId)

    val pageCountNow = settings.pageCount.coerceAtLeast(1)
    val loopHome = settings.homeLoop && pageCountNow > 1
    val homeVirtual = if (loopHome) pageCountNow * 1000 else pageCountNow
    val homeBase = if (loopHome) pageCountNow * 500 else 0
    val mainPage = settings.homePage.coerceIn(0, pageCountNow - 1)
    val pagerState = rememberPagerState(initialPage = homeBase + mainPage, pageCount = { homeVirtual })
    val curPage = pagerState.currentPage % pageCountNow
    val selectedItem = items.firstOrNull { it.id == selectedId }

    LaunchedEffect(resetSignal) {
        // ホームボタンで戻ってきたら、検索・編集モード・メニューを閉じて1ページ目へ
        searchOpen = false
        selectedId = null
        editMode = false
        carrying = false
        emptyMenu = false
        openFolderId = null
        quickOpen = false
        pagerState.scrollToPage(homeBase + mainPage)
    }

    BackHandler(enabled = quickOpen && !searchOpen) { quickOpen = false }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = (selectedId != null || editMode) && !searchOpen) {
        if (selectedId != null) selectedId = null else editMode = false
    }

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

    /** 幅をいっぱいに使う。高さは、ウィジェット(スタックなら中の全ウィジェット)が示す縦横比に合わせる。 */
    fun fitWidth(item: HomeItem) {
        val s = vm.settings.value
        val kids = if (item.type == HomeItem.STACK) items.filter { it.parent == item.id } else listOf(item)
        val ratios = kids.mapNotNull { w ->
            widgets.findProvider(w.provider)?.let { i -> i.minHeight.toFloat() / i.minWidth.coerceAtLeast(1) }
        }
        val cw = cellDp.first
        val ch = cellDp.second
        val ratio = ratios.maxOrNull() ?: ((item.spanY * ch) / (item.spanX * cw))
        val spanY = ceil(cw * s.gridCols * ratio / ch).toInt().coerceIn(1, s.gridRows)
        val y = item.y.coerceAtMost(s.gridRows - spanY)
        if (vm.placeAndResolve(item.copy(free = false, x = 0, y = y, spanX = s.gridCols, spanY = spanY))) {
            toast(context, vm.overlapMessage())
        } else {
            toast(context, "幅いっぱいに調整しました")
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

    /**
     * アプリ/ショートカットを、重なった先のアイコン・フォルダ・アイコンセットにまとめる。まとめたら true。
     * (rx, ry, rw, rh) はドロップした枠の位置とサイズ(マス単位の小数)。最も大きく重なった相手を選ぶので、
     * 自由配置モードでも、マスに揃える場合でも同じように判定できる。
     */
    fun tryMerge(sel: HomeItem, page: Int, rx: Float, ry: Float, rw: Float, rh: Float): Boolean {
        if (!vm.settings.value.mergeOnDrop) return false
        if (sel.type != HomeItem.APP && sel.type != HomeItem.SHORTCUT && sel.type != HomeItem.FOLDER) return false
        var best: HomeItem? = null
        var bestRatio = 0f
        for (t in vm.items.value) {
            if (t.id == sel.id || t.parent.isNotEmpty() || t.page != page) continue
            val ok = t.type == HomeItem.ICONS ||
                (sel.type != HomeItem.FOLDER && (t.type == HomeItem.APP || t.type == HomeItem.SHORTCUT || t.type == HomeItem.FOLDER))
            if (!ok) continue
            val tx = if (t.free) t.fx else t.x.toFloat()
            val ty = if (t.free) t.fy else t.y.toFloat()
            val tw = if (t.free) t.fw else t.spanX.toFloat()
            val th = if (t.free) t.fh else t.spanY.toFloat()
            val ix = (minOf(rx + rw, tx + tw) - maxOf(rx, tx)).coerceAtLeast(0f)
            val iy = (minOf(ry + rh, ty + th) - maxOf(ry, ty)).coerceAtLeast(0f)
            val area = ix * iy
            val ratio = area / minOf(rw * rh, tw * th).coerceAtLeast(0.01f)
            if (ratio > bestRatio) {
                bestRatio = ratio
                best = t
            }
        }
        val target = best?.takeIf { bestRatio >= 0.35f } ?: return false
        val ok = if (target.type == HomeItem.ICONS) vm.moveHomeAppToIconSet(sel.id, target.id)
        else vm.mergeIntoFolder(sel.id, target.id)
        if (ok) {
            selectedId = null
            toast(context, if (target.type == HomeItem.ICONS) "アイコンセットに追加しました" else "フォルダにまとめました")
        }
        return ok
    }

    /** ドラッグ移動・リサイズの確定。アイコンを別のアイコン/フォルダ/アイコンセットに重ねた場合はまとめる。 */
    fun commitMove(sel: HomeItem, page: Int, nx: Int, ny: Int, nsx: Int, nsy: Int) {
        if (nsx == 1 && nsy == 1 && tryMerge(sel, page, nx.toFloat(), ny.toFloat(), 1f, 1f)) return
        val stored = vm.items.value.firstOrNull { it.id == sel.id } ?: return
        val collided = vm.placeAndResolve(stored.copy(page = page, x = nx, y = ny, spanX = nsx, spanY = nsy, free = false))
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
        editing = editingNow,
        badges = badges,
        onDoubleTap = { code -> launchDtap(context, vm, appMap, code) },
        onBadgeMenu = { badgeMenuItem = it },
        dockDtap = dockDtap,
        onDockDtap = { code -> launchDtap(context, vm, appMap, code) },
        onAction = { kind -> if (kind == "drawer") onOpenDrawer() else quickOpen = true },
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = settings.scrimAlpha))
            .pointerInput(Unit) {
                // 下から上へのスワイプでアプリ一覧。入力は消費せずに見張るだけなので、ウィジェット
                // (メモ・Keep など)の中の縦スクロールを妨げない。ウィジェットに触れて始めたスワイプでは開かない。
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    SwipeGuard.widgetTouched = false
                    var tx = 0f
                    var ty = 0f
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        val d = c.positionChange()
                        tx += d.x
                        ty += d.y
                    }
                    if (!SwipeGuard.widgetTouched && !editingRef && ty < -120f && abs(ty) > abs(tx) * 1.5f) {
                        onOpenDrawer()
                    } else if (!SwipeGuard.widgetTouched && !editingRef && ty > 160f && abs(ty) > abs(tx) * 1.5f &&
                        vm.settings.value.quickPanel
                    ) {
                        quickOpen = true
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(settings.marginDp.dp)) {
                val cols = settings.gridCols
                val rows = settings.gridRows
                val cellW = maxWidth / cols
                val cellH = maxHeight / rows
                val cellWpx = with(density) { cellW.toPx() }
                val cellHpx = with(density) { cellH.toPx() }
                val viewportWpx = with(density) { maxWidth.toPx() }
                LaunchedEffect(cellW, cellH) { cellDp = cellW.value to cellH.value }

                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { vp ->
                    val page = vp % pageCountNow
                    val shown = items
                        .filter { it.page == page && it.parent.isEmpty() }
                        .map { GridLogic.clamp(it, cols, rows) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(page, cellWpx, cellHpx, cols, rows) {
                                detectTapGestures(
                                    onTap = { off ->
                                        if (editModeRef && selectedRef == null) {
                                            // 編集モード: 空き地をタップで、その場所に追加するメニュー
                                            val cx = (off.x / cellWpx).toInt().coerceIn(0, cols - 1)
                                            val cy = (off.y / cellHpx).toInt().coerceIn(0, rows - 1)
                                            pendingAt = Triple(page, cx, cy)
                                            emptyMenu = true
                                        } else {
                                            selectedId = null
                                        }
                                    },
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
                            key(item.id, refreshTick) { GridItem(item, env, cellW, cellH) }
                        }
                    }
                }

                // 編集枠はページの外(ビューポート基準)に置き、ページをまたいでドラッグできるようにする
                val sel = selectedItem?.takeIf { it.parent.isEmpty() }
                if (sel != null && (sel.page == curPage || carrying)) {
                    EditFrame(
                        item = GridLogic.clamp(sel, cols, rows),
                        cellWpx = cellWpx,
                        cellHpx = cellHpx,
                        cols = cols,
                        rows = rows,
                        viewportWpx = viewportWpx,
                        carrying = carrying,
                        freeMode = settings.freeMode &&
                            (!settings.freeWidgetsOnly || sel.type != HomeItem.APP && sel.type != HomeItem.SHORTCUT && sel.type != HomeItem.FOLDER),
                        onCarry = { carrying = it },
                        onEdge = { dir ->
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + dir).coerceIn(0, homeVirtual - 1),
                                )
                            }
                        },
                        onCommit = { nx, ny, nsx, nsy -> commitMove(sel, curPage, nx, ny, nsx, nsy) },
                        onCommitFree = { fx, fy, fw, fh ->
                            val stored = vm.items.value.firstOrNull { it.id == sel.id }
                            if (stored != null && tryMerge(stored, curPage, fx, fy, fw, fh)) {
                                // まとめた
                            } else if (stored != null) {
                                vm.placeFree(stored.copy(page = curPage, fx = fx, fy = fy, fw = fw, fh = fh))
                            }
                        },
                    )
                }
            }

            if (settings.pageCount > 1) {
                PageDots(count = settings.pageCount, current = curPage)
            }

            if (settings.showDock) {
                val perPage = settings.dockCount
                val autoPages = (dock.size + perPage) / perPage // ceil((size + 1) / perPage)
                val neededPages = ((dock.size + perPage - 1) / perPage).coerceAtLeast(1)
                // 設定でページ数を指定したときは、その数に固定(入りきらない分だけ自動で増える)
                val dockPages = if (settings.dockPages > 0) maxOf(settings.dockPages, neededPages) else autoPages
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
                            // アイコンサイズに合わせて、ドックの高さが足りなければ自動で広げる
                            modifier = Modifier.fillMaxWidth().height(maxOf(settings.dockHeightDp, settings.dockIconSizeDp + 16).dp),
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
                                            index = idx,
                                            onMove = { from, to -> vm.moveDockEntry(from, to) },
                                            onLift = { buzz() },
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

        // 編集ボタン(長押しなしで編集モードに入る)
        if (settings.showEditButton && !settings.lockLayout && !searchOpen) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(end = 10.dp, bottom = (if (settings.showDock) settings.dockHeightDp + 44 else 20).dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (editMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    )
                    .clickable {
                        editMode = !editMode
                        selectedId = null
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (editMode) "✓" else "✎",
                    color = if (editMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                )
            }
        }
        if (editMode && selectedItem == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "編集モード: タップで選択 / 空き地で追加",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    TextButton(onClick = { editMode = false }) { Text("完了") }
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
                    if (selectedItem.type == HomeItem.APP || selectedItem.type == HomeItem.SHORTCUT) {
                        TextButton(onClick = { folderPickItem = selectedItem }) { Text("フォルダへ") }
                    }
                    if (selectedItem.type == HomeItem.APP) {
                        TextButton(onClick = { appMenuItem = selectedItem }) { Text("ショートカット・情報") }
                    }
                    if (selectedItem.type == HomeItem.WIDGET || selectedItem.type == HomeItem.STACK) {
                        TextButton(onClick = { fitWidth(selectedItem) }) { Text("幅いっぱいに") }
                    }
                    if (selectedItem.type == HomeItem.WIDGET) {
                        TextButton(onClick = {
                            if (vm.convertWidgetToStack(selectedItem.id)) {
                                toast(context, "ウィジェットスタックに変換しました(長押し →「スタック管理」で追加)")
                            }
                        }) { Text("スタックに変換") }
                    }
                    if (selectedItem.type == HomeItem.APP || selectedItem.type == HomeItem.SHORTCUT) {
                        TextButton(onClick = { dtapItemId = selectedItem.id }) {
                            Text(if (selectedItem.dtap.isEmpty()) "ダブルタップ設定" else "ダブルタップ設定(設定済)")
                        }
                        if (selectedItem.dtap.isNotEmpty()) {
                            TextButton(onClick = {
                                vm.updateItem(selectedItem.id) { it.copy(dtap = "") }
                                toast(context, "ダブルタップ設定を解除しました")
                            }) { Text("ダブルタップ解除") }
                        }
                    }
                    if (selectedItem.type == HomeItem.WIDGET || selectedItem.type == HomeItem.STACK) {
                        TextButton(onClick = { alphaItemId = selectedItem.id }) {
                            Text(if (selectedItem.alpha < 0f) "透明度" else "透明度(個別)")
                        }
                    }
                    if (selectedItem.type == HomeItem.STACK) {
                        TextButton(onClick = { stackManageId = selectedItem.id }) { Text("スタック管理") }
                        TextButton(onClick = {
                            vm.dissolve(selectedItem.id)
                            selectedId = null
                            toast(context, "スタックを解除しました(ウィジェットを個別に出しました)")
                        }) { Text("スタック解除") }
                    }
                    if (selectedItem.type == HomeItem.ICONS) {
                        TextButton(onClick = {
                            vm.dissolve(selectedItem.id)
                            selectedId = null
                            toast(context, "アイコンセットを解除しました")
                        }) { Text("セット解除") }
                        TextButton(onClick = { iconSetManageId = selectedItem.id }) { Text("セット管理") }
                    }
                    TextButton(onClick = { deleteItem(selectedItem) }) { Text("削除") }
                    TextButton(onClick = {
                        selectedId = null
                        editMode = false
                    }) { Text("完了") }
                }
            }
        }

        adjustSnap?.let { snap ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(bottom = (if (settings.showDock) settings.dockHeightDp + 60 else 24).dp, start = 8.dp, end = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(
                        if (snap.showingBefore) "調整前を表示中" else "調整後を表示中",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    if (snap.showingBefore) {
                        TextButton(onClick = { vm.adjustShowAfter() }) { Text("調整後を見る") }
                    } else {
                        TextButton(onClick = { vm.adjustShowBefore() }) { Text("元に戻して見る") }
                    }
                    TextButton(onClick = { vm.adjustKeep() }) { Text(if (snap.showingBefore) "このまま確定" else "確定") }
                }
            }
        }

        if (quickOpen) {
            QuickPanel(
                apps = apps,
                quickApps = settings.quickApps,
                mediaState = mediaState,
                media = media,
                onLaunch = {
                    quickOpen = false
                    launchApp(it)
                },
                onChange = { v -> vm.updateSettings { it.copy(quickApps = v) } },
                onRequestNotificationAccess = onRequestNotificationAccess,
                onClose = { quickOpen = false },
            )
        }

        if (searchOpen) {
            SearchOverlay(
                apps = apps,
                yomi = yomi,
                searchUrl = settings.searchUrl,
                history = searchHistory.take(settings.searchHistoryCount),
                onRecord = { vm.addHistory(it) },
                onClearHistory = { vm.clearHistory() },
                onLaunch = {
                    vm.addHistory("app:${it.key}")
                    searchOpen = false
                    launchApp(it)
                },
                onClose = { searchOpen = false },
            )
        }
    }

    // ---- メニュー・ダイアログ ----
    LaunchedEffect(emptyMenu) { if (!emptyMenu) emptyMenuPage = "" }
    if (emptyMenu) {
        val at = pendingAt
        val addEntries = buildList<Pair<String, () -> Unit>> {
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
            add("ここに「アプリ一覧」アイコンを置く" to {
                emptyMenu = false
                report(vm.createAction("drawer", at))
            })
            add("ここに「クイックパネル」アイコンを置く" to {
                emptyMenu = false
                report(vm.createAction("quick", at))
            })
            add("ここにアイコンセットを作成(ページ切替)" to {
                emptyMenu = false
                report(vm.createIconSet(at))
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
            add("ここにショートカットだけを置く" to {
                emptyMenu = false
                scAt = at
                scPickApp = true
            })
        }
        val adjustEntries = buildList<Pair<String, () -> Unit>> {
            add("自動調整: すべて(アイコン+ウィジェット+重なり)" to {
                emptyMenu = false
                vm.autoAdjust(cellDp.first, cellDp.second)
                vm.autoAdjustWidgets { w -> widgets.findProvider(w.provider)?.let { widgets.suggestedSpan(it, cellDp.first, cellDp.second, density.density) } }
                refreshTick++
                toast(context, "大きさと配置を自動調整しました")
            })
            add("自動調整: ウィジェットのみ" to {
                emptyMenu = false
                vm.autoAdjustWidgets { w -> widgets.findProvider(w.provider)?.let { widgets.suggestedSpan(it, cellDp.first, cellDp.second, density.density) } }
                refreshTick++
                toast(context, "ウィジェットの大きさを自動調整しました")
            })
            add("自動調整: アプリアイコンのみ" to {
                emptyMenu = false
                vm.autoAdjustIcons(cellDp.first, cellDp.second)
                toast(context, "アイコン・文字・ドックの大きさを自動調整しました")
            })
            add("おまかせ微調整: すべて(元に戻せます)" to {
                emptyMenu = false
                vm.harmonize(true, true)
            })
            add("おまかせ微調整: ウィジェットのみ" to {
                emptyMenu = false
                vm.harmonize(false, true)
            })
            add("おまかせ微調整: アイコンのみ" to {
                emptyMenu = false
                vm.harmonize(true, false)
            })
            add("整列: アプリアイコンのみ" to {
                emptyMenu = false
                vm.arrangeIcons()
                toast(context, "アプリアイコンを整列しました")
            })
            add("整列: ウィジェットのみ" to {
                emptyMenu = false
                vm.arrangeWidgets()
                toast(context, "ウィジェットを整列しました")
            })
        }
        val topEntries = buildList<Pair<String, () -> Unit>> {
            add("追加する  ›" to { emptyMenuPage = "add" })
            add("整列・自動調整  ›" to { emptyMenuPage = "adjust" })
            add("ランチャー設定" to {
                emptyMenu = false
                onOpenSettings()
            })
            add("クイックパネルを開く" to {
                emptyMenu = false
                quickOpen = true
            })
            add("画面を更新(ウィジェット・アプリ・通知)" to {
                emptyMenu = false
                refreshAll(vm, widgets, media)
                refreshTick++
                toast(context, "画面を更新しました")
            })
            add((if (editMode) "編集モードを終わる" else "編集モードにする(タップで編集)") to {
                emptyMenu = false
                editMode = !editMode
                selectedId = null
            })
            add("壁紙を変更" to {
                emptyMenu = false
                runCatching {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "壁紙を変更")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { toast(context, "壁紙設定を開けません") }
            })
        }
        val entries = when (emptyMenuPage) {
            "add" -> listOf<Pair<String, () -> Unit>>("‹ 戻る" to { emptyMenuPage = "" }) + addEntries
            "adjust" -> listOf<Pair<String, () -> Unit>>("‹ 戻る" to { emptyMenuPage = "" }) + adjustEntries
            else -> topEntries
        }
        MenuDialog(
            title = when (emptyMenuPage) { "add" -> "追加する"; "adjust" -> "整列・自動調整"; else -> "ホーム画面" },
            entries = entries,
            onDismiss = { emptyMenu = false },
        )
    }

    if (scPickApp) {
        AppPickerDialog(
            apps = AppSorter.sort(apps, SortMode.NAME, emptyMap()),
            onPick = {
                scPickApp = false
                scApp = it
            },
            onDismiss = { scPickApp = false },
        )
    }
    scApp?.let { app ->
        val list = remember(app.key) { ShortcutHelper.list(context, app.packageName) }
        MenuDialog(
            title = "「${app.label}」のショートカット",
            entries = if (list.isEmpty()) listOf("ショートカットがありません" to { scApp = null })
            else list.map { (sid, label) ->
                label to {
                    scApp = null
                    report(vm.addShortcut(app.packageName, sid, label, scAt))
                }
            },
            onDismiss = { scApp = null },
        )
    }

    appMenuItem?.let { item ->
        val app = appMap[item.appKey]
        val shortcuts = remember(item.id) { ShortcutHelper.list(context, item.pkg) }
        MenuDialog(
            title = app?.label ?: "アプリ",
            entries = buildList {
                shortcuts.forEach { (sid, label) ->
                    add(label to {
                        appMenuItem = null
                        if (!ShortcutHelper.launch(context, item.pkg, sid)) toast(context, "ショートカットを開けません")
                    })
                }
                add("アプリ情報" to {
                    appMenuItem = null
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${item.pkg}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                })
                add("通知を見る" to {
                    appMenuItem = null
                    notifPkg = item.pkg
                })
            },
            onDismiss = { appMenuItem = null },
        )
    }

    badgeMenuItem?.let { item ->
        val app = appMap[item.appKey]
        MenuDialog(
            title = app?.label ?: "アプリ",
            entries = listOf(
                "通知を見る" to {
                    badgeMenuItem = null
                    notifPkg = item.pkg
                },
                "移動・編集" to {
                    badgeMenuItem = null
                    select(item.id)
                },
            ),
            onDismiss = { badgeMenuItem = null },
        )
    }

    notifPkg?.let { pkg ->
        NotificationsDialog(
            pkg = pkg,
            appLabel = appMap.values.firstOrNull { it.packageName == pkg }?.label ?: pkg,
            notifs = badges[pkg].orEmpty(),
            onDismiss = { notifPkg = null },
        )
    }

    dtapItemId?.let { id ->
        val isDock = id.startsWith("dock:")
        val dockEntry = id.removePrefix("dock:")
        val currentCode = if (isDock) dockDtap[dockEntry].orEmpty() else items.firstOrNull { it.id == id }?.dtap
        fun setCode(code: String) {
            if (isDock) vm.setDockDtap(dockEntry, code) else vm.updateItem(id) { it.copy(dtap = code) }
        }
        if (currentCode == null) {
            dtapItemId = null
        } else if (dtapApp == null) {
            AppPickerDialog(
                apps = AppSorter.sort(apps, SortMode.NAME, emptyMap()),
                onPick = { dtapApp = it },
                onDismiss = { dtapItemId = null },
            )
        } else {
            val app = dtapApp!!
            val shortcuts = remember(app.key) { ShortcutHelper.list(context, app.packageName) }
            MenuDialog(
                title = "「${app.label}」のどれを開く?",
                entries = buildList {
                    add("アプリ本体" to {
                        setCode("app:${app.key}")
                        dtapItemId = null
                        dtapApp = null
                        toast(context, "ダブルタップで「${app.label}」を開きます")
                    })
                    shortcuts.forEach { (sid, label) ->
                        add(label to {
                            setCode("sc:${app.packageName}|$sid")
                            dtapItemId = null
                            dtapApp = null
                            toast(context, "ダブルタップで「$label」を開きます")
                        })
                    }
                    if (currentCode.isNotEmpty()) {
                        add("ダブルタップ設定を解除" to {
                            setCode("")
                            dtapItemId = null
                            dtapApp = null
                        })
                    }
                },
                onDismiss = {
                    dtapItemId = null
                    dtapApp = null
                },
            )
        }
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
                cols = settings.folderCols,
                bgColor = if (settings.folderBgMode == "custom") Color(settings.folderBgColor) else null,
                bgAlpha = settings.folderBgAlpha,
                labelLines = settings.labelLines.coerceAtLeast(1),
                onLaunch = {
                    openFolderId = null
                    launchApp(it)
                },
                onPutOnHome = { app ->
                    if (vm.addAppByKey(app.key) != PlaceResult.FAILED) vm.removeFromFolder(folder.id, app.key)
                    else toast(context, "ホーム画面に空きがありません")
                },
                onRemoveFromFolder = { vm.removeFromFolder(folder.id, it.key) },
                onLaunchShortcut = { pkg, id ->
                    openFolderId = null
                    if (!ShortcutHelper.launch(context, pkg, id)) toast(context, "ショートカットを開けません")
                },
                onRemoveEntry = { vm.removeFromFolder(folder.id, it) },
                dtapMap = dockDtap,
                onDtap = { code ->
                    openFolderId = null
                    launchDtap(context, vm, appMap, code)
                },
                onSetDtap = { entry ->
                    openFolderId = null
                    dtapItemId = "dock:fd:$entry"
                },
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
            onMove = { child, delta -> vm.moveStackChild(child.id, delta) },
            onDismiss = { stackManageId = null },
        )
    }

    iconSetManageId?.let { sid ->
        val kids = items.filter { it.parent == sid }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { iconSetManageId = null },
            title = { Text("アイコンセットのアプリ") },
            confirmButton = { TextButton(onClick = { iconSetManageId = null }) { Text("閉じる") } },
            text = {
                Column {
                    if (kids.isEmpty()) Text("まだアプリがありません。", modifier = Modifier.padding(vertical = 8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        kids.forEach { k -> item(key = k.id) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (k.type) {
                                        HomeItem.FOLDER -> "📁 " + (folders.firstOrNull { it.id == k.ref }?.name ?: "フォルダ")
                                        HomeItem.SHORTCUT -> "↗ " + k.ref
                                        else -> appMap[k.appKey]?.label ?: k.pkg
                                    },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                TextButton(
                                    onClick = {
                                        iconSetManageId = null
                                        dtapItemId = k.id
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                                ) { Text(if (k.dtap.isEmpty()) "ダブルタップ" else "ダブルタップ✓") }
                                TextButton(onClick = { vm.moveStackChild(k.id, -1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { Text("↑") }
                                TextButton(onClick = { vm.moveStackChild(k.id, 1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { Text("↓") }
                                TextButton(onClick = { vm.removeItem(k.id) }) { Text("外す") }
                            }
                        } }
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            iconSetPickId = sid
                            iconSetManageId = null
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("アプリを追加") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { vm.createFolderInSet(sid) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text("フォルダを追加") }
                    Text(
                        "ホームのショートカット・フォルダは、セットに重ねてドロップすると入ります。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
        )
    }

    iconSetPickId?.let { sid ->
        AppPickerDialog(
            apps = AppSorter.sort(apps, SortMode.NAME, emptyMap()),
            onPick = {
                vm.addToIconSet(sid, it.packageName, it.className)
                iconSetPickId = null
                iconSetManageId = sid
            },
            onDismiss = { iconSetPickId = null },
        )
    }

    alphaItemId?.let { aid ->
        val target = items.firstOrNull { it.id == aid }
        if (target == null) {
            alphaItemId = null
        } else {
            var useGlobal by remember(aid) { mutableStateOf(target.alpha < 0f) }
            var v by remember(aid) { mutableFloatStateOf(if (target.alpha < 0f) settings.widgetGlobalAlpha else target.alpha) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { alphaItemId = null },
                title = { Text("このウィジェットだけの透明度") },
                text = {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("全体設定に従う", Modifier.weight(1f))
                            Switch(checked = useGlobal, onCheckedChange = { useGlobal = it })
                        }
                        if (!useGlobal) PercentSliderRow("不透明度", v, 0.1f, 1f) { v = it }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.updateItem(aid) { it.copy(alpha = if (useGlobal) -1f else v) }
                        alphaItemId = null
                    }) { Text("適用") }
                },
                dismissButton = { TextButton(onClick = { alphaItemId = null }) { Text("キャンセル") } },
            )
        }
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
                        "ここにアプリを追加(今あるものは右へずらす)" to {
                            dockMenuIndex = null
                            dockEmptyIndex = idx
                        },
                        "◀ 左へ移動(続けて動かせます)" to {
                            val n = (idx - 1).coerceAtLeast(0)
                            vm.moveDockEntry(idx, n)
                            dockMenuIndex = n
                        },
                        "右へ移動 ▶(続けて動かせます)" to {
                            val n = (idx + 1).coerceAtMost((dock.size - 1).coerceAtLeast(0))
                            vm.moveDockEntry(idx, n)
                            dockMenuIndex = n
                        },
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
                        "ここにアプリを追加(今あるものは右へずらす)" to {
                            dockMenuIndex = null
                            dockEmptyIndex = idx
                        },
                        "◀ 左へ移動(続けて動かせます)" to {
                            val n = (idx - 1).coerceAtLeast(0)
                            vm.moveDockEntry(idx, n)
                            dockMenuIndex = n
                        },
                        "右へ移動 ▶(続けて動かせます)" to {
                            val n = (idx + 1).coerceAtMost((dock.size - 1).coerceAtLeast(0))
                            vm.moveDockEntry(idx, n)
                            dockMenuIndex = n
                        },
                        "ダブルタップ設定" + (if (dockDtap[entry].isNullOrEmpty()) "" else "(設定済)") to {
                            dockMenuIndex = null
                            dtapItemId = "dock:$entry"
                        },
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

/** グリッド上の1項目。セル座標(自由配置なら小数のセル座標)をDpに変換して配置する。 */
@Composable
private fun GridItem(item: HomeItem, env: HomeEnv, cellW: Dp, cellH: Dp) {
    val s = env.settings
    val px = if (item.free) item.fx else item.x.toFloat()
    val py = if (item.free) item.fy else item.y.toFloat()
    val w = if (item.free) cellW * item.fw else cellW * item.spanX
    val h = if (item.free) cellH * item.fh else cellH * item.spanY
    val single = if (item.free) item.fw <= 1.01f && item.fh <= 1.01f else item.spanX == 1 && item.spanY == 1
    val iconDp = when {
        item.free -> (min(w.value, h.value * (if (s.showLabels) 0.72f else 0.95f))).toInt().coerceIn(24, 200)
        single -> min(s.iconSizeDp, (cellW.value - 8f).toInt()).coerceAtLeast(24)
        else -> (min(w.value, h.value) * 0.6f).toInt().coerceAtLeast(24)
    }
    val iconAlpha = if (item.alpha >= 0f) item.alpha else s.iconAlpha
    val editing = env.editing
    // 編集モード中は、アイコンやウィジェットを開かず、タップでその項目を選択する
    fun act(f: () -> Unit): () -> Unit = if (editing) ({ env.onSelect(item.id) }) else f
    val dtapAction: (() -> Unit)? =
        if (item.dtap.isNotEmpty() && !editing) ({ env.onDoubleTap(item.dtap) }) else null

    Box(
        Modifier
            .offset(cellW * px, cellH * py)
            .size(w, h),
    ) {
        when (item.type) {
            HomeItem.APP -> {
                val app = env.appMap[item.appKey]
                if (app != null) {
                    val badge = if (s.showBadges) env.badges[app.packageName]?.size ?: 0 else 0
                    AppCell(
                        app = app,
                        iconSizeDp = iconDp,
                        showLabel = s.showLabels,
                        labelSizeSp = s.labelSizeSp,
                        onWallpaper = true,
                        modifier = Modifier.fillMaxSize(),
                        onClick = act { env.onLaunch(app) },
                        onLongClick = { if (badge > 0 && !editing) env.onBadgeMenu(item) else env.onSelect(item.id) },
                        iconAlpha = iconAlpha,
                        badge = badge,
                        onDoubleClick = dtapAction,
                        labelLines = s.labelLines,
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
                onClick = act { env.onLaunchShortcut(item) },
                onLongClick = { env.onSelect(item.id) },
                onDoubleClick = dtapAction,
                labelLines = s.labelLines,
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
                        onClick = act { env.onOpenFolder(folder.id) },
                        onLongClick = { env.onSelect(item.id) },
                        labelLines = s.labelLines,
                    )
                }
            }
            HomeItem.WIDGET -> WidgetBox(
                item = item,
                widgets = env.widgets,
                alphaValue = if (item.alpha >= 0f) item.alpha else s.widgetGlobalAlpha,
                widthDp = w.value.toInt(),
                heightDp = h.value.toInt(),
                locked = editing,
                onSelect = { env.onSelect(item.id) },
            )
            HomeItem.STACK -> StackBox(
                children = env.items.filter { it.parent == item.id },
                widgets = env.widgets,
                settings = s,
                widthDp = w.value.toInt(),
                heightDp = h.value.toInt(),
                locked = editing,
                onSelect = { env.onSelect(item.id) },
                alphaOverride = item.alpha,
            )
            HomeItem.ICONS -> IconSetBox(
                set = item,
                children = env.items.filter { it.parent == item.id },
                folders = env.folders,
                onLaunchShortcut = env.onLaunchShortcut,
                onOpenFolder = env.onOpenFolder,
                appMap = env.appMap,
                settings = s,
                widthDp = w.value.toInt(),
                heightDp = h.value.toInt(),
                locked = editing,
                badges = env.badges,
                onLaunch = env.onLaunch,
                onDoubleTap = env.onDoubleTap,
                onSelect = { env.onSelect(item.id) },
            )
            HomeItem.ACTION -> ActionIconCell(
                kind = item.ref,
                iconSizeDp = iconDp,
                showLabel = s.showLabels,
                labelSizeSp = s.labelSizeSp,
                iconAlpha = iconAlpha,
                modifier = Modifier.fillMaxSize(),
                onClick = act { env.onAction(item.ref) },
                onLongClick = { env.onSelect(item.id) },
            )
            HomeItem.SEARCH -> SearchBarTile(
                bgAlpha = if (item.alpha >= 0f) item.alpha else s.searchBarAlpha,
                onClick = act { env.onOpenSearch() },
                onLongClick = { env.onSelect(item.id) },
            )
            HomeItem.MEDIA -> Box(Modifier.fillMaxSize()) {
              MediaBar(
                state = env.mediaState,
                media = env.media,
                bgAlpha = if (item.alpha >= 0f) item.alpha else s.mediaBarAlpha,
                buttonSp = s.mediaButtonSp,
                overlay = s.mediaOverlay,
                showSource = s.mediaShowSource,
                onRequestPermission = env.onRequestNotificationAccess,
                onLongClick = { env.onSelect(item.id) },
              )
              if (editing) {
                  // 編集中はボタンを無効にして、タップでメディアバーを選択(移動・サイズ変更)できるようにする
                  Box(
                      Modifier.fillMaxSize().combinedClickable(
                          onClick = { env.onSelect(item.id) },
                          onLongClick = { env.onSelect(item.id) },
                      ),
                  )
              }
            }
        }
    }
}

/**
 * 選択中の項目の枠。本体をドラッグで移動、右下のつまみをドラッグでリサイズ。
 * 通常はマスにスナップ、自由配置モードでは位置・大きさをそのまま確定する。
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
    freeMode: Boolean,
    onCarry: (Boolean) -> Unit,
    onEdge: (Int) -> Unit,
    onCommit: (x: Int, y: Int, sx: Int, sy: Int) -> Unit,
    onCommitFree: (fx: Float, fy: Float, fw: Float, fh: Float) -> Unit,
) {
    val density = LocalDensity.current
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var dw by remember { mutableFloatStateOf(0f) }
    var dh by remember { mutableFloatStateOf(0f) }
    val ix = if (item.free) item.fx else item.x.toFloat()
    val iy = if (item.free) item.fy else item.y.toFloat()
    val iw = if (item.free) item.fw else item.spanX.toFloat()
    val ih = if (item.free) item.fh else item.spanY.toFloat()
    val baseW = cellWpx * iw
    val baseH = cellHpx * ih
    val shape = RoundedCornerShape(12.dp)
    val primary = MaterialTheme.colorScheme.primary

    // 端に寄せている間、一定間隔でページを送る
    val edgePx = with(density) { 40.dp.toPx() }
    val centerX = ix * cellWpx + dx + (baseW + dw) / 2f
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

    val keys = arrayOf<Any>(item.id, ix, iy, iw, ih, cols, rows, freeMode)
    Box(
        Modifier
            .offset { IntOffset((ix * cellWpx + dx).roundToInt(), (iy * cellHpx + dy).roundToInt()) }
            .size(
                with(density) { (baseW + dw).coerceAtLeast(cellWpx * 0.3f).toDp() },
                with(density) { (baseH + dh).coerceAtLeast(cellHpx * 0.3f).toDp() },
            )
            .border(2.dp, primary, shape)
            .background(primary.copy(alpha = 0.15f), shape)
            .pointerInput(*keys) {
                detectDragGestures(
                    onDragStart = { onCarry(true) },
                    onDragEnd = {
                        val px = (ix * cellWpx + dx) / cellWpx
                        val py = (iy * cellHpx + dy) / cellHpx
                        dx = 0f
                        dy = 0f
                        onCarry(false)
                        if (freeMode) {
                            onCommitFree(
                                px.coerceIn(0f, (cols - iw).coerceAtLeast(0f)),
                                py.coerceIn(0f, (rows - ih).coerceAtLeast(0f)),
                                iw, ih,
                            )
                        } else {
                            val sx = kotlin.math.ceil(iw).toInt().coerceIn(1, cols)
                            val sy = kotlin.math.ceil(ih).toInt().coerceIn(1, rows)
                            onCommit(px.roundToInt().coerceIn(0, cols - sx), py.roundToInt().coerceIn(0, rows - sy), sx, sy)
                        }
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
                .pointerInput(*keys) {
                    detectDragGestures(
                        onDragEnd = {
                            val w = (baseW + dw) / cellWpx
                            val h = (baseH + dh) / cellHpx
                            dw = 0f
                            dh = 0f
                            if (freeMode) {
                                onCommitFree(
                                    ix.coerceIn(0f, cols - 0.3f), iy.coerceIn(0f, rows - 0.3f),
                                    w.coerceIn(0.3f, cols - ix), h.coerceIn(0.3f, rows - iy),
                                )
                            } else {
                                val bx = if (item.free) item.x else item.x
                                val by = if (item.free) item.y else item.y
                                onCommit(
                                    bx, by,
                                    w.roundToInt().coerceIn(1, (cols - bx).coerceAtLeast(1)),
                                    h.roundToInt().coerceIn(1, (rows - by).coerceAtLeast(1)),
                                )
                            }
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

/**
 * ドックの1スロット。アプリ・フォルダ・空きのいずれか。アイコンの不透明度・大きさはドック専用の設定を使う。
 * 長押ししてそのまま左右へ動かすと並び替え(間のアイコンは1つずつずれる)、動かさず離すとメニュー。
 */
@Composable
private fun DockSlot(
    entry: String?,
    env: HomeEnv,
    index: Int,
    onMove: (Int, Int) -> Unit,
    onLift: () -> Unit,
    onEmpty: () -> Unit,
    onLong: () -> Unit,
) {
    val s = env.settings
    var slotW by remember { mutableFloatStateOf(1f) }
    val slotDp = (slotW / LocalDensity.current.density).toInt()
    val iconDp = (if (slotDp > 0) min(s.dockIconSizeDp, slotDp - 4) else s.dockIconSizeDp).coerceAtLeast(24)
    val folderId = entry?.let { DockEntry.folderId(it) }
    val folder = folderId?.let { id -> env.folders.firstOrNull { it.id == id } }
    val app = if (folderId == null && entry != null) env.appMap[entry] else null
    var dragDx by remember { mutableFloatStateOf(0f) }
    val currentLong by rememberUpdatedState(onLong)
    val currentMove by rememberUpdatedState(onMove)
    val currentLift by rememberUpdatedState(onLift)

    Box(
        Modifier
            .onSizeChanged { slotW = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(entry, index) {
                if (entry == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val lp = awaitLongPressOrCancellation(down.id)
                    if (lp != null) {
                        currentLift()
                        var total = 0f
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            total += c.positionChange().x
                            c.consume()
                            if (abs(total) > viewConfiguration.touchSlop) moved = true
                            if (moved) dragDx = total
                        }
                        val delta = (total / slotW).roundToInt()
                        dragDx = 0f
                        if (moved && delta != 0) currentMove(index, index + delta) else if (!moved) currentLong()
                    }
                }
            }
            .offset { IntOffset(dragDx.roundToInt(), 0) }
            .zIndex(if (dragDx != 0f) 1f else 0f),
        contentAlignment = Alignment.Center,
    ) {
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
                onLongClick = {},
            )
            app != null -> AppCell(
                app = app,
                badge = if (s.showBadges) env.badges[app.packageName]?.size ?: 0 else 0,
                iconSizeDp = iconDp,
                showLabel = false,
                labelSizeSp = s.labelSizeSp,
                onWallpaper = false,
                onClick = { env.onLaunch(app) },
                onLongClick = {},
                iconAlpha = s.dockIconAlpha,
                onDoubleClick = entry?.let { env.dockDtap[it] }?.takeIf { it.isNotEmpty() && !env.editing }?.let { code -> { env.onDockDtap(code) } },
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

/**
 * メディア操作バー。文字色を明示し、背景の不透明度は設定(個別設定)で変えられる。
 * 大きさに応じて表示を切り替える: 極小=再生/停止のみ、小=3ボタン、通常=曲名+3ボタン(高さがあればアーティスト名も)。
 * overlay=true なら、大きな3つのボタンを透かして背景に置き、曲名をその上に重ねて表示する(ボタンと文字は別の色)。
 * buttonSp はボタンの大きさ(0=自動)。
 */
@Composable
private fun MediaBar(
    state: MediaHelper.State,
    media: MediaHelper,
    bgAlpha: Float,
    buttonSp: Int,
    overlay: Boolean,
    showSource: Boolean,
    onRequestPermission: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sourceText = if (showSource && state.permitted && state.hasSession) {
        listOfNotNull(state.appName?.takeIf { it.isNotBlank() }, state.output?.takeIf { it.isNotBlank() }).joinToString(" · ")
    } else ""

    val onSurface = cs.onSurface
    val shape = RoundedCornerShape(20.dp)
    val titleText = if (!state.permitted) "曲名を表示するには通知アクセスを許可"
    else state.title ?: if (state.hasSession) "再生中のメディア" else "メディアなし"
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(shape)
            .background(cs.surfaceContainerHigh.copy(alpha = bgAlpha))
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        val h = maxHeight
        val showAll = w >= 110.dp
        val btnSize = if (buttonSp > 0) buttonSp.sp else (h.value * 0.45f).coerceIn(16f, 26f).sp

        if (overlay) {
            // 文字色(onSurface)と輝度が離れた色をボタンに選び、文字と重なっても読めるようにする
            val ink = onSurface.luminance()
            val btnColor = listOf(cs.primary, cs.tertiary, cs.secondary, cs.inversePrimary)
                .firstOrNull { abs(it.luminance() - ink) >= 0.35f }
                ?: (if (ink > 0.5f) Color.Black else Color.White)
            val big = if (buttonSp > 0) (buttonSp * 1.8f).sp else (h.value * 0.75f).coerceIn(28f, 56f).sp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val items = if (showAll) listOf(MediaGlyphKind.PREV, if (state.playing) MediaGlyphKind.PAUSE else MediaGlyphKind.PLAY, MediaGlyphKind.NEXT)
                else listOf(if (state.playing) MediaGlyphKind.PAUSE else MediaGlyphKind.PLAY)
                items.forEachIndexed { i, kind ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                when {
                                    !showAll || i == 1 -> media.playPause()
                                    i == 0 -> media.previous()
                                    else -> media.next()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { MediaGlyph(kind, btnColor.copy(alpha = 0.32f), big.value.dp) }
                }
            }
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val halo = TextStyle(fontFamily = LocalAppFont.current, shadow = Shadow(cs.surface, blurRadius = 10f))
                Text(
                    titleText,
                    color = onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = halo,
                    modifier = if (!state.permitted) Modifier.clickable(onClick = onRequestPermission) else Modifier,
                )
                if (h >= 56.dp && state.permitted) {
                    state.artist?.let {
                        Text(it, color = onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = halo)
                    }
                }
                if (h >= 72.dp && sourceText.isNotEmpty()) {
                    Text(sourceText, color = onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = halo)
                }
            }
            return@BoxWithConstraints
        }

        val showText = w >= 190.dp
        val showArtist = h >= 56.dp
        val colors = ButtonDefaults.textButtonColors(contentColor = onSurface)
        val pad = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        Row(
            Modifier.fillMaxSize().padding(start = if (showText) 14.dp else 0.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showText) Arrangement.Start else Arrangement.Center,
        ) {
            if (showText) {
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                    if (!state.permitted) {
                        Text(
                            titleText,
                            color = onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(onClick = onRequestPermission),
                        )
                    } else {
                        Text(
                            titleText,
                            color = onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showArtist) {
                            state.artist?.let {
                                Text(
                                    it,
                                    color = cs.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (h >= 64.dp && sourceText.isNotEmpty()) {
                            Text(
                                sourceText,
                                color = cs.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (showAll) {
                TextButton(onClick = { media.previous() }, colors = colors, contentPadding = pad) { MediaGlyph(MediaGlyphKind.PREV, onSurface, btnSize.value.dp) }
            }
            TextButton(onClick = { media.playPause() }, colors = colors, contentPadding = pad) {
                MediaGlyph(if (state.playing) MediaGlyphKind.PAUSE else MediaGlyphKind.PLAY, onSurface, btnSize.value.dp)
            }
            if (showAll) {
                TextButton(onClick = { media.next() }, colors = colors, contentPadding = pad) { MediaGlyph(MediaGlyphKind.NEXT, onSurface, btnSize.value.dp) }
            }
        }
    }
}

/** 検索バーをタップしたときの全画面検索。アプリ名の部分一致とWeb検索。 */
@Composable
private fun SearchOverlay(
    apps: List<AppInfo>,
    yomi: Map<String, String>,
    searchUrl: String,
    history: List<String>,
    onRecord: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    fun web(q: String) {
        if (q.isBlank()) return
        onRecord("q:${q.trim()}")
        openWebSearch(context, searchUrl, q)
    }
    val focus = remember { FocusRequester() }
    val matches = remember(apps, query, yomi) {
        AppSorter.sort(AppSorter.filter(apps, query, yomi), SortMode.NAME, emptyMap()).take(50)
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
                    keyboardActions = KeyboardActions(onSearch = { web(query) }),
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
                if (query.isBlank() && history.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("検索履歴", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = onClearHistory) { Text("消去") }
                        }
                    }
                    items(history, key = { "h$it" }) { h ->
                        val app = if (h.startsWith("app:")) apps.firstOrNull { it.key == h.removePrefix("app:") } else null
                        if (h.startsWith("q:")) {
                            val word = h.removePrefix("q:")
                            Row(
                                Modifier.fillMaxWidth().clickable { web(word) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                Text(word, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else if (app != null) {
                            Row(
                                Modifier.fillMaxWidth().clickable { onLaunch(app) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app, 32)
                                Text(app.label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (query.isNotBlank()) {
                    item {
                        Text(
                            "「${query.trim()}」をWebで検索",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { web(query) }
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
    val hasAlpha = item.type != HomeItem.ICONS
    val globalAlpha = when (item.type) {
        HomeItem.WIDGET, HomeItem.STACK -> settings.widgetGlobalAlpha
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

/** ダブルタップで開く対象("app:pkg/cls" か "sc:pkg|id")を起動する。 */
private fun launchDtap(context: android.content.Context, vm: LauncherViewModel, appMap: Map<String, AppInfo>, code: String) {
    when {
        code.startsWith("app:") -> {
            val app = appMap[code.removePrefix("app:")]
            if (app != null) vm.launch(context, app) else toast(context, "アプリが見つかりません")
        }
        code.startsWith("sc:") -> {
            val pkg = code.removePrefix("sc:").substringBefore('|')
            val id = code.substringAfter('|')
            if (!ShortcutHelper.launch(context, pkg, id)) toast(context, "ショートカットを開けません")
        }
    }
}

/** 画面の更新: アプリ一覧・ウィジェット・メディア・通知を取り直す。 */
private fun refreshAll(vm: LauncherViewModel, widgets: WidgetController, media: MediaHelper) {
    vm.repo.refresh()
    widgets.stop()
    widgets.start()
    media.start()
    BadgeStore.refresh()
}

/** アプリの通知の内容。タップでその通知を開き、「消す」で通知を消去する。 */
@Composable
private fun NotificationsDialog(pkg: String, appLabel: String, notifs: List<BadgeStore.Notif>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$appLabel の通知") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        text = {
            if (notifs.isEmpty()) {
                Text("通知はありません。")
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(notifs, key = { it.key }) { n ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching { n.intent?.send() }
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                if (n.title.isNotEmpty()) {
                                    Text(n.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (n.text.isNotEmpty()) {
                                    Text(n.text, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (n.clearable) {
                                TextButton(onClick = { BadgeStore.dismiss(n.key) }) { Text("消す") }
                            }
                        }
                    }
                }
            }
        },
    )
}
