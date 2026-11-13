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
    /** 初回インストール日時(ダウンロード順の並び替え用) */
    val installedAt: Long = 0L,
    /** 仕事用プロファイル(Island など)のアプリか */
    val work: Boolean = false,
    /** work=true のとき、プロファイルのユーザー識別番号 */
    val userSerial: Long = 0L,
    /** ApplicationInfo.category(-1 は未設定) */
    val category: Int = -1,
    val isGame: Boolean = false,
    val isSystem: Boolean = false,
) {
    val key: String get() = if (work) "w$userSerial:$packageName/$className" else "$packageName/$className"
}

/** 名前順 / 最終起動順 / ダウンロード(インストール)順 / よく使う順(起動回数) / 更新順 */
enum class SortMode { NAME, LAST_LAUNCH, INSTALL, FREQUENT, UPDATED, CATEGORY }

/** アプリのカテゴリー(ゲーム・SNS・ツール等)。OSの分類とパッケージ名から自動で仕分けし、手動で上書きもできる。 */
object AppCategory {
    val ORDER = listOf("ゲーム", "SNS・通信", "音楽・動画", "写真", "ニュース", "地図・移動", "仕事・効率", "ツール", "システム", "その他")

    fun auto(app: AppInfo): String {
        if (app.isGame || app.category == 0) return "ゲーム"
        when (app.category) {
            1, 2 -> return "音楽・動画"
            3 -> return "写真"
            4 -> return "SNS・通信"
            5 -> return "ニュース"
            6 -> return "地図・移動"
            7 -> return "仕事・効率"
            8 -> return "ツール"
        }
        val p = (app.packageName + " " + app.label).lowercase()
        fun has(vararg k: String) = k.any { p.contains(it) }
        return when {
            has("game", "ゲーム") -> "ゲーム"
            has("camera", "photo", "gallery", "カメラ", "写真") -> "写真"
            has("music", "video", "player", "radio", "podcast", "youtube", "音楽", "動画") -> "音楽・動画"
            has("mail", "messag", "chat", "line", "twitter", "instagram", "facebook", "discord", "dialer", "contacts", "電話", "連絡先", "メール") -> "SNS・通信"
            has("maps", "navi", "transit", "地図", "乗換") -> "地図・移動"
            has("docs", "office", "note", "calendar", "drive", "sheet", "keep", "メモ", "カレンダー") -> "仕事・効率"
            has("calc", "clock", "torch", "flashlight", "scanner", "vpn", "file", "電卓", "時計", "ファイル") -> "ツール"
            app.isSystem -> "システム"
            else -> "その他"
        }
    }

    fun of(app: AppInfo, manual: Map<String, String>): String = manual[app.key] ?: auto(app)

    /** 表示順。定義済みのカテゴリー → 自分で作った名前(五十音) の順。 */
    fun order(name: String): Int = ORDER.indexOf(name).let { if (it < 0) ORDER.size else it }
}

object AppSorter {
    /**
     * 並び替え。名前順は日本語の読み順(Collator)。
     * 最終起動順・ダウンロード順・更新順は新しい順、よく使う順は起動回数の多い順。同値は名前順。
     */
    fun sort(
        apps: List<AppInfo>,
        mode: SortMode,
        counts: Map<String, Int>,
        lastLaunch: Map<String, Long> = emptyMap(),
        categories: Map<String, String> = emptyMap(),
    ): List<AppInfo> {
        val collator = Collator.getInstance(Locale.JAPANESE)
        val byName = Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
            .thenBy { it.key }
        return when (mode) {
            SortMode.NAME -> apps.sortedWith(byName)
            SortMode.LAST_LAUNCH -> apps.sortedWith(compareByDescending<AppInfo> { lastLaunch[it.key] ?: 0L }.then(byName))
            SortMode.INSTALL -> apps.sortedWith(compareByDescending<AppInfo> { it.installedAt }.then(byName))
            SortMode.FREQUENT -> apps.sortedWith(compareByDescending<AppInfo> { counts[it.key] ?: 0 }.then(byName))
            SortMode.UPDATED -> apps.sortedWith(compareByDescending<AppInfo> { it.updatedAt }.then(byName))
            SortMode.CATEGORY -> apps.sortedWith(
                compareBy<AppInfo> { AppCategory.order(AppCategory.of(it, categories)) }
                    .thenBy { AppCategory.of(it, categories) }
                    .then(byName),
            )
        }
    }

    /** アプリ名の部分一致(大文字小文字は区別しない)。空文字なら全件。 */
    fun filter(apps: List<AppInfo>, query: String, yomi: Map<String, String> = emptyMap()): List<AppInfo> {
        if (query.isBlank()) return apps
        return apps.filter { Kana.matches(it.label, query, yomi[it.key]) }
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
    /** FOLDER: フォルダID */
    val ref: String = "",
    /** ウィジェットスタックの子なら、親STACKのitem id。空ならホーム直置き。 */
    val parent: String = "",
    /** 自由配置(マスに縛られない)。true のとき fx/fy/fw/fh(マス単位の小数)で描画する。x,y,spanX,spanY は丸めた値。 */
    val free: Boolean = false,
    val fx: Float = 0f,
    val fy: Float = 0f,
    val fw: Float = 1f,
    val fh: Float = 1f,
    /** ダブルタップで開く対象。"app:pkg/cls" または "sc:pkg|shortcutId"。空なら無効。 */
    val dtap: String = "",
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
        put("ref", ref)
        put("parent", parent)
        put("free", free)
        put("fx", fx.toDouble())
        put("fy", fy.toDouble())
        put("fw", fw.toDouble())
        put("fh", fh.toDouble())
        put("dtap", dtap)
    }

    companion object {
        const val APP = "app"
        const val WIDGET = "widget"
        const val FOLDER = "folder"
        const val STACK = "stack"
        /** アイコンセット: 数個〜9個のアプリを1つの枠にまとめ、左右スワイプでセットを切り替える(最後の次は最初に戻る)。子は parent = このid */
        const val ICONS = "icons"
        const val SEARCH = "search"
        const val MEDIA = "media"
        /** ブラウザ等から「ホーム画面に追加」されたショートカット。pkg=パッケージ, cls=ショートカットID, ref=表示名 */
        const val SHORTCUT = "shortcut"
        private val TYPES = setOf(APP, WIDGET, FOLDER, STACK, ICONS, SEARCH, MEDIA, SHORTCUT)

        fun fromJson(o: JSONObject): HomeItem {
            val type = o.getString("type")
            require(type in TYPES) { "unknown item type: $type" }
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
                ref = o.optString("ref"),
                parent = o.optString("parent"),
                free = o.optBoolean("free", false),
                fx = o.optDouble("fx", 0.0).toFloat(),
                fy = o.optDouble("fy", 0.0).toFloat(),
                fw = o.optDouble("fw", 1.0).toFloat().coerceAtLeast(0.2f),
                fh = o.optDouble("fh", 1.0).toFloat().coerceAtLeast(0.2f),
                dtap = o.optString("dtap"),
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
        items.none { it.parent.isEmpty() && it.page == page && overlaps(it, x, y, sx, sy) }

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

    /**
     * 項目 mover を配置し、既存項目との重なりを policy に従って処理する。
     * overlap=そのまま被せる / push_down=重なった項目を下へずらす / push_up=上へずらす。
     * 戻り値は (新しい項目リスト, mover が既存項目と重なったか)。mover はリスト末尾(最前面)に置く。
     */
    fun placeWithPolicy(
        items: List<HomeItem>,
        mover: HomeItem,
        policy: String,
        rows: Int,
        cols: Int = 4,
        pageCount: Int = 1,
    ): Pair<List<HomeItem>, Boolean> {
        val result = items.filter { it.id != mover.id }.toMutableList()
        result.add(mover)
        fun hits(cur: HomeItem) = result.filter {
            it.id != cur.id && it.parent.isEmpty() && !cur.free && it.page == cur.page &&
                overlaps(it, cur.x, cur.y, cur.spanX, cur.spanY)
        }
        val collided = hits(mover).isNotEmpty()
        if (collided && policy == "shift" && !mover.free && mover.spanX == 1 && mover.spanY == 1) {
            val shifted = shiftInsert(items.filter { it.id != mover.id }, mover, cols, rows)
            if (shifted != null) return shifted to true
        }
        val pol = if (policy == "shift") "push_down" else policy
        if (collided && (pol == "push_down" || pol == "push_up")) {
            val pending = ArrayDeque<HomeItem>()
            pending.add(mover)
            var guard = 0
            while (pending.isNotEmpty() && guard++ < 300) {
                val cur = pending.removeFirst()
                for (h in hits(cur)) {
                    val ny = if (pol == "push_down") {
                        (cur.y + cur.spanY).coerceAtMost((rows - h.spanY).coerceAtLeast(0))
                    } else {
                        (cur.y - h.spanY).coerceAtLeast(0)
                    }
                    if (ny == h.y) continue // これ以上動けない場合は重なったままにする
                    val moved = h.copy(y = ny, free = false)
                    result[result.indexOfFirst { it.id == h.id }] = moved
                    pending.add(moved)
                }
            }
        }
        // ずらしきれず重なったままの項目は、空いているマスへ移す(重ねる設定のときは何もしない)
        if (collided && policy != "overlap") {
            for (h in hits(mover)) {
                val rest = result.filter { it.id != h.id }
                val cell = findFreeCell(rest, pageCount, cols, rows, h.spanX, h.spanY) ?: continue
                result[result.indexOfFirst { it.id == h.id }] = h.copy(page = cell.first, x = cell.second, y = cell.third, free = false)
            }
        }
        return result to collided
    }

    /**
     * 押し出し(挿入)。mover を置く位置から行方向(左→右、次の行へ)に、連続して埋まっているアイコンを
     * 1つずつ後ろへずらして空きを作る。A B C で C を先頭に置くと C A B になる。
     * 1マスのアイコン以外(ウィジェット等)にぶつかる、または空きが無い場合は null(呼び出し側で別の方法にする)。
     */
    fun shiftInsert(others: List<HomeItem>, mover: HomeItem, cols: Int, rows: Int): List<HomeItem>? {
        val total = cols * rows
        val page = others.filter { it.parent.isEmpty() && it.page == mover.page }
        fun at(c: Int): HomeItem? = page.firstOrNull { overlaps(it, c % cols, c / cols, 1, 1) }
        val chain = mutableListOf<HomeItem>()
        var cur = mover.y * cols + mover.x
        while (cur < total) {
            val h = at(cur) ?: break
            if (h.spanX != 1 || h.spanY != 1) return null
            chain.add(h)
            cur++
        }
        if (cur >= total) return null
        val moved = chain.associate { it.id to it.copy(x = (it.y * cols + it.x + 1) % cols, y = (it.y * cols + it.x + 1) / cols) }
        return others.map { moved[it.id] ?: it } + mover
    }

    /** グリッドサイズが縮んでも画面内に収まるよう補正した表示用コピーを返す。 */
    fun clamp(item: HomeItem, cols: Int, rows: Int): HomeItem {
        if (item.free) {
            val fw = item.fw.coerceIn(0.3f, cols.toFloat())
            val fh = item.fh.coerceIn(0.3f, rows.toFloat())
            val fx = item.fx.coerceIn(0f, cols - fw)
            val fy = item.fy.coerceIn(0f, rows - fh)
            return item.copy(
                fx = fx, fy = fy, fw = fw, fh = fh,
                x = fx.toInt().coerceIn(0, cols - 1), y = fy.toInt().coerceIn(0, rows - 1),
                spanX = kotlin.math.ceil(fw).toInt().coerceIn(1, cols), spanY = kotlin.math.ceil(fh).toInt().coerceIn(1, rows),
            )
        }
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

/** 表示の自動調整(AIなし。セルの大きさから大きさ・間隔を決め、はみ出しや重なりを整える)。 */
object AutoLayout {
    /** セルの大きさ(dp)に合わせてアイコン・文字・ドックの大きさを決める。 */
    fun fitSettings(s: LauncherSettings, cellWdp: Float, cellHdp: Float): LauncherSettings {
        val labelRoom = if (s.showLabels) 0.62f else 0.8f
        val icon = minOf(cellWdp * 0.72f, cellHdp * labelRoom).toInt().coerceIn(32, 96)
        val label = (icon * 0.2f).toInt().coerceIn(9, 14)
        val dock = (icon + 16).coerceIn(56, 120)
        return s.copy(
            iconSizeDp = icon,
            labelSizeSp = label,
            dockHeightDp = dock,
            dockIconSizeDp = icon,
            drawerIconSizeDp = icon.coerceIn(40, 72),
        )
    }

    private fun isIconType(i: HomeItem) =
        i.parent.isEmpty() && (i.type == HomeItem.APP || i.type == HomeItem.SHORTCUT || i.type == HomeItem.FOLDER)

    /** アイコンだけを整列: アイコンを現在の並び順のまま、空いているマスへ左上から詰める(ウィジェットなどは動かさない)。 */
    fun arrangeIcons(items: List<HomeItem>, cols: Int, rows: Int, pageCount: Int): List<HomeItem> {
        val icons = items.filter(::isIconType).sortedWith(compareBy({ it.page }, { it.y }, { it.x }))
        val placed = items.filter { it.parent.isEmpty() && !isIconType(it) }.toMutableList()
        val out = HashMap<String, HomeItem>()
        var p = 0
        var x = 0
        var y = 0
        for (icon in icons) {
            var done = false
            while (p < pageCount && !done) {
                if (GridLogic.isFree(placed, p, x, y, 1, 1)) {
                    val n = icon.copy(page = p, x = x, y = y, spanX = 1, spanY = 1, free = false, fw = 1f, fh = 1f)
                    placed.add(n)
                    out[icon.id] = n
                    done = true
                }
                x++
                if (x >= cols) { x = 0; y++ }
                if (y >= rows) { y = 0; p++ }
            }
        }
        return items.map { out[it.id] ?: it }
    }

    /** ウィジェットだけを整列(ウィジェット・スタック・アイコンセット): アイコン等は動かさず、空きへ左上から詰める。 */
    fun arrangeWidgets(items: List<HomeItem>, cols: Int, rows: Int, pageCount: Int): List<HomeItem> {
        fun isBlock(i: HomeItem) = i.parent.isEmpty() &&
            (i.type == HomeItem.WIDGET || i.type == HomeItem.STACK || i.type == HomeItem.ICONS)
        val blocks = items.filter(::isBlock).sortedWith(compareBy({ it.page }, { it.y }, { it.x }))
        val placed = items.filter { it.parent.isEmpty() && !isBlock(it) }.toMutableList()
        val out = HashMap<String, HomeItem>()
        for (b0 in blocks) {
            val b = GridLogic.clamp(b0.copy(free = false), cols, rows)
            val cell = GridLogic.findFreeCell(placed, pageCount, cols, rows, b.spanX, b.spanY)
            val n = if (cell != null) b.copy(page = cell.first, x = cell.second, y = cell.third) else b
            placed.add(n)
            out[b0.id] = n
        }
        return items.map { out[it.id] ?: it }
    }

    /** 自由配置をマスに揃え、はみ出しを直し、重なった項目は空きマスへ移す。 */
    /**
     * おまかせ微調整: 配置を大きく変えず、ばらついた大きさ・位置だけをそろえる。
     * アイコン類は1マスにそろえ、自由配置のものは最も近いマスへ。ウィジェット類は自由配置を最も近いマスの大きさに。
     * 最後に、はみ出し・重なりだけを整える。
     */
    fun harmonize(items: List<HomeItem>, cols: Int, rows: Int, pageCount: Int, icons: Boolean, widgets: Boolean): List<HomeItem> {
        val snapped = items.map { i ->
            if (i.parent.isNotEmpty()) return@map i
            val iconLike = i.type == HomeItem.APP || i.type == HomeItem.SHORTCUT || i.type == HomeItem.FOLDER
            val widgetLike = i.type == HomeItem.WIDGET || i.type == HomeItem.STACK || i.type == HomeItem.ICONS
            when {
                iconLike && icons -> i.copy(
                    free = false,
                    x = (if (i.free) Math.round(i.fx) else i.x).coerceIn(0, cols - 1),
                    y = (if (i.free) Math.round(i.fy) else i.y).coerceIn(0, rows - 1),
                    spanX = 1, spanY = 1, fw = 1f, fh = 1f,
                )
                widgetLike && widgets && i.free -> i.copy(
                    free = false,
                    x = Math.round(i.fx).coerceIn(0, cols - 1),
                    y = Math.round(i.fy).coerceIn(0, rows - 1),
                    spanX = Math.round(i.fw).coerceIn(1, cols),
                    spanY = Math.round(i.fh).coerceIn(1, rows),
                )
                else -> i
            }
        }
        return tidy(snapped, cols, rows, pageCount)
    }

    fun tidy(items: List<HomeItem>, cols: Int, rows: Int, pageCount: Int): List<HomeItem> {
        val top = items.filter { it.parent.isEmpty() }
            .sortedWith(compareBy({ it.page }, { it.y }, { it.x }))
        val placed = mutableListOf<HomeItem>()
        for (raw in top) {
            var it0 = GridLogic.clamp(raw, cols, rows)
            if (it0.free) {
                it0 = it0.copy(free = false, spanX = it0.spanX.coerceIn(1, cols), spanY = it0.spanY.coerceIn(1, rows))
                it0 = GridLogic.clamp(it0, cols, rows)
            }
            it0 = it0.copy(page = it0.page.coerceIn(0, pageCount - 1))
            if (GridLogic.isFree(placed, it0.page, it0.x, it0.y, it0.spanX, it0.spanY)) {
                placed.add(it0)
            } else {
                val cell = GridLogic.findFreeCell(placed, pageCount, cols, rows, it0.spanX, it0.spanY)
                placed.add(if (cell != null) it0.copy(page = cell.first, x = cell.second, y = cell.third) else it0)
            }
        }
        val byId = placed.associateBy { it.id }
        return items.map { byId[it.id] ?: it }
    }
}

/** フォルダに入れるときの文字列。アプリは "pkg/cls"、ショートカットは "sc:pkg|id|表示名"。 */
val HomeItem.folderEntry: String
    get() = if (type == HomeItem.SHORTCUT) "sc:$pkg|$cls|$ref" else appKey

/** アプリフォルダ。ホーム画面(HomeItem.FOLDER)とドックの両方から参照される。 */
data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val apps: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("apps", LayoutJson.stringsToJson(apps))
    }

    companion object {
        fun fromJson(o: JSONObject): Folder = Folder(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name").ifEmpty { "フォルダ" },
            apps = LayoutJson.stringsFromJson(o.optJSONArray("apps")),
        )

        fun listToJson(list: List<Folder>): JSONArray = JSONArray().also { arr -> list.forEach { arr.put(it.toJson()) } }

        fun listFromJson(arr: JSONArray?): List<Folder> = buildList {
            if (arr == null) return@buildList
            for (i in 0 until arr.length()) {
                runCatching { Folder.fromJson(arr.getJSONObject(i)) }.getOrNull()?.let { add(it) }
            }
        }
    }
}

/** ドックの項目は、アプリなら "pkg/cls"、フォルダなら "folder:<id>"。 */
object DockEntry {
    private const val FOLDER_PREFIX = "folder:"
    fun folder(id: String): String = FOLDER_PREFIX + id
    fun folderId(entry: String): String? = if (entry.startsWith(FOLDER_PREFIX)) entry.removePrefix(FOLDER_PREFIX) else null
}

/** 配置の結果。OVERLAPPED は既存項目と重なって配置したことを表す。 */
enum class PlaceResult { FAILED, PLACED, OVERLAPPED }
