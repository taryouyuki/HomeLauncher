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
    "wobbly_soft" to "ゆるい丸(丸に近い)",
    "flower" to "花形",
    "pentagon" to "五角形",
    "hexagon" to "六角形",
    "octagon" to "八角形",
    "heart" to "ハート",
    "heart_round" to "ふっくらハート",
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
private val WOBBLY_SOFT = GenericShape { size, _ ->
    addPath(polar(size, 120) { t -> 0.96 + 0.015 * sin(3 * t + 0.6) + 0.012 * cos(5 * t + 1.3) })
}
private val FLOWER = GenericShape { size, _ ->
    addPath(polar(size, 160) { t -> 0.9 + 0.08 * cos(8 * t) })
}

/** やわらかいハート(媒介変数曲線)。アイコンが隠れすぎないよう、箱いっぱいに広げて描く。 */
/** 手描き風のやわらかいハート。左右をわざと少し非対称にして、先端も丸める。 */
private val HEART = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    fun x(v: Float) = v * w
    fun y(v: Float) = v * h
    moveTo(x(0.50f), y(0.30f))
    // 左のふくらみ
    cubicTo(x(0.44f), y(0.14f), x(0.28f), y(0.07f), x(0.16f), y(0.14f))
    cubicTo(x(0.03f), y(0.22f), x(0.02f), y(0.42f), x(0.11f), y(0.56f))
    // 左下 → 先端(丸め)
    cubicTo(x(0.20f), y(0.70f), x(0.38f), y(0.82f), x(0.47f), y(0.92f))
    cubicTo(x(0.49f), y(0.95f), x(0.53f), y(0.95f), x(0.56f), y(0.92f))
    // 右下 → 右のふくらみ(少し大きめ)
    cubicTo(x(0.67f), y(0.81f), x(0.84f), y(0.69f), x(0.91f), y(0.55f))
    cubicTo(x(1.00f), y(0.39f), x(0.97f), y(0.19f), x(0.84f), y(0.12f))
    cubicTo(x(0.71f), y(0.05f), x(0.55f), y(0.13f), x(0.50f), y(0.30f))
    close()
}

/** 丸みを強くした、ふっくらしたハート(左右対称・先端も丸い)。 */
private val HEART_ROUND = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    fun x(v: Float) = v * w
    fun y(v: Float) = v * h
    moveTo(x(0.50f), y(0.28f))
    cubicTo(x(0.44f), y(0.06f), x(0.12f), y(0.02f), x(0.05f), y(0.28f))
    cubicTo(x(0.00f), y(0.46f), x(0.12f), y(0.62f), x(0.30f), y(0.77f))
    cubicTo(x(0.40f), y(0.85f), x(0.45f), y(0.90f), x(0.47f), y(0.93f))
    cubicTo(x(0.49f), y(0.96f), x(0.51f), y(0.96f), x(0.53f), y(0.93f))
    cubicTo(x(0.55f), y(0.90f), x(0.60f), y(0.85f), x(0.70f), y(0.77f))
    cubicTo(x(0.88f), y(0.62f), x(1.00f), y(0.46f), x(0.95f), y(0.28f))
    cubicTo(x(0.88f), y(0.02f), x(0.56f), y(0.06f), x(0.50f), y(0.28f))
    close()
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
    "heart" -> HEART
    "heart_round" -> HEART_ROUND
    "wobbly_soft" -> WOBBLY_SOFT
    else -> null
}
