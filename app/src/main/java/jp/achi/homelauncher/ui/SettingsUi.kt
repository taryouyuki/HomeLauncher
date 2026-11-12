package jp.achi.homelauncher.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import jp.achi.homelauncher.data.LauncherSettings
import kotlin.math.min

private val SWATCHES = listOf(
    0xFFF48FB1, 0xFFE91E63, 0xFFFF8A65, 0xFFFFB74D, 0xFFFFF176, 0xFFAED581, 0xFF4DB6AC, 0xFF4FC3F7,
    0xFF7986CB, 0xFFBA68C8, 0xFFFFFFFF, 0xFFB0BEC5, 0xFF607D8B, 0xFF37474F, 0xFF212121, 0xFF000000,
).map { it.toInt() }

/** 色の選択行。タップでカラーピッカーを開く。 */
@Composable
fun ColorPickerRow(label: String, argb: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
    if (open) {
        ColorPickerDialog(argb, onPick = { onChange(it) }, onDismiss = { open = false })
    }
}

@Composable
private fun ColorPickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember {
        FloatArray(3).also { AColor.colorToHSV(initial, it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val current = AColor.HSVToColor(floatArrayOf(hue, sat, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("色を選択") },
        text = {
            Column {
                SWATCHES.chunked(8).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { c ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable {
                                        val t = FloatArray(3)
                                        AColor.colorToHSV(c, t)
                                        hue = t[0]
                                        sat = t[1]
                                        value = t[2]
                                    },
                            )
                        }
                    }
                }
                Text("色相", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("鮮やかさ", style = MaterialTheme.typography.bodySmall)
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                Text("明るさ", style = MaterialTheme.typography.bodySmall)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(current))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(current)
                onDismiss()
            }) { Text("決定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

private val ICON_COLORS = listOf(
    Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFF66BB6A),
    Color(0xFFFFCA28), Color(0xFFAB47BC), Color(0xFF26C6DA),
)

/**
 * 現在の設定を反映した、ホーム画面(またはアプリ一覧)の縮小プレビュー。
 * 壁紙は仮のグラデーション。座標は幅 360dp 基準で描いて縮尺する。
 */
@Composable
fun LauncherPreview(s: LauncherSettings, drawer: Boolean, modifier: Modifier = Modifier) {
    HomeColorTheme(s) {
        val cs = MaterialTheme.colorScheme
        val surface = cs.surface
        val shape = RoundedCornerShape(14.dp)
        val drawerBase = if (s.drawerBgMode == "custom") Color(s.drawerBgColor) else surface
        val drawerBg = drawerBase.copy(alpha = s.drawerBgAlpha)
        val drawerInk = if (drawerBase.luminance() > 0.5f) Color(0xFF222222) else Color.White
        val searchBg = surface.copy(alpha = s.searchBarAlpha)
        val mediaBg = cs.surfaceContainerHigh.copy(alpha = s.mediaBarAlpha)
        val dockBg = surface.copy(alpha = 0.55f)
        val ink = cs.onSurface
        val chipBg = cs.secondaryContainer
        val chipOn = cs.primaryContainer
        val fieldBg = cs.surfaceVariant

        Canvas(modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)) {
            val k = size.width / 360f
            val h = size.height / k

            fun box(x: Float, y: Float, w: Float, hh: Float, r: Float, c: Color) {
                drawRoundRect(c, Offset(x * k, y * k), Size(w * k, hh * k), CornerRadius(r * k, r * k))
            }

            // 仮の壁紙
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7FA1FF), Color(0xFFFFA6C9))))

            if (!drawer) {
                drawRect(Color.Black.copy(alpha = s.scrimAlpha))
                val top = 28f
                val dockH = s.dockHeightDp.toFloat()
                val dockTop = h - dockH - 20f
                val bottom = dockTop - 16f
                val cols = s.gridCols
                val rows = s.gridRows
                val cw = 360f / cols
                val ch = (bottom - top) / rows

                // 検索バー(1行目)とメディアバー(2行目)
                box(8f, top + 4f, 344f, ch - 8f, (ch - 8f) / 2f, searchBg)
                box(24f, top + ch / 2f - 4f, 90f, 8f, 4f, ink.copy(alpha = 0.5f))
                box(8f, top + ch + 4f, 344f, ch - 8f, 16f, mediaBg)
                box(24f, top + ch + ch * 0.28f, 130f, 9f, 4f, ink)
                box(24f, top + ch + ch * 0.28f + 14f, 80f, 6f, 3f, ink.copy(alpha = 0.6f))

                // アイコン
                for (r in 2 until rows) {
                    for (c in 0 until cols) {
                        val idx = r * cols + c
                        if (idx % 5 == 4) continue
                        val isz = min(s.iconSizeDp.toFloat(), cw - 8f)
                        val cx = c * cw + cw / 2f
                        val cy = top + r * ch + ch / 2f - if (s.showLabels) 5f else 0f
                        box(cx - isz / 2f, cy - isz / 2f, isz, isz, isz * 0.24f, ICON_COLORS[idx % ICON_COLORS.size].copy(alpha = s.iconAlpha))
                        if (s.showLabels) box(cx - isz * 0.4f, cy + isz / 2f + 3f, isz * 0.8f, 5f, 2.5f, Color.White.copy(alpha = 0.85f))
                    }
                }

                // ドック
                box(8f, dockTop, 344f, dockH, 28f, dockBg)
                val dsz = min(s.iconSizeDp.toFloat(), dockH - 8f).coerceAtLeast(10f)
                for (i in 0 until s.dockCount) {
                    val cx = 8f + (344f / s.dockCount) * (i + 0.5f)
                    box(cx - dsz / 2f, dockTop + (dockH - dsz) / 2f, dsz, dsz, dsz * 0.24f, ICON_COLORS[i % ICON_COLORS.size].copy(alpha = s.dockIconAlpha))
                }
            } else {
                drawRect(drawerBg)
                box(12f, 40f, 336f, 34f, 17f, fieldBg)
                for (i in 0 until 5) {
                    box(12f + i * 62f, 84f, 56f, 20f, 10f, if (i == 0) chipOn else chipBg)
                }
                val cols = s.drawerCols
                val cw = 360f / cols
                val isz = min(s.drawerIconSizeDp.toFloat(), cw - 8f)
                val rowH = isz + 22f
                var r = 0
                while (116f + (r + 1) * rowH < h) {
                    for (c in 0 until cols) {
                        val idx = r * cols + c
                        val cx = c * cw + cw / 2f
                        val cy = 116f + r * rowH + isz / 2f
                        box(cx - isz / 2f, cy - isz / 2f, isz, isz, isz * 0.24f, ICON_COLORS[idx % ICON_COLORS.size])
                        box(cx - isz * 0.4f, cy + isz / 2f + 4f, isz * 0.8f, 5f, 2.5f, drawerInk.copy(alpha = 0.8f))
                    }
                    r++
                }
            }
        }
    }
}
