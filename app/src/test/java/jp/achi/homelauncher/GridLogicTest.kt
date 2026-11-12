package jp.achi.homelauncher

import jp.achi.homelauncher.data.GridLogic
import jp.achi.homelauncher.data.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLogicTest {
    private fun app(page: Int, x: Int, y: Int) = HomeItem(type = HomeItem.APP, page = page, x = x, y = y)
    private fun widget(page: Int, x: Int, y: Int, sx: Int, sy: Int) =
        HomeItem(type = HomeItem.WIDGET, page = page, x = x, y = y, spanX = sx, spanY = sy)

    @Test
    fun firstFreeCellSkipsOccupied() {
        val items = listOf(app(0, 0, 0), app(0, 1, 0))
        assertEquals(Triple(0, 2, 0), GridLogic.findFreeCell(items, 1, 4, 4))
    }

    @Test
    fun movesToNextPageWhenFull() {
        val items = (0 until 2).flatMap { y -> (0 until 2).map { x -> app(0, x, y) } }
        assertEquals(Triple(1, 0, 0), GridLogic.findFreeCell(items, 2, 2, 2))
        assertNull(GridLogic.findFreeCell(items, 1, 2, 2))
    }

    @Test
    fun widgetSpanBlocksCells() {
        val items = listOf(widget(0, 0, 0, 2, 2))
        assertFalse(GridLogic.isFree(items, 0, 1, 1, 1, 1))
        assertTrue(GridLogic.isFree(items, 0, 2, 0, 1, 1))
        assertTrue(GridLogic.isFree(items, 1, 0, 0, 1, 1))
    }

    @Test
    fun stackChildrenDoNotOccupyCells() {
        val child = HomeItem(type = HomeItem.WIDGET, page = 0, x = 0, y = 0, parent = "stack")
        assertTrue(GridLogic.isFree(listOf(child), 0, 0, 0, 1, 1))
    }

    @Test
    fun oversizedWidgetNeverFits() {
        assertNull(GridLogic.findFreeCell(emptyList(), 1, 4, 4, 5, 1))
    }

    @Test
    fun clampKeepsItemInsideShrunkGrid() {
        val c = GridLogic.clamp(widget(0, 6, 7, 5, 5), 4, 4)
        assertEquals(4, c.spanX)
        assertEquals(4, c.spanY)
        assertEquals(0, c.x)
        assertEquals(0, c.y)
    }

    @Test
    fun placeWithOverlapPolicyKeepsBothAndReportsCollision() {
        val a = app(0, 1, 1)
        val mover = app(0, 1, 1)
        val (r, hit) = GridLogic.placeWithPolicy(listOf(a), mover, "overlap", 4)
        assertTrue(hit)
        assertEquals(2, r.size)
        assertEquals(mover.id, r.last().id)
    }

    @Test
    fun placeWithPushDownMovesExistingBelow() {
        val a = app(0, 1, 1)
        val mover = app(0, 1, 1)
        val (r, hit) = GridLogic.placeWithPolicy(listOf(a), mover, "push_down", 4)
        assertTrue(hit)
        assertEquals(2, r.first { it.id == a.id }.y)
        assertEquals(1, r.first { it.id == mover.id }.y)
    }

    @Test
    fun placeWithPushUpMovesExistingAbove() {
        val a = app(0, 1, 2)
        val mover = app(0, 1, 2)
        val (r, _) = GridLogic.placeWithPolicy(listOf(a), mover, "push_up", 4)
        assertEquals(1, r.first { it.id == a.id }.y)
    }

    @Test
    fun placeWithoutCollisionReportsFalse() {
        val (_, hit) = GridLogic.placeWithPolicy(listOf(app(0, 0, 0)), app(0, 2, 2), "push_down", 4)
        assertFalse(hit)
    }

    @Test
    fun freeItemsDoNotBlockCellsOrCollide() {
        val f = app(0, 1, 1).copy(free = true, fx = 1.3f, fy = 1.2f)
        assertTrue(GridLogic.isFree(listOf(f), 0, 1, 1, 1, 1))
        val (r, hit) = GridLogic.placeWithPolicy(listOf(f), app(0, 1, 1), "push_down", 4)
        assertFalse(hit)
        assertEquals(2, r.size)
    }

    @Test
    fun clampKeepsFreeItemInsideScreen() {
        val f = app(0, 0, 0).copy(free = true, fx = 9f, fy = -2f, fw = 2.5f, fh = 1.5f)
        val c = GridLogic.clamp(f, 4, 5)
        assertEquals(1.5f, c.fx, 0.001f)
        assertEquals(0f, c.fy, 0.001f)
    }

    @Test
    fun autoTidyResolvesOverlapsAndSnapsFreeItems() {
        val a = app(0, 1, 1)
        val b = app(0, 1, 1)
        val f = app(0, 0, 0).copy(free = true, fx = 2.4f, fy = 3.2f)
        val out = jp.achi.homelauncher.data.AutoLayout.tidy(listOf(a, b, f), 4, 5, 2)
        assertTrue(out.none { it.free })
        val cells = out.map { Triple(it.page, it.x, it.y) }
        assertEquals(cells.size, cells.toSet().size)
    }

    @Test
    fun autoFitSettingsStaysInRange() {
        val s = jp.achi.homelauncher.data.AutoLayout.fitSettings(jp.achi.homelauncher.data.LauncherSettings(), 90f, 110f)
        assertTrue(s.iconSizeDp in 32..96)
        assertTrue(s.dockHeightDp in 56..120)
    }

    @Test
    fun shiftPolicyInsertsAndPushesOthersBack() {
        // A B C が並ぶ行で、C を先頭へ持ってくると C A B になる
        val a = app(0, 0, 0)
        val b = app(0, 1, 0)
        val c = app(0, 2, 0)
        val moverC = c.copy(x = 0, y = 0)
        val (r, hit) = GridLogic.placeWithPolicy(listOf(a, b, c), moverC, "shift", 5, 4)
        assertTrue(hit)
        assertEquals(0, r.first { it.id == c.id }.x)
        assertEquals(1, r.first { it.id == a.id }.x)
        assertEquals(2, r.first { it.id == b.id }.x)
    }

    @Test
    fun shiftPolicyWrapsToNextRow() {
        val row = (0 until 4).map { app(0, it, 0) }
        val mover = app(0, 0, 0)
        val (r, _) = GridLogic.placeWithPolicy(row, mover, "shift", 5, 4)
        val last = r.first { it.id == row[3].id }
        assertEquals(0, last.x)
        assertEquals(1, last.y)
    }

    @Test
    fun shiftFallsBackWhenBlockedByWidget() {
        val w = widget(0, 1, 0, 2, 1)
        val a = app(0, 0, 0)
        val (r, hit) = GridLogic.placeWithPolicy(listOf(a, w), app(0, 0, 0), "shift", 5, 4)
        assertTrue(hit)
        assertEquals(2, r.size)
    }

    @Test
    fun heartShapeIsKnownSetting() {
        val o = org.json.JSONObject().put("iconShape", "heart")
        assertEquals("heart", jp.achi.homelauncher.data.LauncherSettings.fromJson(o).iconShape)
    }

    @Test
    fun workAppKeyDiffersFromPersonalKey() {
        val p = jp.achi.homelauncher.data.AppInfo("X", "a.b", "C", 0L)
        val w = p.copy(work = true, userSerial = 10L)
        assertTrue(p.key != w.key)
    }
}
