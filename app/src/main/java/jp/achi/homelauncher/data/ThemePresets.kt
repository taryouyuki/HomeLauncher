package jp.achi.homelauncher.data

/** 儚い系・ゆめかわ系のかわいいテーマ。ホーム画面と一覧の色・背景の暗さ・アイコンの形をまとめて設定する。 */
data class ThemePreset(
    val id: String,
    val name: String,
    /** ホーム画面の基準色 */
    val home: Int,
    /** アプリ一覧の背景色 */
    val drawer: Int,
    val scrim: Float,
    val shape: String,
)

object ThemePresets {
    val all: List<ThemePreset> = listOf(
        ThemePreset("yumekawa", "ゆめかわパステル", 0xFFF3A6DC.toInt(), 0xFFFFF0FB.toInt(), 0.05f, "wobbly"),
        ThemePreset("lavender", "ミルキーラベンダー", 0xFFBFAEF2.toInt(), 0xFFF6F1FF.toInt(), 0.08f, "squircle"),
        ThemePreset("soda", "ソーダミント", 0xFF9FE3D3.toInt(), 0xFFEFFBF8.toInt(), 0.06f, "circle"),
        ThemePreset("sakura", "さくら雨(儚いピンク)", 0xFFF7BCCB.toInt(), 0xFFFFF5F7.toInt(), 0.10f, "heart"),
        ThemePreset("moon", "月あかりブルー", 0xFFA9C7F2.toInt(), 0xFFF2F7FF.toInt(), 0.12f, "flower"),
    )

    fun apply(s: LauncherSettings, p: ThemePreset): LauncherSettings = s.copy(
        homeColorMode = "custom",
        homeColor = p.home,
        homeDarkTheme = "light",
        drawerBgMode = "custom",
        drawerBgColor = p.drawer,
        drawerBgAlpha = 0.92f,
        scrimAlpha = p.scrim,
        iconShape = p.shape,
    )
}
