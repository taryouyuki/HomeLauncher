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
}
