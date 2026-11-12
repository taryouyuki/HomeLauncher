package jp.achi.homelauncher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppRepository
import jp.achi.homelauncher.data.BackupCodec
import jp.achi.homelauncher.data.DockEntry
import jp.achi.homelauncher.data.Folder
import jp.achi.homelauncher.data.GridLogic
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
    val settings = MutableStateFlow(prefs.loadSettings())
    val items = MutableStateFlow(prefs.loadItems())
    val folders = MutableStateFlow(prefs.loadFolders())
    /** ドックの項目。アプリなら "pkg/cls"、フォルダなら DockEntry.folder(id)。 */
    val dock = MutableStateFlow(prefs.loadDock())
    val counts = MutableStateFlow(prefs.loadCounts())
    val lastLaunch = MutableStateFlow(prefs.loadLastLaunch())
    val pendingRebind = MutableStateFlow(prefs.loadPending())

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
        val (list, collided) = GridLogic.placeWithPolicy(items.value, fixed, s.overlapPolicy, s.gridRows)
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
            if (index in m.indices) m[index] = entry else m.add(entry)
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
        val item = items.value.firstOrNull { it.id == itemId && it.type == HomeItem.APP } ?: return
        removeItem(itemId)
        addToFolder(folderId, item.appKey)
    }

    /**
     * アイコンを別のアイコン/フォルダの上にドロップしたときの処理。
     * アプリ+アプリなら新しいフォルダを作り、アプリ+フォルダなら中に入れる。
     */
    fun mergeIntoFolder(draggedId: String, targetId: String): Boolean {
        val dragged = items.value.firstOrNull { it.id == draggedId && it.type == HomeItem.APP } ?: return false
        val target = items.value.firstOrNull { it.id == targetId } ?: return false
        if (target.id == dragged.id) return false
        return when (target.type) {
            HomeItem.APP -> {
                val folder = Folder(name = "フォルダ", apps = listOf(target.appKey, dragged.appKey))
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
                addToFolder(target.ref, dragged.appKey)
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
        BackupCodec.encode(settings.value, items.value, dock.value, counts.value, folders.value, lastLaunch.value)

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
        pendingRebind.value = backup.items.filter { it.type == HomeItem.WIDGET }

        prefs.saveSettings(settings.value)
        persistItems()
        persistFolders()
        prefs.saveDock(dock.value)
        prefs.saveCounts(counts.value)
        prefs.saveLastLaunch(lastLaunch.value)
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
