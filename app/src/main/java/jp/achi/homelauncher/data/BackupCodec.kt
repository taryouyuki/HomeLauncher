package jp.achi.homelauncher.data

import org.json.JSONException
import org.json.JSONObject

/**
 * バックアップ(JSON)の読み書き。
 * ウィジェットIDは端末ごとに割り当てられるため保存しない(復元時に再バインドする)。
 */
object BackupCodec {
    const val FORMAT = "homelauncher-backup"
    const val VERSION = 1

    data class Backup(
        val settings: LauncherSettings,
        val items: List<HomeItem>,
        val dock: List<String>,
        val counts: Map<String, Int>,
        val folders: List<Folder> = emptyList(),
        val lastLaunch: Map<String, Long> = emptyMap(),
    )

    fun encode(
        settings: LauncherSettings,
        items: List<HomeItem>,
        dock: List<String>,
        counts: Map<String, Int>,
        folders: List<Folder> = emptyList(),
        lastLaunch: Map<String, Long> = emptyMap(),
    ): String {
        val portableItems = items.map { if (it.type == HomeItem.WIDGET) it.copy(widgetId = -1) else it }
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("settings", settings.toJson())
            put("items", LayoutJson.itemsToJson(portableItems))
            put("dock", LayoutJson.stringsToJson(dock))
            put("launchCounts", LayoutJson.countsToJson(counts))
            put("folders", Folder.listToJson(folders))
            put("lastLaunch", JSONObject().also { o -> lastLaunch.forEach { (k, v) -> o.put(k, v) } })
        }.toString(2)
    }

    /** 不正な内容なら IllegalArgumentException を投げる。 */
    fun decode(text: String): Backup {
        val o = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("JSONとして読み込めません", e)
        }
        require(o.optString("format") == FORMAT) { "HomeLauncherのバックアップではありません" }
        val version = o.optInt("version", 0)
        require(version in 1..VERSION) { "未対応のバックアップ形式です(version=$version)" }

        val settings = o.optJSONObject("settings")?.let { LauncherSettings.fromJson(it) } ?: LauncherSettings()
        val items = o.optJSONArray("items")?.let { LayoutJson.itemsFromJson(it) } ?: emptyList()
        val dock = LayoutJson.stringsFromJson(o.optJSONArray("dock"))
        val counts = LayoutJson.countsFromJson(o.optJSONObject("launchCounts"))
        val folders = Folder.listFromJson(o.optJSONArray("folders"))
        val lastLaunch = buildMap<String, Long> {
            val lo = o.optJSONObject("lastLaunch") ?: return@buildMap
            val keys = lo.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k, lo.optLong(k, 0L))
            }
        }
        return Backup(
            settings = settings,
            items = items.map { if (it.type == HomeItem.WIDGET) it.copy(widgetId = -1) else it },
            dock = dock,
            counts = counts,
            folders = folders,
            lastLaunch = lastLaunch,
        )
    }
}
