package jp.achi.homelauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** メディアボタンの種類 */
enum class MediaGlyphKind { PREV, PLAY, PAUSE, NEXT }

/**
 * 文字ではなく図形で描く、角のまるいメディアボタン(▶ ⏸ ⏮ ⏭)。
 * 角を丸めた三角と棒だけで、どのフォントでもきれいに表示される。
 */
@Composable
fun MediaGlyph(kind: MediaGlyphKind, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = w * 0.09f
        val stroke = Stroke(width = r * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun tri(left: Float, right: Float, forward: Boolean): Path = Path().apply {
            val top = h * 0.24f + r
            val bottom = h * 0.76f - r
            val l = left * w + r
            val rr = right * w - r
            if (forward) {
                moveTo(l, top); lineTo(rr, h / 2f); lineTo(l, bottom)
            } else {
                moveTo(rr, top); lineTo(l, h / 2f); lineTo(rr, bottom)
            }
            close()
        }
        fun drawTri(p: Path) {
            drawPath(p, color)
            drawPath(p, color, style = stroke)
        }
        when (kind) {
            MediaGlyphKind.PLAY -> drawTri(tri(0.28f, 0.80f, true))
            MediaGlyphKind.PAUSE -> {
                val bw = w * 0.16f
                val bh = h * 0.56f
                val top = (h - bh) / 2f
                drawRoundRect(color, Offset(w * 0.28f, top), Size(bw, bh), CornerRadius(bw / 2f))
                drawRoundRect(color, Offset(w * 0.72f - bw, top), Size(bw, bh), CornerRadius(bw / 2f))
            }
            MediaGlyphKind.NEXT -> {
                drawTri(tri(0.18f, 0.70f, true))
                val bw = w * 0.1f
                val bh = h * 0.5f
                drawRoundRect(color, Offset(w * 0.76f, (h - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f))
            }
            MediaGlyphKind.PREV -> {
                drawTri(tri(0.30f, 0.82f, false))
                val bw = w * 0.1f
                val bh = h * 0.5f
                drawRoundRect(color, Offset(w * 0.14f, (h - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f))
            }
        }
    }
}
