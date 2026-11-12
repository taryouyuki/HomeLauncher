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
import jp.achi.homelauncher.data.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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
    val pendingRebind = MutableStateFlow(prefs.loadPending())

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
    }

    // ---- ホーム配置 ----
    /** 最初に見つかった空きに配置する。空きが無ければ false。 */
    private fun placeItem(sx: Int, sy: Int, build: (page: Int, x: Int, y: Int) -> HomeItem): Boolean {
        val s = settings.value
        val cell = GridLogic.findFreeCell(items.value, s.pageCount, s.gridCols, s.gridRows, sx, sy) ?: return false
        items.update { it + build(cell.first, cell.second, cell.third) }
        persistItems()
        return true
    }

    fun addAppAuto(app: AppInfo): Boolean = addAppByKey(app.key)

    fun addAppByKey(key: String): Boolean {
        if (items.value.any { it.type == HomeItem.APP && it.appKey == key }) return true
        return placeItem(1, 1) { p, x, y ->
            HomeItem(type = HomeItem.APP, page = p, x = x, y = y, pkg = key.substringBefore('/'), cls = key.substringAfter('/'))
        }
    }

    /** 検索バー / メディアバーを(無ければ)1行ぶん追加する。 */
    fun addSpecial(type: String): Boolean {
        if (items.value.any { it.type == type }) return true
        val cols = settings.value.gridCols
        return placeItem(cols, 1) { p, x, y -> HomeItem(type = type, page = p, x = x, y = y, spanX = cols, spanY = 1) }
    }

    fun createStack(): Boolean {
        val s = settings.value
        val sx = minOf(4, s.gridCols)
        val sy = minOf(2, s.gridRows)
        return placeItem(sx, sy) { p, x, y -> HomeItem(type = HomeItem.STACK, page = p, x = x, y = y, spanX = sx, spanY = sy) }
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
    /** dockIndex が null ならホーム画面の空きに、指定があればドックのその位置に作る。 */
    fun createFolder(name: String, dockIndex: Int?): Folder? {
        val folder = Folder(name = name.trim().ifEmpty { "フォルダ" })
        if (dockIndex != null) {
            folders.update { it + folder }
            persistFolders()
            setDockEntry(dockIndex, DockEntry.folder(folder.id))
            return folder
        }
        val placed = placeItem(1, 1) { p, x, y ->
            HomeItem(type = HomeItem.FOLDER, page = p, x = x, y = y, ref = folder.id)
        }
        if (!placed) return null
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
        BackupCodec.encode(settings.value, items.value, dock.value, counts.value, folders.value)

    /** 成功時は、不要になった旧ウィジェットIDの一覧(呼び出し側で解放する)を返す。 */
    fun importBackup(text: String): Result<List<Int>> = runCatching {
        val backup = BackupCodec.decode(text)
        val oldWidgetIds = items.value.filter { it.type == HomeItem.WIDGET && it.widgetId >= 0 }.map { it.widgetId }

        settings.value = backup.settings
        items.value = backup.items.filter { it.type != HomeItem.WIDGET }
        folders.value = backup.folders
        dock.value = backup.dock
        counts.value = backup.counts
        pendingRebind.value = backup.items.filter { it.type == HomeItem.WIDGET }

        prefs.saveSettings(settings.value)
        persistItems()
        persistFolders()
        prefs.saveDock(dock.value)
        prefs.saveCounts(counts.value)
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
