package jp.achi.homelauncher.data

import org.json.JSONObject

/** ランチャーの全設定。JSON で保存・バックアップされる。 */
data class LauncherSettings(
    // ホーム画面
    val gridCols: Int = 4,
    val gridRows: Int = 5,
    val pageCount: Int = 2,
    val iconSizeDp: Int = 56,
    val showLabels: Boolean = true,
    val labelSizeSp: Int = 11,
    val showDock: Boolean = true,
    val dockCount: Int = 5,
    val showSearchBar: Boolean = true,
    val showMediaPanel: Boolean = true,
    val lockLayout: Boolean = false,
    // アプリ一覧
    val drawerCols: Int = 4,
    val drawerIconSizeDp: Int = 52,
    val drawerSort: SortMode = SortMode.NAME,
    // ウィジェット(全体設定。個別設定がある場合はそちらが優先)
    val widgetGlobalAlpha: Float = 1f,
    // 外観
    val useDynamicColor: Boolean = true,
    val darkTheme: String = "system", // system / light / dark
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
        put("showLabels", showLabels)
        put("labelSizeSp", labelSizeSp)
        put("showDock", showDock)
        put("dockCount", dockCount)
        put("showSearchBar", showSearchBar)
        put("showMediaPanel", showMediaPanel)
        put("lockLayout", lockLayout)
        put("drawerCols", drawerCols)
        put("drawerIconSizeDp", drawerIconSizeDp)
        put("drawerSort", drawerSort.name)
        put("widgetGlobalAlpha", widgetGlobalAlpha.toDouble())
        put("useDynamicColor", useDynamicColor)
        put("darkTheme", darkTheme)
        put("scrimAlpha", scrimAlpha.toDouble())
        put("haptics", haptics)
        put("searchUrl", searchUrl)
    }

    companion object {
        const val DEFAULT_SEARCH_URL = "https://www.google.com/search?q=%s"

        /** 欠けている項目は既定値、範囲外の値は範囲内に丸めて読み込む。 */
        fun fromJson(o: JSONObject): LauncherSettings {
            val d = LauncherSettings()
            val url = o.optString("searchUrl", d.searchUrl)
            val theme = o.optString("darkTheme", d.darkTheme)
            return LauncherSettings(
                gridCols = o.optInt("gridCols", d.gridCols).coerceIn(3, 8),
                gridRows = o.optInt("gridRows", d.gridRows).coerceIn(3, 10),
                pageCount = o.optInt("pageCount", d.pageCount).coerceIn(1, 8),
                iconSizeDp = o.optInt("iconSizeDp", d.iconSizeDp).coerceIn(32, 96),
                showLabels = o.optBoolean("showLabels", d.showLabels),
                labelSizeSp = o.optInt("labelSizeSp", d.labelSizeSp).coerceIn(8, 16),
                showDock = o.optBoolean("showDock", d.showDock),
                dockCount = o.optInt("dockCount", d.dockCount).coerceIn(3, 7),
                showSearchBar = o.optBoolean("showSearchBar", d.showSearchBar),
                showMediaPanel = o.optBoolean("showMediaPanel", d.showMediaPanel),
                lockLayout = o.optBoolean("lockLayout", d.lockLayout),
                drawerCols = o.optInt("drawerCols", d.drawerCols).coerceIn(3, 7),
                drawerIconSizeDp = o.optInt("drawerIconSizeDp", d.drawerIconSizeDp).coerceIn(32, 96),
                drawerSort = runCatching { SortMode.valueOf(o.optString("drawerSort", d.drawerSort.name)) }
                    .getOrDefault(d.drawerSort),
                widgetGlobalAlpha = o.optDouble("widgetGlobalAlpha", d.widgetGlobalAlpha.toDouble())
                    .toFloat().coerceIn(0f, 1f),
                useDynamicColor = o.optBoolean("useDynamicColor", d.useDynamicColor),
                darkTheme = if (theme in setOf("system", "light", "dark")) theme else d.darkTheme,
                scrimAlpha = o.optDouble("scrimAlpha", d.scrimAlpha.toDouble()).toFloat().coerceIn(0f, 0.8f),
                haptics = o.optBoolean("haptics", d.haptics),
                searchUrl = if (url.startsWith("http") && url.contains("%s")) url else d.searchUrl,
            )
        }
    }
}
