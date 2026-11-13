package jp.achi.homelauncher.data

import org.json.JSONObject

/** ランチャーの全設定。JSON で保存・バックアップされる。 */
data class LauncherSettings(
    // ホーム画面
    val gridCols: Int = 4,
    val gridRows: Int = 5,
    val pageCount: Int = 2,
    val iconSizeDp: Int = 56,
    val iconAlpha: Float = 1f,
    val showLabels: Boolean = true,
    val labelSizeSp: Int = 11,
    val lockLayout: Boolean = false,
    /** true なら、ドラッグ移動・リサイズをマスに揃えず自由な位置・大きさにできる */
    val freeMode: Boolean = false,
    /** アイコンの形: original / circle / squircle / rounded / wobbly / pentagon / hexagon / octagon / flower */
    val iconShape: String = "original",
    /** 通知の数をアイコンに表示する(通知アクセスの許可が必要) */
    val showBadges: Boolean = true,
    /** アプリ名の最大行数(1〜2) */
    val labelLines: Int = 1,
    /** テーマアイコン(モノクロアイコンをテーマ色で表示。Android 13+): auto=端末の設定に従う / on / off */
    val themedIcons: String = "auto",
    /** ホーム画面の最後のページの次は1ページ目に戻る(逆も) */
    val homeLoop: Boolean = true,
    /** ホームボタンで表示するメインのページ(0始まり) */
    val homePage: Int = 0,
    /** ホーム画面の周りの余白(dp) */
    val marginDp: Int = 0,
    /** メディアバーに再生中のアプリと出力先(Bluetooth等)を表示する */
    val mediaShowSource: Boolean = true,
    /** 検索バーの履歴の表示件数(0で履歴オフ) */
    val searchHistoryCount: Int = 5,
    /** 追加したフォントのファイル名(アプリ内保存)。空なら標準 */
    val fontFile: String = "",
    val fontName: String = "",
    /** 配置先に既存の項目がある場合の動作: overlap(被せる) / push_down(既存を下へ) / push_up(既存を上へ) */
    val overlapPolicy: String = OVERLAP,
    // ドック
    val showDock: Boolean = true,
    val dockCount: Int = 5,
    val dockHeightDp: Int = 72,
    val dockIconAlpha: Float = 1f,
    val dockIconSizeDp: Int = 56,
    /** 最後のページからさらに進むと最初のページに戻る */
    val dockLoop: Boolean = true,
    // 検索バー・メディアバー(個別設定がなければこの不透明度)
    val searchBarAlpha: Float = 0.92f,
    val mediaBarAlpha: Float = 0.97f,
    /** メディアバーのボタンの大きさ(sp)。0 なら自動 */
    val mediaButtonSp: Int = 0,
    /** ボタンを透かして、曲名をその上に重ねて表示する */
    val mediaOverlay: Boolean = false,
    /** ホーム画面に「編集」ボタンを表示する(長押しなしで編集モードに入る) */
    val showEditButton: Boolean = true,
    // アプリ一覧
    val drawerCols: Int = 4,
    val drawerIconSizeDp: Int = 52,
    val drawerSort: SortMode = SortMode.NAME,
    /** theme=テーマ色 / custom=好きな色 */
    val drawerBgMode: String = "theme",
    val drawerBgColor: Int = DEFAULT_DRAWER_COLOR,
    val drawerBgAlpha: Float = 0.97f,
    // ウィジェット(全体設定。個別設定がある場合はそちらが優先)
    val widgetGlobalAlpha: Float = 1f,
    // 外観
    val useDynamicColor: Boolean = true,
    val darkTheme: String = "system", // system / light / dark
    /** ホーム画面の色。system=アプリ全体と同じ / custom=好きな色 */
    val homeColorMode: String = "system",
    val homeColor: Int = DEFAULT_HOME_COLOR,
    val homeDarkTheme: String = "light", // system / light / dark (homeColorMode=custom のとき)
    val scrimAlpha: Float = 0.25f,
    // 操作
    val haptics: Boolean = true,
    val searchUrl: String = DEFAULT_SEARCH_URL,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("gridCols", gridCols)
        put("gridRows", gridRows)
        put("pageCount", pageCount)
        put("iconSizeDp", iconSizeDp)
        put("iconAlpha", iconAlpha.toDouble())
        put("showLabels", showLabels)
        put("labelSizeSp", labelSizeSp)
        put("lockLayout", lockLayout)
        put("freeMode", freeMode)
        put("iconShape", iconShape)
        put("showBadges", showBadges)
        put("labelLines", labelLines)
        put("themedIcons", themedIcons)
        put("homeLoop", homeLoop)
        put("homePage", homePage)
        put("marginDp", marginDp)
        put("mediaShowSource", mediaShowSource)
        put("searchHistoryCount", searchHistoryCount)
        put("fontFile", fontFile)
        put("fontName", fontName)
        put("overlapPolicy", overlapPolicy)
        put("showDock", showDock)
        put("dockCount", dockCount)
        put("dockHeightDp", dockHeightDp)
        put("dockIconAlpha", dockIconAlpha.toDouble())
        put("dockIconSizeDp", dockIconSizeDp)
        put("mediaButtonSp", mediaButtonSp)
        put("mediaOverlay", mediaOverlay)
        put("showEditButton", showEditButton)
        put("dockLoop", dockLoop)
        put("searchBarAlpha", searchBarAlpha.toDouble())
        put("mediaBarAlpha", mediaBarAlpha.toDouble())
        put("drawerCols", drawerCols)
        put("drawerIconSizeDp", drawerIconSizeDp)
        put("drawerSort", drawerSort.name)
        put("drawerBgMode", drawerBgMode)
        put("drawerBgColor", drawerBgColor)
        put("drawerBgAlpha", drawerBgAlpha.toDouble())
        put("widgetGlobalAlpha", widgetGlobalAlpha.toDouble())
        put("useDynamicColor", useDynamicColor)
        put("darkTheme", darkTheme)
        put("homeColorMode", homeColorMode)
        put("homeColor", homeColor)
        put("homeDarkTheme", homeDarkTheme)
        put("scrimAlpha", scrimAlpha.toDouble())
        put("haptics", haptics)
        put("searchUrl", searchUrl)
    }

    companion object {
        const val DEFAULT_SEARCH_URL = "https://www.google.com/search?q=%s"
        const val OVERLAP = "overlap"
        const val PUSH_DOWN = "push_down"
        const val PUSH_UP = "push_up"
        /** 押し出し: 重なった位置から後ろのアイコンを1つずつずらす(A B C → C A B) */
        const val SHIFT = "shift"
        val SHAPES = setOf("original", "circle", "squircle", "rounded", "wobbly", "pentagon", "hexagon", "octagon", "flower", "heart")
        val DEFAULT_HOME_COLOR: Int = 0xFFF48FB1.toInt()
        val DEFAULT_DRAWER_COLOR: Int = 0xFFFFFFFF.toInt()

        private fun JSONObject.alpha(key: String, def: Float, min: Float = 0f): Float =
            optDouble(key, def.toDouble()).toFloat().coerceIn(min, 1f)

        private fun oneOf(value: String, allowed: Set<String>, def: String) = if (value in allowed) value else def

        /** 欠けている項目は既定値、範囲外の値は範囲内に丸めて読み込む。 */
        fun fromJson(o: JSONObject): LauncherSettings {
            val d = LauncherSettings()
            val url = o.optString("searchUrl", d.searchUrl)
            val themes = setOf("system", "light", "dark")
            val sortName = o.optString("drawerSort", d.drawerSort.name)
            return LauncherSettings(
                gridCols = o.optInt("gridCols", d.gridCols).coerceIn(3, 8),
                gridRows = o.optInt("gridRows", d.gridRows).coerceIn(3, 10),
                pageCount = o.optInt("pageCount", d.pageCount).coerceIn(1, 8),
                iconSizeDp = o.optInt("iconSizeDp", d.iconSizeDp).coerceIn(32, 96),
                iconAlpha = o.alpha("iconAlpha", d.iconAlpha, 0.1f),
                showLabels = o.optBoolean("showLabels", d.showLabels),
                labelSizeSp = o.optInt("labelSizeSp", d.labelSizeSp).coerceIn(8, 16),
                lockLayout = o.optBoolean("lockLayout", d.lockLayout),
                freeMode = o.optBoolean("freeMode", d.freeMode),
                iconShape = oneOf(o.optString("iconShape", d.iconShape), SHAPES, d.iconShape),
                showBadges = o.optBoolean("showBadges", d.showBadges),
                labelLines = o.optInt("labelLines", d.labelLines).coerceIn(1, 2),
                themedIcons = oneOf(o.optString("themedIcons", d.themedIcons), setOf("auto", "on", "off"), d.themedIcons),
                homeLoop = o.optBoolean("homeLoop", d.homeLoop),
                homePage = o.optInt("homePage", d.homePage).coerceIn(0, 7),
                marginDp = o.optInt("marginDp", d.marginDp).coerceIn(0, 48),
                mediaShowSource = o.optBoolean("mediaShowSource", d.mediaShowSource),
                searchHistoryCount = o.optInt("searchHistoryCount", d.searchHistoryCount).coerceIn(0, 10),
                fontFile = o.optString("fontFile", d.fontFile).filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' },
                fontName = o.optString("fontName", d.fontName).take(60),
                overlapPolicy = oneOf(o.optString("overlapPolicy", d.overlapPolicy), setOf(OVERLAP, PUSH_DOWN, PUSH_UP, SHIFT), d.overlapPolicy),
                showDock = o.optBoolean("showDock", d.showDock),
                dockCount = o.optInt("dockCount", d.dockCount).coerceIn(3, 7),
                dockHeightDp = o.optInt("dockHeightDp", d.dockHeightDp).coerceIn(48, 160),
                dockIconAlpha = o.alpha("dockIconAlpha", d.dockIconAlpha, 0.1f),
                dockIconSizeDp = o.optInt("dockIconSizeDp", d.dockIconSizeDp).coerceIn(24, 120),
                mediaButtonSp = o.optInt("mediaButtonSp", d.mediaButtonSp).let { if (it <= 0) 0 else it.coerceIn(14, 44) },
                mediaOverlay = o.optBoolean("mediaOverlay", d.mediaOverlay),
                showEditButton = o.optBoolean("showEditButton", d.showEditButton),
                dockLoop = o.optBoolean("dockLoop", d.dockLoop),
                searchBarAlpha = o.alpha("searchBarAlpha", d.searchBarAlpha, 0.1f),
                mediaBarAlpha = o.alpha("mediaBarAlpha", d.mediaBarAlpha, 0.1f),
                drawerCols = o.optInt("drawerCols", d.drawerCols).coerceIn(3, 7),
                drawerIconSizeDp = o.optInt("drawerIconSizeDp", d.drawerIconSizeDp).coerceIn(32, 96),
                // 旧バージョンの "LAUNCH" は「よく使う順」に読み替える
                drawerSort = if (sortName == "LAUNCH") SortMode.FREQUENT
                else runCatching { SortMode.valueOf(sortName) }.getOrDefault(d.drawerSort),
                drawerBgMode = oneOf(o.optString("drawerBgMode", d.drawerBgMode), setOf("theme", "custom"), d.drawerBgMode),
                drawerBgColor = o.optInt("drawerBgColor", d.drawerBgColor),
                drawerBgAlpha = o.alpha("drawerBgAlpha", d.drawerBgAlpha, 0.3f),
                widgetGlobalAlpha = o.alpha("widgetGlobalAlpha", d.widgetGlobalAlpha),
                useDynamicColor = o.optBoolean("useDynamicColor", d.useDynamicColor),
                darkTheme = oneOf(o.optString("darkTheme", d.darkTheme), themes, d.darkTheme),
                homeColorMode = oneOf(o.optString("homeColorMode", d.homeColorMode), setOf("system", "custom"), d.homeColorMode),
                homeColor = o.optInt("homeColor", d.homeColor),
                homeDarkTheme = oneOf(o.optString("homeDarkTheme", d.homeDarkTheme), themes, d.homeDarkTheme),
                scrimAlpha = o.optDouble("scrimAlpha", d.scrimAlpha.toDouble()).toFloat().coerceIn(0f, 0.8f),
                haptics = o.optBoolean("haptics", d.haptics),
                searchUrl = if (url.startsWith("http") && url.contains("%s")) url else d.searchUrl,
            )
        }
    }
}
