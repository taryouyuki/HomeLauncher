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

    fun loadLastLaunch(): Map<String, Long> = runCatching {
        val o = JSONObject(sp.getString(KEY_LAST_LAUNCH, "{}"))
        buildMap<String, Long> {
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k, o.optLong(k, 0L))
            }
        }
    }.getOrDefault(emptyMap())

    fun saveLastLaunch(map: Map<String, Long>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString(KEY_LAST_LAUNCH, o.toString()).apply()
    }

    fun loadFolders(): List<Folder> =
        runCatching { Folder.listFromJson(JSONArray(sp.getString(KEY_FOLDERS, "[]"))) }
            .getOrDefault(emptyList())

    fun saveFolders(list: List<Folder>) {
        sp.edit().putString(KEY_FOLDERS, Folder.listToJson(list).toString()).apply()
    }

    fun loadStringMap(key: String): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString(key, "{}"))
        buildMap<String, String> {
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                o.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) }
            }
        }
    }.getOrDefault(emptyMap())

    fun saveStringMap(key: String, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString(key, o.toString()).apply()
    }

    /** 検索履歴。"q:ことば" または "app:pkg/cls"。新しい順。 */
    fun loadHistory(): List<String> =
        runCatching { LayoutJson.stringsFromJson(JSONArray(sp.getString(KEY_HISTORY, "[]"))) }.getOrDefault(emptyList())

    fun saveHistory(list: List<String>) {
        sp.edit().putString(KEY_HISTORY, LayoutJson.stringsToJson(list).toString()).apply()
    }

    /** 読み仮名(アプリ名検索用)。key = "pkg/cls" */
    fun loadYomi(): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString(KEY_YOMI, "{}"))
        buildMap<String, String> {
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                o.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) }
            }
        }
    }.getOrDefault(emptyMap())

    fun saveYomi(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString(KEY_YOMI, o.toString()).apply()
    }

    /** 「ホーム画面に追加」の要求(PinShortcutActivity が積み、MainActivity が配置する)。 "pkg\tid\tlabel" */
    @Synchronized
    fun pushPin(pkg: String, id: String, label: String) {
        val arr = runCatching { JSONArray(sp.getString(KEY_PINS, "[]")) }.getOrDefault(JSONArray())
        arr.put(listOf(pkg, id, label).joinToString("\t"))
        sp.edit().putString(KEY_PINS, arr.toString()).commit()
    }

    @Synchronized
    fun popPins(): List<Triple<String, String, String>> {
        val arr = runCatching { JSONArray(sp.getString(KEY_PINS, "[]")) }.getOrDefault(JSONArray())
        sp.edit().remove(KEY_PINS).commit()
        return buildList {
            for (i in 0 until arr.length()) {
                val p = arr.optString(i).split("\t")
                if (p.size >= 2) add(Triple(p[0], p[1], p.getOrElse(2) { "" }))
            }
        }
    }

    /** 検索バー/メディアバーをグリッド項目にした新レイアウトへの移行が済んだか。 */
    var layoutV2Done: Boolean
        get() = sp.getBoolean(KEY_LAYOUT_V2, false)
        set(v) {
            sp.edit().putBoolean(KEY_LAYOUT_V2, v).apply()
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

    companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_ITEMS = "items"
        const val KEY_PENDING = "pending_widgets"
        const val KEY_DOCK = "dock"
        const val KEY_COUNTS = "launch_counts"
        const val KEY_FOLDERS = "folders"
        const val KEY_LAST_LAUNCH = "last_launch"
        const val KEY_YOMI = "yomi"
        const val KEY_HISTORY = "search_history"
        const val KEY_DOCK_DTAP = "dock_dtap"
        const val KEY_CATEGORIES = "categories"
        const val KEY_PINS = "pending_pins"
        const val KEY_LAYOUT_V2 = "layout_v2_done"
    }
}
