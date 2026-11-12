package jp.achi.homelauncher

import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppSorter
import jp.achi.homelauncher.data.SortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSorterTest {
    private fun app(label: String, updated: Long) = AppInfo(label, "pkg.$label", "Main", updated)

    private val apps = listOf(
        app("Chrome", 300),
        app("Camera", 100),
        app("Maps", 200),
    )

    @Test
    fun sortByName() {
        val r = AppSorter.sort(apps, SortMode.NAME, emptyMap()).map { it.label }
        assertEquals(listOf("Camera", "Chrome", "Maps"), r)
    }

    @Test
    fun sortByUpdatedNewestFirst() {
        val r = AppSorter.sort(apps, SortMode.UPDATED, emptyMap()).map { it.label }
        assertEquals(listOf("Chrome", "Maps", "Camera"), r)
    }

    @Test
    fun sortByLaunchCountDescendingWithNameTieBreak() {
        val counts = mapOf("pkg.Maps/Main" to 5, "pkg.Chrome/Main" to 5, "pkg.Camera/Main" to 1)
        val r = AppSorter.sort(apps, SortMode.FREQUENT, counts).map { it.label }
        assertEquals(listOf("Chrome", "Maps", "Camera"), r)
    }

    @Test
    fun sortByLastLaunchNewestFirstAndNeverLaunchedLast() {
        val last = mapOf("pkg.Maps/Main" to 900L, "pkg.Camera/Main" to 500L)
        val r = AppSorter.sort(apps, SortMode.LAST_LAUNCH, emptyMap(), last).map { it.label }
        assertEquals(listOf("Maps", "Camera", "Chrome"), r)
    }

    @Test
    fun sortByInstallNewestFirst() {
        val a = listOf(
            AppInfo("A", "pkg.A", "Main", 0L, 10L),
            AppInfo("B", "pkg.B", "Main", 0L, 30L),
            AppInfo("C", "pkg.C", "Main", 0L, 20L),
        )
        val r = AppSorter.sort(a, SortMode.INSTALL, emptyMap()).map { it.label }
        assertEquals(listOf("B", "C", "A"), r)
    }

    @Test
    fun filterPartialMatchIgnoresCase() {
        assertEquals(listOf("Chrome", "Camera"), AppSorter.filter(apps, "c").filter { it.label.startsWith("C") }.map { it.label })
        assertEquals(listOf("Maps"), AppSorter.filter(apps, "AP").map { it.label })
        assertEquals(3, AppSorter.filter(apps, "  ").size)
        assertEquals(0, AppSorter.filter(apps, "zzz").size)
    }
}
