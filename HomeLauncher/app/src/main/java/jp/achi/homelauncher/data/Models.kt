package jp.achi.homelauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale
import java.util.UUID

/** ランチャーに表示するアプリ1件(起動アクティビティ単位)。 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val className: String,
    val updatedAt: Long,
) {
    val key: String get() = "$packageName/$className"
}

enum class SortMode { NAME, UPDATED, LAUNCH }

object AppSorter {
    /** 並び替え。名前順は日本語の読み順(Collator)、更新順は新しい順、起動順は回数の多い順。 */
    fun sort(apps: List<AppInfo>, mode: SortMode, counts: Map<String, Int>): List<AppInfo> {
        val collator = Collator.getInstance(Locale.JAPANESE)
        val byName = Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
            .thenBy { it.key }
        return when (mode) {
            SortMode.NAME -> apps.sortedWith(byName)
            SortMode.UPDATED -> apps.sortedWith(compareByDescending<AppInfo> { it.updatedAt }.then(byName))
            SortMode.LAUNCH -> apps.sortedWith(compareByDescending<AppInfo> { counts[it.key] ?: 0 }.then(byName))
        }
    }

    /** アプリ名の部分一致(大文字小文字は区別しない)。空文字なら全件。 */
    fun filter(apps: List<AppInfo>, query: String): List<AppInfo> {
        val q = query.trim()
        return if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
}

/** ホーム画面上に置かれたアプリショートカットまたはウィジェット。 */
data class HomeItem(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val page: Int,
    val x: Int,
    val y: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val pkg: String = "",
    val cls: String = "",
    val widgetId: Int = -1,
    val provider: String = "",
    /** ウィジェット個別の透明度。負の値なら全体設定を使う。 */
    val alpha: Float = -1f,
) {
    val appKey: String get() = "$pkg/$cls"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("page", page)
        put("x", x)
        put("y", y)
        put("spanX", spanX)
        put("spanY", spanY)
        put("pkg", pkg)
        put("cls", cls)
        put("widgetId", widgetId)
        put("provider", provider)
        put("alpha", alpha.toDouble())
    }

    companion object {
        const val APP = "app"
        const val WIDGET = "widget"

        fun fromJson(o: JSONObject): HomeItem {
            val type = o.getString("type")
            require(type == APP || type == WIDGET) { "unknown item type: $type" }
            return HomeItem(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                type = type,
                page = o.optInt("page", 0).coerceAtLeast(0),
                x = o.optInt("x", 0).coerceAtLeast(0),
                y = o.optInt("y", 0).coerceAtLeast(0),
                spanX = o.optInt("spanX", 1).coerceAtLeast(1),
                spanY = o.optInt("spanY", 1).coerceAtLeast(1),
                pkg = o.optString("pkg"),
                cls = o.optString("cls"),
                widgetId = o.optInt("widgetId", -1),
                provider = o.optString("provider"),
                alpha = o.optDouble("alpha", -1.0).toFloat(),
            )
        }
    }
}

object LayoutJson {
    fun itemsToJson(items: List<HomeItem>): JSONArray = JSONArray().also { arr ->
        items.forEach { arr.put(it.toJson()) }
    }

    /** 壊れた要素は読み飛ばす。 */
    fun itemsFromJson(arr: JSONArray): List<HomeItem> = buildList {
        for (i in 0 until arr.length()) {
            runCatching { HomeItem.fromJson(arr.getJSONObject(i)) }.getOrNull()?.let { add(it) }
        }
    }

    fun stringsToJson(list: List<String>): JSONArray = JSONArray().also { arr -> list.forEach { arr.put(it) } }

    fun stringsFromJson(arr: JSONArray?): List<String> = buildList {
        if (arr == null) return@buildList
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotEmpty()) add(s)
        }
    }

    fun countsToJson(counts: Map<String, Int>): JSONObject = JSONObject().also { o ->
        counts.forEach { (k, v) -> o.put(k, v) }
    }

    fun countsFromJson(o: JSONObject?): Map<String, Int> = buildMap {
        if (o == null) return@buildMap
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            put(k, o.optInt(k, 0).coerceAtLeast(0))
        }
    }
}

/** グリッド配置の純粋ロジック(テスト可能)。 */
object GridLogic {
    fun overlaps(a: HomeItem, x: Int, y: Int, sx: Int, sy: Int): Boolean =
        a.x < x + sx && x < a.x + a.spanX && a.y < y + sy && y < a.y + a.spanY

    fun isFree(items: List<HomeItem>, page: Int, x: Int, y: Int, sx: Int, sy: Int): Boolean =
        items.none { it.page == page && overlaps(it, x, y, sx, sy) }

    /** 空きセルを探す。見つからなければ null。戻り値は Triple(page, x, y)。 */
    fun findFreeCell(
        items: List<HomeItem>,
        pageCount: Int,
        cols: Int,
        rows: Int,
        sx: Int = 1,
        sy: Int = 1,
    ): Triple<Int, Int, Int>? {
        if (sx > cols || sy > rows) return null
        for (page in 0 until pageCount) {
            for (y in 0..(rows - sy)) {
                for (x in 0..(cols - sx)) {
                    if (isFree(items, page, x, y, sx, sy)) return Triple(page, x, y)
                }
            }
        }
        return null
    }

    /** グリッドサイズが縮んでも画面内に収まるよう補正した表示用コピーを返す。 */
    fun clamp(item: HomeItem, cols: Int, rows: Int): HomeItem {
        val sx = item.spanX.coerceIn(1, cols)
        val sy = item.spanY.coerceIn(1, rows)
        return item.copy(
            spanX = sx,
            spanY = sy,
            x = item.x.coerceIn(0, cols - sx),
            y = item.y.coerceIn(0, rows - sy),
        )
    }
}
