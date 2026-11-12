package jp.achi.homelauncher

import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppSorter
import jp.achi.homelauncher.data.Kana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaTest {
    @Test
    fun hiraganaMatchesKatakana() {
        assertTrue(Kana.matches("グーグル マップ", "ぐーぐる"))
        assertTrue(Kana.matches("カメラ", "かめ"))
        assertFalse(Kana.matches("カメラ", "ちず"))
    }

    @Test
    fun hiraganaMatchesCommonKanji() {
        assertTrue(Kana.matches("電話", "でんわ"))
        assertTrue(Kana.matches("設定", "せってい"))
        assertTrue(Kana.matches("天気予報", "てんき"))
    }

    @Test
    fun fullWidthAndCaseAreIgnored() {
        assertTrue(Kana.matches("Chrome", "CHRO"))
        assertTrue(Kana.matches("ＬＩＮＥ", "line"))
    }

    @Test
    fun customYomiIsUsedForUnknownKanji() {
        val apps = listOf(AppInfo("魑魅", "p.a", "A", 0L))
        assertEquals(0, AppSorter.filter(apps, "ちみ").size)
        assertEquals(1, AppSorter.filter(apps, "ちみ", mapOf("p.a/A" to "ちみもうりょう")).size)
    }
}
