package jp.achi.homelauncher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun IntSliderRow(label: String, value: Int, min: Int, max: Int, unit: String = "", onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("$value$unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (max > min) {
            Slider(
                value = value.coerceIn(min, max).toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(min, max)) },
                valueRange = min.toFloat()..max.toFloat(),
                steps = max - min - 1,
            )
        }
    }
}

/** 0.0〜1.0 を % 表示で操作するスライダー。 */
@Composable
fun PercentSliderRow(label: String, value: Float, min: Float = 0f, max: Float = 1f, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { onChange((it * 100).roundToInt() / 100f) },
            valueRange = min..max,
        )
    }
}

fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun openWebSearch(context: Context, searchUrl: String, query: String) {
    val q = query.trim()
    if (q.isEmpty()) return
    runCatching {
        val url = searchUrl.replace("%s", Uri.encode(q))
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast(context, "ブラウザを開けません") }
}
