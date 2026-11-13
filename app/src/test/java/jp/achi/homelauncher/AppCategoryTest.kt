package jp.achi.homelauncher

import jp.achi.homelauncher.data.AppCategory
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppSorter
import jp.achi.homelauncher.data.SortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryTest {
    private fun a(label: String, pkg: String, game: Boolean = false) =
        AppInfo(label = label, packageName = pkg, className = "$pkg.Main", updatedAt = 0L, isGame = game)

    @Test
    fun autoDetectsGame() {
        assertEquals("ゲーム", AppCategory.auto(a("Foo", "com.foo", game = true)))
    }

    @Test
    fun manualOverridesAuto() {
        val app = a("Foo", "com.foo", game = true)
        assertEquals("自作", AppCategory.of(app, mapOf(app.key to "自作")))
    }

    @Test
    fun categorySortGroupsByOrder() {
        val g = a("Zgame", "com.z", game = true)
        val t = a("Calc", "com.calc.app")
        val sorted = AppSorter.sort(listOf(t, g), SortMode.CATEGORY, emptyMap())
        assertEquals(listOf(g.key, t.key), sorted.map { it.key })
    }
}
