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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.draw.alpha
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

/** アイコンのビットマップキャッシュ。flat=形を変えるときの、マスクなし(全面)のアイコン。 */
object IconCache {
    private val cache = LruCache<String, ImageBitmap>(400)
    private const val SIZE_PX = 192

    /** アプリのアイコン。仕事用アプリは LauncherApps から(仕事用バッジ付きで)取得する。 */
    private fun drawableOf(context: Context, app: AppInfo): Drawable? = runCatching {
        if (app.work) {
            val la = context.getSystemService(android.content.pm.LauncherApps::class.java)
            val um = context.getSystemService(android.os.UserManager::class.java)
            val user = um.getUserForSerialNumber(app.userSerial)
            val i = la.getActivityList(app.packageName, user).firstOrNull { it.componentName.className == app.className }
            i?.getBadgedIcon(0)
        } else {
            context.packageManager.getActivityIcon(android.content.ComponentName(app.packageName, app.className))
        }
    }.getOrNull()

    fun get(key: String): ImageBitmap? = cache.get(key)

    /** 形を指定するとき用。アダプティブアイコンの背景と前景を、標準マスクなしで全面に描く。 */
    fun loadFlat(context: Context, app: AppInfo): ImageBitmap? {
        val ck = "f:" + app.key
        cache.get(ck)?.let { return it }
        val drawable: Drawable = drawableOf(context, app) ?: return null
        val bmp = runCatching {
            val s = SIZE_PX
            val b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
                val pad = s / 4 // レイヤーは108dp、見える範囲は中央72dp → 1.5倍に広げて見える範囲をちょうど s にする
                drawable.background?.apply { setBounds(-pad, -pad, s + pad, s + pad); draw(c) }
                drawable.foreground?.apply { setBounds(-pad, -pad, s + pad, s + pad); draw(c) }
            } else {
                val inset = s / 12
                drawable.setBounds(inset, inset, s - inset, s - inset)
                drawable.draw(c)
            }
            b.asImageBitmap()
        }.getOrNull() ?: return null
        cache.put(ck, bmp)
        return bmp
    }

    /**
     * テーマアイコン。モノクロ版アイコン(Android 13+ のアダプティブアイコン)を、テーマ色の背景に前景色で描く。
     * モノクロ版が無いアプリは通常のアイコンのまま。
     */
    fun loadThemed(context: Context, app: AppInfo, bg: Int, fg: Int): ImageBitmap? {
        val ck = "t:$bg:$fg:${app.key}"
        cache.get(ck)?.let { return it }
        val drawable: Drawable = drawableOf(context, app) ?: return null
        val mono = if (android.os.Build.VERSION.SDK_INT >= 33 && drawable is android.graphics.drawable.AdaptiveIconDrawable) {
            drawable.monochrome
        } else null
        if (mono == null) return loadFlat(context, app)?.also { cache.put(ck, it) }
        val bmp = runCatching {
            val s = SIZE_PX
            val b = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            c.drawColor(bg)
            val pad = s / 4
            mono.mutate().apply { setTint(fg); setBounds(-pad, -pad, s + pad, s + pad); draw(c) }
            b.asImageBitmap()
        }.getOrNull() ?: return null
        cache.put(ck, bmp)
        return bmp
    }

    fun load(context: Context, app: AppInfo): ImageBitmap? {
        cache.get(app.key)?.let { return it }
        val drawable: Drawable = drawableOf(context, app) ?: return null
        val bmp = runCatching { drawable.toBitmap(SIZE_PX, SIZE_PX).asImageBitmap() }.getOrNull() ?: return null
        cache.put(app.key, bmp)
        return bmp
    }
}

@Composable
fun AppIcon(app: AppInfo, sizeDp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tint = LocalIconTint.current
    // テーマアイコンは全面に描くので、形が「そのまま」のときは角丸四角で切り抜く
    val shape = iconShapeOf(LocalIconShape.current) ?: if (tint != null) RoundedCornerShape(percent = 28) else null
    val cacheKey = when {
        tint != null -> "t:${tint.first}:${tint.second}:${app.key}"
        shape != null -> "f:" + app.key
        else -> app.key
    }
    val bitmap by produceState(initialValue = IconCache.get(cacheKey), cacheKey) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                when {
                    tint != null -> IconCache.loadThemed(context, app, tint.first, tint.second)
                    shape != null -> IconCache.loadFlat(context, app)
                    else -> IconCache.load(context, app)
                }
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = app.label,
            modifier = modifier.size(sizeDp.dp).let { if (shape != null) it.clip(shape) else it },
        )
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
    iconAlpha: Float = 1f,
    subLabel: String? = null,
    labelColor: Color? = null,
    badge: Int = 0,
    onDoubleClick: (() -> Unit)? = null,
    labelLines: Int = 1,
) {
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AppIcon(app, iconSizeDp, Modifier.alpha(iconAlpha))
            if (badge > 0) BadgeDot(badge, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
        }
        if (showLabel) {
            val color = labelColor ?: if (onWallpaper) Color.White else MaterialTheme.colorScheme.onSurface
            val shadow = if (onWallpaper) Shadow(Color.Black, blurRadius = 6f) else null
            Text(
                text = app.label,
                color = color,
                fontSize = labelSizeSp.sp,
                maxLines = labelLines,
                lineHeight = (labelSizeSp + 2).sp,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = LocalAppFont.current, shadow = shadow),
                modifier = Modifier.padding(top = 2.dp).heightIn(min = with(androidx.compose.ui.platform.LocalDensity.current) { ((labelSizeSp + 2).sp * labelLines).toDp() }),
            )
        }
        if (subLabel != null) {
            Text(
                text = subLabel,
                color = if (onWallpaper) Color.White else MaterialTheme.colorScheme.outline,
                fontSize = (labelSizeSp - 2).coerceAtLeast(8).sp,
                maxLines = 1,
            )
        }
    }
}

/** 通知の数を表す赤い丸。 */
@Composable
fun BadgeDot(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
            .clip(CircleShape)
            .background(Color(0xFFE53935))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
        )
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
