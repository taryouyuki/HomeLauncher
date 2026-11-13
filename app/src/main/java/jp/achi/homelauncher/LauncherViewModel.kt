package jp.achi.homelauncher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppRepository
import jp.achi.homelauncher.data.AutoLayout
import jp.achi.homelauncher.data.BackupCodec
import jp.achi.homelauncher.data.DockEntry
import jp.achi.homelauncher.data.Folder
import jp.achi.homelauncher.data.GridLogic
import jp.achi.homelauncher.data.folderEntry
import jp.achi.homelauncher.data.HomeItem
import jp.achi.homelauncher.data.LauncherPrefs
import jp.achi.homelauncher.data.LauncherSettings
import jp.achi.homelauncher.data.PlaceResult
import jp.achi.homelauncher.data.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** 配置先の指定(ページ, 列, 行)。null なら最初の空きに置く。 */
typealias CellAt = Triple<Int, Int, Int>

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = LauncherPrefs(app)
    val repo = AppRepository(app)

    val apps = repo.apps
    val workApps = repo.workApps
    val settings = MutableStateFlow(prefs.loadSettings())
    val items = MutableStateFlow(prefs.loadItems())
    val folders = MutableStateFlow(prefs.loadFolders())
    /** ドックの項目。アプリなら "pkg/cls"、フォルダなら DockEntry.folder(id)。 */
    val dock = MutableStateFlow(prefs.loadDock())
    val counts = MutableStateFlow(prefs.loadCounts())
    val lastLaunch = MutableStateFlow(prefs.loadLastLaunch())
    val pendingRebind = MutableStateFlow(prefs.loadPending())
    /** 読み仮名(アプリ名検索用)。key = "pkg/cls" */
    val yomi = MutableStateFlow(prefs.loadYomi())
    /** ドックのアイコンのダブルタップ設定。key = ドックの項目、value = "app:pkg/cls" か "sc:pkg|id" */
    val dockDtap = MutableStateFlow(prefs.loadStringMap(LauncherPrefs.KEY_DOCK_DTAP))
    val searchHistory = MutableStateFlow(prefs.loadHistory())
    /** 手動で決めたアプリのカテゴリー。key = アプリのkey */
    val categories = MutableStateFlow(prefs.loadStringMap(LauncherPrefs.KEY_CATEGORIES))

    /** 直近の配置結果(フォルダ作成のように戻り値で伝えにくい操作用)。 */
    var lastPlace: PlaceResult = PlaceResult.PLACED
        private set

    init {
        // 旧バージョンからの移行: 検索バーとメディアバーをグリッド上の項目として追加する
        if (!prefs.layoutV2Done) {
            val s = settings.value
            var list = items.value
            for (type in listOf(HomeItem.SEARCH, HomeItem.MEDIA)) {
                if (list.none { it.type == type }) {
                    val cell = GridLogic.findFreeCell(list, s.pageCount, s.gridCols, s.gridRows, s.gridCols, 1)
                    if (cell != null) {
                        list = list + HomeItem(
                            type = type, page = cell.first, x = cell.second, y = cell.third,
                            spanX = s.gridCols, spanY = 1,
                        )
                    }
                }
            }
            items.value = list
            prefs.saveItems(list)
            prefs.layoutV2Done = true
        }
    }

    // ---- 設定 ----
    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        settings.update(transform)
        prefs.saveSettings(settings.value)
    }

    fun setYomi(appKey: String, reading: String) {
        yomi.update { if (reading.isBlank()) it - appKey else it + (appKey to reading.trim()) }
        prefs.saveYomi(yomi.value)
    }

    /** アプリ一覧から隠すアプリ(ホームには置ける)。 */
    val hidden = MutableStateFlow(prefs.loadStringMap("hidden_apps").keys)

    fun setHidden(appKey: String, hide: Boolean) {
        hidden.update { if (hide) it + appKey else it - appKey }
        prefs.saveStringMap("hidden_apps", hidden.value.associateWith { "1" })
    }

    fun setCategory(appKey: String, name: String) {
        categories.update { if (name.isBlank()) it - appKey else it + (appKey to name.trim()) }
        prefs.saveStringMap(LauncherPrefs.KEY_CATEGORIES, categories.value)
    }

    fun arrangeIcons() {
        beginAdjust()
        val s = settings.value
        items.update { AutoLayout.arrangeIcons(it, s.gridCols, s.gridRows, s.pageCount) }
        persistItems()
    }

    fun arrangeWidgets() {
        beginAdjust()
        val s = settings.value
        items.update { AutoLayout.arrangeWidgets(it, s.gridCols, s.gridRows, s.pageCount) }
        persistItems()
    }

    // ---- 自動調整の「調整前 / 調整後」切替 ----
    data class AdjustSnap(
        val beforeItems: List<HomeItem>,
        val beforeSettings: LauncherSettings,
        val afterItems: List<HomeItem>? = null,
        val afterSettings: LauncherSettings? = null,
        val showingBefore: Boolean = false,
    )

    val adjust = MutableStateFlow<AdjustSnap?>(null)

    private fun beginAdjust() {
        // 連続して調整しても、最初の状態に戻せるようにする
        if (adjust.value == null || adjust.value?.showingBefore == true) {
            adjust.value = AdjustSnap(items.value, settings.value)
        }
    }

    fun adjustShowBefore() {
        val snap = adjust.value ?: return
        if (snap.showingBefore) return
        adjust.value = snap.copy(afterItems = items.value, afterSettings = settings.value, showingBefore = true)
        items.value = snap.beforeItems
        persistItems()
        updateSettings { snap.beforeSettings }
    }

    fun adjustShowAfter() {
        val snap = adjust.value ?: return
        val ai = snap.afterItems ?: return
        val asx = snap.afterSettings ?: return
        adjust.value = snap.copy(showingBefore = false)
        items.value = ai
        persistItems()
        updateSettings { asx }
    }

    fun adjustKeep() {
        adjust.value = null
    }

    /** おまかせ微調整(大きく変えず、ばらつきだけそろえる)。 */
    fun harmonize(icons: Boolean, widgets: Boolean) {
        beginAdjust()
        val s = settings.value
        items.update { AutoLayout.harmonize(it, s.gridCols, s.gridRows, s.pageCount, icons, widgets) }
        persistItems()
    }

    /** スタック / アイコンセットを解除して、中身を個別にホームへ出す。 */
    fun dissolve(containerId: String) {
        val c = items.value.firstOrNull { it.id == containerId && (it.type == HomeItem.STACK || it.type == HomeItem.ICONS) } ?: return
        val s = settings.value
        var base = items.value.filter { it.id != c.id && it.parent != c.id }
        for (kid in items.value.filter { it.parent == c.id }) {
            val icon = kid.type == HomeItem.APP || kid.type == HomeItem.SHORTCUT || kid.type == HomeItem.FOLDER
            val sx = if (icon) 1 else c.spanX
            val sy = if (icon) 1 else c.spanY
            val cell = GridLogic.findFreeCell(base, s.pageCount, s.gridCols, s.gridRows, sx, sy)
            val (pg, x, y) = cell ?: Triple(c.page, c.x, c.y)
            base = base + kid.copy(parent = "", page = pg, x = x, y = y, spanX = sx, spanY = sy, free = false)
        }
        items.value = base
        persistItems()
    }

    /** アイコンセット(既定 3×3)を置く。 */
    fun createIconSet(at: CellAt? = null): PlaceResult {
        val s = settings.value
        val sx = minOf(3, s.gridCols)
        val sy = minOf(3, s.gridRows)
        return place(sx, sy, at) { p, x, y -> HomeItem(type = HomeItem.ICONS, page = p, x = x, y = y, spanX = sx, spanY = sy) }
    }

    /** アイコンセットの末尾にアプリを追加する(並びは子の順。枠の大きさごとに自動でセットに分かれる)。 */
    /** アイコンセットの中に新しいフォルダを作る。 */
    fun createFolderInSet(setId: String): Boolean {
        if (items.value.none { it.id == setId && it.type == HomeItem.ICONS }) return false
        val folder = Folder(name = "フォルダ")
        folders.update { it + folder }
        persistFolders()
        items.update { it + HomeItem(type = HomeItem.FOLDER, page = 0, x = 0, y = 0, ref = folder.id, parent = setId) }
        persistItems()
        return true
    }

    /** 「アプリ一覧」「クイックパネル」を開くアイコンをホームに置く。 */
    fun createAction(kind: String, at: CellAt? = null): PlaceResult =
        place(1, 1, at) { p, x, y -> HomeItem(type = HomeItem.ACTION, page = p, x = x, y = y, ref = kind) }

    fun addToIconSet(setId: String, packageName: String, className: String) {
        if (items.value.none { it.id == setId && it.type == HomeItem.ICONS }) return
        items.update { it + HomeItem(type = HomeItem.APP, page = 0, x = 0, y = 0, pkg = packageName, cls = className, parent = setId) }
        persistItems()
    }

    /** ホームに置いてあるアプリを、アイコンセットへ移す(ドラッグで重ねたとき)。 */
    fun moveHomeAppToIconSet(draggedId: String, setId: String): Boolean {
        val dragged = items.value.firstOrNull {
            it.id == draggedId && it.parent.isEmpty() &&
                (it.type == HomeItem.APP || it.type == HomeItem.SHORTCUT || it.type == HomeItem.FOLDER)
        } ?: return false
        if (items.value.none { it.id == setId && it.type == HomeItem.ICONS }) return false
        items.update { list -> list.filter { it.id != draggedId } + dragged.copy(parent = setId, page = 0, x = 0, y = 0, free = false) }
        persistItems()
        return true
    }

    fun setDockDtap(entry: String, code: String) {
        dockDtap.update { if (code.isEmpty()) it - entry else it + (entry to code) }
        prefs.saveStringMap(LauncherPrefs.KEY_DOCK_DTAP, dockDtap.value)
    }

    /** 検索履歴に追加(新しい順・重複なし)。件数0なら記録しない。 */
    fun addHistory(entry: String) {
        if (settings.value.searchHistoryCount <= 0) return
        searchHistory.update { (listOf(entry) + (it - entry)).take(20) }
        prefs.saveHistory(searchHistory.value)
    }

    fun clearHistory() {
        searchHistory.value = emptyList()
        prefs.saveHistory(emptyList())
    }

    fun applyTheme(p: jp.achi.homelauncher.data.ThemePreset) =
        updateSettings { jp.achi.homelauncher.data.ThemePresets.apply(it, p) }

    /** 設置済みのアイコン(アプリ・ショートカット・フォルダ)を指定サイズに統一する。大きさを変えていたものは1マスに戻す。 */
    fun unifyIconSize(dp: Int) {
        updateSettings { it.copy(iconSizeDp = dp.coerceIn(32, 96), dockIconSizeDp = dp.coerceIn(24, 120)) }
        val s = settings.value
        items.update { list ->
            list.map { i ->
                if (i.parent.isEmpty() && (i.type == HomeItem.APP || i.type == HomeItem.SHORTCUT || i.type == HomeItem.FOLDER)) {
                    GridLogic.clamp(i.copy(spanX = 1, spanY = 1, fw = 1f, fh = 1f), s.gridCols, s.gridRows)
                } else i
            }
        }
        persistItems()
    }

    fun setDrawerSort(mode: SortMode) = updateSettings { it.copy(drawerSort = mode) }

    // ---- 起動 ----
    fun launch(context: Context, app: AppInfo) {
        if (!repo.launch(context, app)) {
            repo.refresh()
            return
        }
        counts.update { it + (app.key to ((it[app.key] ?: 0) + 1)) }
        prefs.saveCounts(counts.value)
        lastLaunch.update { it + (app.key to System.currentTimeMillis()) }
        prefs.saveLastLaunch(lastLaunch.value)
    }

    // ---- ホーム配置 ----
    /** 重なりが起きたときに画面へ出すお知らせ文。 */
    fun overlapMessage(): String = when (settings.value.overlapPolicy) {
        LauncherSettings.PUSH_DOWN -> "既にあるアイテムを下にずらして配置しました"
        LauncherSettings.PUSH_UP -> "既にあるアイテムを上にずらして配置しました"
        LauncherSettings.SHIFT -> "既にあるアイテムを順番に押し出して配置しました"
        else -> "既にあるアイテムの上に重ねて配置しました"
    }

    /**
     * 項目を(既存なら置き換えて)配置し、重なりを設定の動作で処理する。
     * 戻り値は「既存の項目と重なったか」。移動・リサイズ・指定位置への追加すべてで使う。
     */
    fun placeAndResolve(item: HomeItem): Boolean {
        val s = settings.value
        val fixed = GridLogic.clamp(item, s.gridCols, s.gridRows)
            .let { it.copy(page = it.page.coerceIn(0, s.pageCount - 1)) }
        val (list, collided) = GridLogic.placeWithPolicy(items.value, fixed, s.overlapPolicy, s.gridRows, s.gridCols, s.pageCount)
        items.value = list
        persistItems()
        return collided
    }

    /**
     * at があればそのセルに、無ければ最初の空きに配置する。
     * 空きが無ければ FAILED。at 指定で既存項目と重なった場合は OVERLAPPED。
     */
    private fun place(sx: Int, sy: Int, at: CellAt?, build: (page: Int, x: Int, y: Int) -> HomeItem): PlaceResult {
        val s = settings.value
        val result = if (at != null) {
            val item = build(
                at.first.coerceIn(0, s.pageCount - 1),
                at.second.coerceIn(0, (s.gridCols - sx).coerceAtLeast(0)),
                at.third.coerceIn(0, (s.gridRows - sy).coerceAtLeast(0)),
            )
            if (placeAndResolve(item)) PlaceResult.OVERLAPPED else PlaceResult.PLACED
        } else {
            val cell = GridLogic.findFreeCell(items.value, s.pageCount, s.gridCols, s.gridRows, sx, sy)
            if (cell == null) {
                PlaceResult.FAILED
            } else {
                items.update { it + build(cell.first, cell.second, cell.third) }
                persistItems()
                PlaceResult.PLACED
            }
        }
        lastPlace = result
        return result
    }

    fun addAppAuto(app: AppInfo, at: CellAt? = null): PlaceResult = addAppByKey(app.key, at)

    fun addAppByKey(key: String, at: CellAt? = null): PlaceResult {
        if (items.value.any { it.type == HomeItem.APP && it.appKey == key }) return PlaceResult.PLACED
        return place(1, 1, at) { p, x, y ->
            HomeItem(type = HomeItem.APP, page = p, x = x, y = y, pkg = key.substringBefore('/'), cls = key.substringAfter('/'))
        }
    }

    /** ブラウザなどから「ホーム画面に追加」されたショートカットを置く。 */
    fun addShortcut(pkg: String, shortcutId: String, label: String, at: CellAt? = null): PlaceResult {
        if (items.value.any { it.type == HomeItem.SHORTCUT && it.pkg == pkg && it.cls == shortcutId }) {
            return PlaceResult.PLACED
        }
        return place(1, 1, at) { p, x, y ->
            HomeItem(type = HomeItem.SHORTCUT, page = p, x = x, y = y, pkg = pkg, cls = shortcutId, ref = label)
        }
    }

    /** 検索バー / メディアバーを(無ければ)1行ぶん追加する。 */
    fun addSpecial(type: String, at: CellAt? = null): PlaceResult {
        if (items.value.any { it.type == type }) return PlaceResult.PLACED
        val cols = settings.value.gridCols
        return place(cols, 1, at) { p, x, y -> HomeItem(type = type, page = p, x = x, y = y, spanX = cols, spanY = 1) }
    }

    fun createStack(at: CellAt? = null): PlaceResult {
        val s = settings.value
        val sx = minOf(4, s.gridCols)
        val sy = minOf(2, s.gridRows)
        return place(sx, sy, at) { p, x, y -> HomeItem(type = HomeItem.STACK, page = p, x = x, y = y, spanX = sx, spanY = sy) }
    }

    /** 自由配置の確定。マスに揃えず、他の項目とも重ねたまま置く(最前面)。 */
    fun placeFree(item: HomeItem) {
        val s = settings.value
        val fixed = GridLogic.clamp(item.copy(free = true, page = item.page.coerceIn(0, s.pageCount - 1)), s.gridCols, s.gridRows)
        items.update { list -> list.filter { it.id != fixed.id } + fixed }
        persistItems()
    }

    /** ドックの並び替え。from の項目を to の位置へ挿入する(間の項目は1つずつずれる)。 */
    fun moveDockEntry(from: Int, to: Int) {
        dock.update { list ->
            if (from !in list.indices) return@update list
            val m = list.toMutableList()
            val e = m.removeAt(from)
            m.add(to.coerceIn(0, m.size), e)
            m
        }
        prefs.saveDock(dock.value)
    }

    /** スタック内のウィジェットの並び替え。delta=-1 で前へ、+1 で後ろへ。 */
    fun moveStackChild(childId: String, delta: Int) {
        val list = items.value
        val child = list.firstOrNull { it.id == childId } ?: return
        val siblings = list.filter { it.parent == child.parent }
        val i = siblings.indexOfFirst { it.id == childId }
        val j = i + delta
        if (i < 0 || j !in siblings.indices) return
        val a = siblings[i]
        val b = siblings[j]
        items.value = list.map {
            when (it.id) {
                a.id -> b
                b.id -> a
                else -> it
            }
        }
        persistItems()
    }

    /** アイコン・文字・ドックの大きさだけを自動調整する(ウィジェットや配置は変えない)。 */
    fun autoAdjustIcons(cellWdp: Float, cellHdp: Float) {
        beginAdjust()
        updateSettings { AutoLayout.fitSettings(it, cellWdp, cellHdp) }
    }

    /**
     * ウィジェットだけを自動調整する。各ウィジェットを、提供元が示す最小サイズに合う大きさに直し、
     * はみ出し・重なりを整える(アイコンの大きさ等は変えない)。minSpan は (最小の幅マス, 高さマス) を返す。
     */
    fun autoAdjustWidgets(minSpan: (HomeItem) -> Pair<Int, Int>?) {
        beginAdjust()
        val s = settings.value
        items.update { list ->
            val resized = list.map { w ->
                if (w.parent.isNotEmpty() || (w.type != HomeItem.WIDGET && w.type != HomeItem.STACK)) w
                else {
                    val span = if (w.type == HomeItem.STACK) {
                        val kids = list.filter { it.parent == w.id }.mapNotNull(minSpan)
                        if (kids.isEmpty()) null else kids.maxOf { it.first } to kids.maxOf { it.second }
                    } else minSpan(w)
                    if (span == null) w
                    else w.copy(free = false, spanX = span.first.coerceIn(1, s.gridCols), spanY = span.second.coerceIn(1, s.gridRows))
                }
            }
            AutoLayout.tidy(resized, s.gridCols, s.gridRows, s.pageCount)
        }
        persistItems()
    }

    /** ウィジェットを、同じ場所・大きさのウィジェットスタックの1枚目に変換する。 */
    fun convertWidgetToStack(widgetItemId: String): Boolean {
        val w = items.value.firstOrNull { it.id == widgetItemId && it.type == HomeItem.WIDGET && it.parent.isEmpty() }
            ?: return false
        val stack = HomeItem(
            type = HomeItem.STACK, page = w.page, x = w.x, y = w.y, spanX = w.spanX, spanY = w.spanY,
            free = w.free, fx = w.fx, fy = w.fy, fw = w.fw, fh = w.fh,
        )
        items.update { list ->
            list.map { if (it.id == w.id) it.copy(parent = stack.id, page = 0, x = 0, y = 0, free = false) else it } + stack
        }
        persistItems()
        return true
    }

    /** 表示の自動調整: 大きさ(アイコン・文字・ドック)をセルに合わせ、はみ出し・重なり・自由配置を整える。 */
    fun autoAdjust(cellWdp: Float, cellHdp: Float) {
        beginAdjust()
        updateSettings { AutoLayout.fitSettings(it, cellWdp, cellHdp) }
        val s = settings.value
        items.update { AutoLayout.tidy(it, s.gridCols, s.gridRows, s.pageCount) }
        persistItems()
    }

    /** PinShortcutActivity が積んだ「ホーム画面に追加」を配置する。戻り値は (表示名, 結果, ページ) の一覧。 */
    fun consumePendingPins(): List<Triple<String, PlaceResult, Int>> =
        prefs.popPins().map { (pkg, id, label) ->
            val r = addShortcut(pkg, id, label)
            val page = items.value.firstOrNull { it.type == HomeItem.SHORTCUT && it.pkg == pkg && it.cls == id }?.page ?: 0
            Triple(label, r, page)
        }

    fun addWidget(item: HomeItem) {
        items.update { it + item }
        persistItems()
    }

    /** 項目を削除する。スタックなら子ウィジェットも含めて、削除した項目の一覧を返す。 */
    fun removeItem(id: String): List<HomeItem> {
        val removed = items.value.filter { it.id == id || it.parent == id }
        if (removed.isEmpty()) return emptyList()
        val ids = removed.map { it.id }.toSet()
        items.update { list -> list.filterNot { it.id in ids } }
        persistItems()
        return removed
    }

    fun updateItem(id: String, transform: (HomeItem) -> HomeItem) {
        items.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persistItems()
    }

    // ---- ドック(アプリまたはフォルダ。ページ数に上限なし) ----
    fun setDockEntry(index: Int, entry: String) {
        dock.update { list ->
            val m = list.toMutableList()
            m.remove(entry)
            // 指定位置に差し込む(もともとあったものは右へ1つずつずれる)
            m.add(index.coerceIn(0, m.size), entry)
            m
        }
        prefs.saveDock(dock.value)
    }

    fun removeDockEntry(entry: String) {
        dock.update { it - entry }
        prefs.saveDock(dock.value)
    }

    // ---- フォルダ ----
    /** dockIndex が null ならホーム画面(at または最初の空き)に、指定があればドックのその位置に作る。 */
    fun createFolder(name: String, dockIndex: Int?, at: CellAt? = null): Folder? {
        val folder = Folder(name = name.trim().ifEmpty { "フォルダ" })
        if (dockIndex != null) {
            folders.update { it + folder }
            persistFolders()
            setDockEntry(dockIndex, DockEntry.folder(folder.id))
            lastPlace = PlaceResult.PLACED
            return folder
        }
        val r = place(1, 1, at) { p, x, y ->
            HomeItem(type = HomeItem.FOLDER, page = p, x = x, y = y, ref = folder.id)
        }
        if (r == PlaceResult.FAILED) return null
        folders.update { it + folder }
        persistFolders()
        return folder
    }

    fun renameFolder(id: String, name: String) {
        val n = name.trim().ifEmpty { return }
        folders.update { list -> list.map { if (it.id == id) it.copy(name = n) else it } }
        persistFolders()
    }

    fun addToFolder(folderId: String, appKey: String) {
        folders.update { list ->
            list.map { if (it.id == folderId && appKey !in it.apps) it.copy(apps = it.apps + appKey) else it }
        }
        persistFolders()
    }

    fun removeFromFolder(folderId: String, appKey: String) {
        folders.update { list -> list.map { if (it.id == folderId) it.copy(apps = it.apps - appKey) else it } }
        persistFolders()
    }

    /** ホーム上のアプリアイコンをフォルダへ移す。 */
    fun moveHomeAppToFolder(itemId: String, folderId: String) {
        val item = items.value.firstOrNull { it.id == itemId && (it.type == HomeItem.APP || it.type == HomeItem.SHORTCUT) } ?: return
        removeItem(itemId)
        addToFolder(folderId, item.folderEntry)
    }

    /**
     * アイコンを別のアイコン/フォルダの上にドロップしたときの処理。
     * アプリ+アプリなら新しいフォルダを作り、アプリ+フォルダなら中に入れる。
     */
    fun mergeIntoFolder(draggedId: String, targetId: String): Boolean {
        val dragged = items.value.firstOrNull { it.id == draggedId && (it.type == HomeItem.APP || it.type == HomeItem.SHORTCUT) } ?: return false
        val target = items.value.firstOrNull { it.id == targetId } ?: return false
        if (target.id == dragged.id) return false
        return when (target.type) {
            HomeItem.APP, HomeItem.SHORTCUT -> {
                val folder = Folder(name = "フォルダ", apps = listOf(target.folderEntry, dragged.folderEntry))
                folders.update { it + folder }
                items.update { list ->
                    list.filterNot { it.id == draggedId || it.id == targetId } +
                        HomeItem(type = HomeItem.FOLDER, page = target.page, x = target.x, y = target.y, ref = folder.id)
                }
                persistFolders()
                persistItems()
                true
            }
            HomeItem.FOLDER -> {
                removeItem(draggedId)
                addToFolder(target.ref, dragged.folderEntry)
                true
            }
            else -> false
        }
    }

    /** フォルダを解散し、中のアプリはホームの空きに戻す(空きがなければ一覧からのみ起動できる)。 */
    fun deleteFolder(id: String) {
        val folder = folders.value.firstOrNull { it.id == id } ?: return
        items.update { list -> list.filterNot { it.type == HomeItem.FOLDER && it.ref == id } }
        dock.update { it - DockEntry.folder(id) }
        folders.update { list -> list.filterNot { it.id == id } }
        persistItems()
        prefs.saveDock(dock.value)
        persistFolders()
        folder.apps.forEach { addAppByKey(it) }
    }

    // ---- ウィジェット再バインド(バックアップ復元後) ----
    fun completeRebind(itemId: String, newWidgetId: Int) {
        val item = pendingRebind.value.firstOrNull { it.id == itemId } ?: return
        items.update { it + item.copy(widgetId = newWidgetId) }
        persistItems()
        dropPending(itemId)
    }

    fun dropPending(itemId: String) {
        pendingRebind.update { list -> list.filterNot { it.id == itemId } }
        prefs.savePending(pendingRebind.value)
    }

    // ---- バックアップ ----
    fun exportBackup(): String =
        BackupCodec.encode(settings.value, items.value, dock.value, counts.value, folders.value, lastLaunch.value, yomi.value)

    /** 成功時は、不要になった旧ウィジェットIDの一覧(呼び出し側で解放する)を返す。 */
    fun importBackup(text: String): Result<List<Int>> = runCatching {
        val backup = BackupCodec.decode(text)
        val oldWidgetIds = items.value.filter { it.type == HomeItem.WIDGET && it.widgetId >= 0 }.map { it.widgetId }

        settings.value = backup.settings
        items.value = backup.items.filter { it.type != HomeItem.WIDGET }
        folders.value = backup.folders
        dock.value = backup.dock
        counts.value = backup.counts
        lastLaunch.value = backup.lastLaunch
        yomi.value = backup.yomi
        pendingRebind.value = backup.items.filter { it.type == HomeItem.WIDGET }

        prefs.saveSettings(settings.value)
        persistItems()
        persistFolders()
        prefs.saveDock(dock.value)
        prefs.saveCounts(counts.value)
        prefs.saveLastLaunch(lastLaunch.value)
        prefs.saveYomi(yomi.value)
        prefs.savePending(pendingRebind.value)
        prefs.layoutV2Done = true
        oldWidgetIds
    }

    private fun persistItems() = prefs.saveItems(items.value)
    private fun persistFolders() = prefs.saveFolders(folders.value)

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }
}
