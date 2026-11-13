package jp.achi.homelauncher

import jp.achi.homelauncher.data.BackupCodec
import jp.achi.homelauncher.data.DockEntry
import jp.achi.homelauncher.data.Folder
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
            drawerSort = SortMode.FREQUENT,
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
    fun foldersStacksAndSpecialItemsRoundTrip() {
        val folder = Folder(id = "f1", name = "ゲーム", apps = listOf("p/a", "q/b"))
        val items = listOf(
            HomeItem(id = "fi", type = HomeItem.FOLDER, page = 0, x = 0, y = 0, ref = "f1"),
            HomeItem(id = "st", type = HomeItem.STACK, page = 0, x = 0, y = 1, spanX = 4, spanY = 2),
            HomeItem(id = "ch", type = HomeItem.WIDGET, page = 0, x = 0, y = 0, widgetId = 9, provider = "p/.W", parent = "st"),
            HomeItem(id = "se", type = HomeItem.SEARCH, page = 0, x = 0, y = 3, spanX = 4),
            HomeItem(id = "me", type = HomeItem.MEDIA, page = 0, x = 0, y = 4, spanX = 4),
        )
        val dock = listOf("p/a", DockEntry.folder("f1"))
        val r = BackupCodec.decode(BackupCodec.encode(LauncherSettings(), items, dock, emptyMap(), listOf(folder)))

        assertEquals(listOf(folder), r.folders)
        assertEquals(dock, r.dock)
        assertEquals(5, r.items.size)
        assertEquals("st", r.items.first { it.id == "ch" }.parent)
        assertEquals(-1, r.items.first { it.id == "ch" }.widgetId)
        assertEquals("f1", r.items.first { it.id == "fi" }.ref)
        assertEquals("f1", DockEntry.folderId(dock[1]))
        assertEquals(null, DockEntry.folderId("p/a"))
    }

    @Test
    fun iconAlphaIsClamped() {
        assertEquals(0.1f, LauncherSettings.fromJson(JSONObject().put("iconAlpha", 0.0)).iconAlpha, 0.001f)
        assertEquals(1f, LauncherSettings.fromJson(JSONObject()).iconAlpha, 0.001f)
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

    @Test
    fun newFieldsRoundTrip() {
        val settings = LauncherSettings(freeMode = true, iconShape = "hexagon", showBadges = false, fontFile = "font_1.ttf", fontName = "テスト")
        val items = listOf(
            HomeItem(id = "a", type = HomeItem.APP, page = 0, x = 1, y = 1, pkg = "p", cls = "c",
                free = true, fx = 1.25f, fy = 2.5f, fw = 1.5f, fh = 1.5f, dtap = "app:q/d"),
        )
        val r = BackupCodec.decode(BackupCodec.encode(settings, items, emptyList(), emptyMap(), yomi = mapOf("p/c" to "てすと")))
        assertEquals(settings, r.settings)
        assertEquals(items, r.items)
        assertEquals(mapOf("p/c" to "てすと"), r.yomi)
    }

    @Test
    fun round6SettingsRoundTrip() {
        val settings = LauncherSettings(
            labelLines = 2, themedIcons = "on", homeLoop = false, homePage = 1,
            marginDp = 12, mediaShowSource = false, searchHistoryCount = 3,
            folderCols = 5, quickPanel = false, quickApps = "a.b,c.d",
            freeWidgetsOnly = true, mergeOnDrop = false, iconShape = "heart_round",
            folderBgMode = "custom", folderBgColor = 0xFF112233.toInt(), folderBgAlpha = 0.5f, dockPages = 3,
        )
        val items = listOf(HomeItem(id = "s", type = HomeItem.ICONS, page = 0, x = 0, y = 0, spanX = 3, spanY = 3, alpha = 0.5f))
        val r = BackupCodec.decode(BackupCodec.encode(settings, items, emptyList(), emptyMap()))
        assertEquals(settings, r.settings)
        assertEquals(items, r.items)
    }

    @Test
    fun unknownIconShapeFallsBack() {
        val o = JSONObject().put("iconShape", "banana")
        assertEquals("original", LauncherSettings.fromJson(o).iconShape)
    }
}
