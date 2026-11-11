package jp.achi.homelauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences への永続化。壊れたデータは既定値にフォールバックしてクラッシュを避ける。 */
class LauncherPrefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun loadSettings(): LauncherSettings =
        runCatching { sp.getString(KEY_SETTINGS, null)?.let { LauncherSettings.fromJson(JSONObject(it)) } }
            .getOrNull() ?: LauncherSettings()

    fun saveSettings(s: LauncherSettings) {
        sp.edit().putString(KEY_SETTINGS, s.toJson().toString()).apply()
    }

    fun loadItems(): List<HomeItem> = loadItemList(KEY_ITEMS)
    fun saveItems(items: List<HomeItem>) = saveItemList(KEY_ITEMS, items)

    /** バックアップ復元後、再バインド待ちのウィジェット。 */
    fun loadPending(): List<HomeItem> = loadItemList(KEY_PENDING)
    fun savePending(items: List<HomeItem>) = saveItemList(KEY_PENDING, items)

    fun loadDock(): List<String> =
        runCatching { LayoutJson.stringsFromJson(JSONArray(sp.getString(KEY_DOCK, "[]"))) }
            .getOrDefault(emptyList())

    fun saveDock(dock: List<String>) {
        sp.edit().putString(KEY_DOCK, LayoutJson.stringsToJson(dock).toString()).apply()
    }

    fun loadCounts(): Map<String, Int> =
        runCatching { LayoutJson.countsFromJson(JSONObject(sp.getString(KEY_COUNTS, "{}"))) }
            .getOrDefault(emptyMap())

    fun saveCounts(counts: Map<String, Int>) {
        sp.edit().putString(KEY_COUNTS, LayoutJson.countsToJson(counts).toString()).apply()
    }

    private fun loadItemList(key: String): List<HomeItem> =
        runCatching { LayoutJson.itemsFromJson(JSONArray(sp.getString(key, "[]"))) }
            .getOrDefault(emptyList())

    private fun saveItemList(key: String, items: List<HomeItem>) {
        sp.edit().putString(key, LayoutJson.itemsToJson(items).toString()).apply()
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_ITEMS = "items"
        const val KEY_PENDING = "pending_widgets"
        const val KEY_DOCK = "dock"
        const val KEY_COUNTS = "launch_counts"
    }
}
