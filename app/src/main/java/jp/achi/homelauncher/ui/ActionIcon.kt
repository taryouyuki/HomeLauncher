package jp.achi.homelauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** ホームに置く操作アイコン(アプリ一覧を開く / クイックパネルを開く)。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionIconCell(
    kind: String,
    iconSizeDp: Int,
    showLabel: Boolean,
    labelSizeSp: Int,
    iconAlpha: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = iconShapeOf(LocalIconShape.current) ?: RoundedCornerShape(percent = 28)
    val fg = cs.onPrimaryContainer
    Column(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(iconSizeDp.dp).alpha(iconAlpha).clip(shape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size((iconSizeDp * 0.56f).dp)) {
                val w = size.width
                val h = size.height
                if (kind == "drawer") {
                    // 3×3 のドット
                    val r = w * 0.09f
                    for (i in 0..2) for (j in 0..2) {
                        drawCircle(fg, r, Offset(w * (0.16f + 0.34f * i), h * (0.16f + 0.34f * j)))
                    }
                } else {
                    // 上から下りてくるパネル
                    val sw = w * 0.09f
                    drawRoundRect(fg, Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f), CornerRadius(w * 0.14f), style = Stroke(sw))
                    drawLine(fg, Offset(w * 0.28f, h * 0.3f), Offset(w * 0.72f, h * 0.3f), sw, StrokeCap.Round)
                    drawLine(fg, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.72f, h * 0.5f), sw, StrokeCap.Round)
                    drawLine(fg, Offset(w * 0.28f, h * 0.7f), Offset(w * 0.5f, h * 0.7f), sw, StrokeCap.Round)
                }
            }
        }
        if (showLabel) {
            Text(
                if (kind == "drawer") "アプリ一覧" else "クイック",
                color = Color.White,
                fontSize = labelSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f)),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
