package jp.achi.homelauncher

import jp.achi.homelauncher.data.BackupCodec
import jp.achi.homelauncher.data.HomeItem
import jp.achi.homelauncher.data.LauncherSettings
import jp.achi.homelauncher.data.SortMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    @Test
    fun roundTripKeepsEverything() {
        val settings = LauncherSettings(
            gridCols = 5,
            iconSizeDp = 72,
            drawerSort = SortMode.LAUNCH,
            widgetGlobalAlpha = 0.4f,
            darkTheme = "dark",
        )
        val items = listOf(
            HomeItem(id = "a", type = HomeItem.APP, page = 0, x = 1, y = 2, pkg = "p", cls = "c"),
            HomeItem(
                id = "w", type = HomeItem.WIDGET, page = 1, x = 0, y = 0, spanX = 3, spanY = 2,
                widgetId = 42, provider = "p/.W", alpha = 0.5f,
            ),
        )
        val text = BackupCodec.encode(settings, items, listOf("p/c"), mapOf("p/c" to 7))
        val r = BackupCodec.decode(text)

        assertEquals(settings, r.settings)
        assertEquals(listOf("p/c"), r.dock)
        assertEquals(mapOf("p/c" to 7), r.counts)
        assertEquals(2, r.items.size)
        val w = r.items.first { it.id == "w" }
        assertEquals(3, w.spanX)
        assertEquals(2, w.spanY)
        assertEquals(0.5f, w.alpha, 0.001f)
        // ウィジェットIDは端末依存なので復元時に破棄される
        assertEquals(-1, w.widgetId)
    }

    @Test
    fun rejectsGarbage() {
        try {
            BackupCodec.decode("not json")
            fail("should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
        try {
            BackupCodec.decode("{\"format\":\"other\",\"version\":1}")
            fail("should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun outOfRangeSettingsAreClamped() {
        val o = JSONObject().put("gridCols", 99).put("iconSizeDp", 1).put("widgetGlobalAlpha", 5.0)
            .put("drawerSort", "BOGUS").put("searchUrl", "javascript:alert(1)")
        val s = LauncherSettings.fromJson(o)
        assertEquals(8, s.gridCols)
        assertEquals(32, s.iconSizeDp)
        assertEquals(1f, s.widgetGlobalAlpha, 0.001f)
        assertEquals(SortMode.NAME, s.drawerSort)
        assertEquals(LauncherSettings.DEFAULT_SEARCH_URL, s.searchUrl)
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        val text = JSONObject().put("format", BackupCodec.FORMAT).put("version", 1).toString()
        val r = BackupCodec.decode(text)
        assertEquals(LauncherSettings(), r.settings)
        assertTrue(r.items.isEmpty())
    }
}
