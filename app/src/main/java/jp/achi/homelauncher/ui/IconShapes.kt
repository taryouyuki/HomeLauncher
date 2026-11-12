package jp.achi.homelauncher.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** アイコンの形(設定)。original は各アプリのアイコンをそのまま表示する。 */
val LocalIconShape = staticCompositionLocalOf { "original" }

val ICON_SHAPE_LABELS: List<Pair<String, String>> = listOf(
    "original" to "そのまま",
    "circle" to "丸",
    "squircle" to "丸みの四角",
    "rounded" to "角丸",
    "wobbly" to "歪んだ丸",
    "flower" to "花形",
    "pentagon" to "五角形",
    "hexagon" to "六角形",
    "octagon" to "八角形",
)

private fun polar(size: Size, steps: Int, r: (Double) -> Double): Path = Path().apply {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val base = min(size.width, size.height) / 2f
    for (i in 0..steps) {
        val t = 2.0 * PI * i / steps
        val rr = base * r(t).toFloat()
        val x = cx + rr * cos(t).toFloat()
        val y = cy + rr * sin(t).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun polygon(n: Int) = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val base = min(size.width, size.height) / 2f * 0.98f
    for (k in 0 until n) {
        val t = -PI / 2 + 2.0 * PI * k / n
        val x = cx + base * cos(t).toFloat()
        val y = cy + base * sin(t).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val WOBBLY = GenericShape { size, _ ->
    addPath(polar(size, 120) { t -> 0.92 + 0.04 * sin(3 * t + 0.6) + 0.03 * cos(5 * t + 1.3) })
}
private val FLOWER = GenericShape { size, _ ->
    addPath(polar(size, 160) { t -> 0.9 + 0.08 * cos(8 * t) })
}

/** 形のキーから Shape を返す。original(や不明な値)は null。 */
fun iconShapeOf(key: String): Shape? = when (key) {
    "circle" -> CircleShape
    "squircle" -> RoundedCornerShape(percent = 32)
    "rounded" -> RoundedCornerShape(percent = 18)
    "wobbly" -> WOBBLY
    "flower" -> FLOWER
    "pentagon" -> polygon(5)
    "hexagon" -> polygon(6)
    "octagon" -> polygon(8)
    else -> null
}
