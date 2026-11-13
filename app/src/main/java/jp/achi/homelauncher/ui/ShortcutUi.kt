@file:OptIn(ExperimentalFoundationApi::class)

package jp.achi.homelauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import jp.achi.homelauncher.data.HomeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ブラウザ等が requestPinShortcut で追加したピン留めショートカットの起動・アイコン取得。 */
object ShortcutHelper {
    fun launch(context: Context, pkg: String, id: String): Boolean = runCatching {
        val la = context.getSystemService(LauncherApps::class.java) ?: return false
        la.startShortcut(pkg, id, null, null, Process.myUserHandle())
        true
    }.getOrDefault(false)

    /** 指定アプリのショートカット(ID, 表示名)の一覧。デフォルトのホームでないと空になる。 */
    fun list(context: Context, pkg: String): List<Pair<String, String>> = runCatching {
        val la = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        if (!la.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(pkg)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        la.getShortcuts(q, Process.myUserHandle()).orEmpty()
            .filter { it.isEnabled }
            .map { it.id to (it.shortLabel?.toString() ?: it.id) }
    }.getOrDefault(emptyList())

    /** ショートカット固有のアイコン。取得できなければ提供元アプリのアイコン。 */
    fun icon(context: Context, pkg: String, id: String): ImageBitmap? {
        val own = runCatching {
            val la = context.getSystemService(LauncherApps::class.java)
            val query = LauncherApps.ShortcutQuery()
                .setPackage(pkg)
                .setShortcutIds(listOf(id))
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
                )
            val info = la?.getShortcuts(query, Process.myUserHandle())?.firstOrNull()
            info?.let { la.getShortcutIconDrawable(it, context.resources.displayMetrics.densityDpi) }
                ?.toBitmap(192, 192)?.asImageBitmap()
        }.getOrNull()
        return own ?: runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192).asImageBitmap()
        }.getOrNull()
    }
}

@Composable
fun ShortcutCell(
    item: HomeItem,
    iconSizeDp: Int,
    showLabel: Boolean,
    labelSizeSp: Int,
    iconAlpha: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    labelLines: Int = 1,
) {
    val shape = iconShapeOf(LocalIconShape.current)
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, item.pkg, item.cls) {
        value = withContext(Dispatchers.Default) { ShortcutHelper.icon(context, item.pkg, item.cls) }
    }
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(bitmap = b, contentDescription = item.ref, modifier = Modifier.size(iconSizeDp.dp).alpha(iconAlpha).let { if (shape != null) it.clip(shape) else it })
        } else {
            Spacer(Modifier.size(iconSizeDp.dp))
        }
        if (showLabel) {
            Text(
                text = item.ref,
                color = Color.White,
                fontSize = labelSizeSp.sp,
                maxLines = labelLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = LocalAppFont.current, shadow = Shadow(Color.Black, blurRadius = 6f)),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
