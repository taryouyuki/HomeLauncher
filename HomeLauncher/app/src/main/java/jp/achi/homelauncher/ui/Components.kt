package jp.achi.homelauncher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppSorter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** アイコンのビットマップキャッシュ。 */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(400)
    private const val SIZE_PX = 192

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun load(context: Context, app: AppInfo): ImageBitmap? {
        cache.get(app.key)?.let { return it }
        val drawable: Drawable = runCatching {
            context.packageManager.getActivityIcon(android.content.ComponentName(app.packageName, app.className))
        }.getOrNull() ?: return null
        val bmp = runCatching { drawable.toBitmap(SIZE_PX, SIZE_PX).asImageBitmap() }.getOrNull() ?: return null
        cache.put(app.key, bmp)
        return bmp
    }
}

@Composable
fun AppIcon(app: AppInfo, sizeDp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = IconCache.get(app.key), app.key) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { IconCache.load(context, app) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = app.label, modifier = modifier.size(sizeDp.dp))
    } else {
        Spacer(modifier.size(sizeDp.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: AppInfo,
    iconSizeDp: Int,
    showLabel: Boolean,
    labelSizeSp: Int,
    onWallpaper: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(app, iconSizeDp)
        if (showLabel) {
            val color = if (onWallpaper) Color.White else MaterialTheme.colorScheme.onSurface
            val shadow = if (onWallpaper) Shadow(Color.Black, blurRadius = 6f) else null
            Text(
                text = app.label,
                color = color,
                fontSize = labelSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = shadow),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 選択肢を並べるだけの簡易メニューダイアログ。 */
@Composable
fun MenuDialog(title: String, entries: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text(title) },
        text = {
            Column {
                entries.forEach { (label, action) ->
                    TextButton(
                        onClick = { action() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                }
            }
        },
    )
}

/** アプリを選ぶダイアログ(名前の部分一致で絞り込み可)。 */
@Composable
fun AppPickerDialog(apps: List<AppInfo>, onPick: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) { AppSorter.filter(apps, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text("アプリを選択") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("検索") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.key }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 6.dp),
                        ) {
                            AppIcon(app, 36)
                            Text(app.label, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}
