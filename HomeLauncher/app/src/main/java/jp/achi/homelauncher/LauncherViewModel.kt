package jp.achi.homelauncher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppRepository
import jp.achi.homelauncher.data.BackupCodec
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
    val dock = MutableStateFlow(prefs.loadDock())
    val counts = MutableStateFlow(prefs.loadCounts())
    val pendingRebind = MutableStateFlow(prefs.loadPending())

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
    fun addApp(page: Int, x: Int, y: Int, app: AppInfo) {
        items.update { it + HomeItem(type = HomeItem.APP, page = page, x = x, y = y, pkg = app.packageName, cls = app.className) }
        persistItems()
    }

    /** 最初の空きセルに置く。空きが無ければ false。 */
    fun addAppAuto(app: AppInfo): Boolean {
        val s = settings.value
        val cell = GridLogic.findFreeCell(items.value, s.pageCount, s.gridCols, s.gridRows) ?: return false
        addApp(cell.first, cell.second, cell.third, app)
        return true
    }

    fun addWidget(item: HomeItem) {
        items.update { it + item }
        persistItems()
    }

    fun removeItem(id: String): HomeItem? {
        val target = items.value.firstOrNull { it.id == id } ?: return null
        items.update { list -> list.filterNot { it.id == id } }
        persistItems()
        return target
    }

    fun updateItem(id: String, transform: (HomeItem) -> HomeItem) {
        items.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persistItems()
    }

    // ---- ドック ----
    fun setDockApp(index: Int, key: String) {
        dock.update { list ->
            val m = list.toMutableList()
            m.remove(key)
            m.add(index.coerceAtMost(m.size), key)
            m
        }
        prefs.saveDock(dock.value)
    }

    fun removeDockApp(key: String) {
        dock.update { it - key }
        prefs.saveDock(dock.value)
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
        BackupCodec.encode(settings.value, items.value, dock.value, counts.value)

    /** 成功時は、不要になった旧ウィジェットIDの一覧(呼び出し側で解放する)を返す。 */
    fun importBackup(text: String): Result<List<Int>> = runCatching {
        val backup = BackupCodec.decode(text)
        val oldWidgetIds = items.value.filter { it.type == HomeItem.WIDGET && it.widgetId >= 0 }.map { it.widgetId }

        settings.value = backup.settings
        items.value = backup.items.filter { it.type == HomeItem.APP }
        dock.value = backup.dock
        counts.value = backup.counts
        pendingRebind.value = backup.items.filter { it.type == HomeItem.WIDGET }

        prefs.saveSettings(settings.value)
        persistItems()
        prefs.saveDock(dock.value)
        prefs.saveCounts(counts.value)
        prefs.savePending(pendingRebind.value)
        oldWidgetIds
    }

    private fun persistItems() = prefs.saveItems(items.value)

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }
}
