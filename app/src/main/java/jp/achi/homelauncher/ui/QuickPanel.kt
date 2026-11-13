package jp.achi.homelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.media.BadgeStore
import jp.achi.homelauncher.media.MediaHelper

/** クイックパネルの既定アプリ(入っているものだけ表示)。 */
val QUICK_DEFAULTS = listOf("com.twitter.android", "com.discord", "com.spotify.music")

/**
 * ホームで上から下へスワイプして出すパネル。アプリを開かずに、選んだアプリ(Twitter(X)・Discord など)の
 * 最新の通知と、再生中のメディア(Spotify など)の操作を見られる。タップでそのアプリ/通知を開く。
 */
@Composable
fun QuickPanel(
    apps: List<AppInfo>,
    quickApps: String,
    mediaState: MediaHelper.State,
    media: MediaHelper,
    onLaunch: (AppInfo) -> Unit,
    onChange: (String) -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onClose: () -> Unit,
) {
    val pkgs = remember(quickApps) {
        quickApps.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { QUICK_DEFAULTS }
    }
    val shown = pkgs.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
    var picking by remember { mutableStateOf(false) }
    val notifs by BadgeStore.history.collectAsState()
    var editing by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total < -100f) onClose() },
                )
            },
        color = cs.surface.copy(alpha = 0.97f),
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("クイックパネル", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "完了" else "編集") }
                TextButton(onClick = { BadgeStore.clearHistory() }) { Text("全クリア") }
                TextButton(onClick = onClose) { Text("閉じる") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // メディア(Spotify など)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(14.dp),
                ) {
                    Text(
                        if (!mediaState.permitted) "曲名を表示するには通知アクセスを許可"
                        else mediaState.title ?: if (mediaState.hasSession) "再生中のメディア" else "メディアなし",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (!mediaState.permitted) Modifier.clickable(onClick = onRequestNotificationAccess) else Modifier,
                    )
                    mediaState.artist?.let { Text(it, color = cs.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    val src = listOfNotNull(mediaState.appName, mediaState.output).joinToString(" · ")
                    if (src.isNotEmpty()) Text(src, color = cs.outline, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButtonBox({ media.previous() }) { MediaGlyph(MediaGlyphKind.PREV, cs.onSurface, 26.dp) }
                        // 再生/停止は丸い塗りのボタンにする
                        Box(
                            Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary).clickable { media.playPause() },
                            contentAlignment = Alignment.Center,
                        ) {
                            MediaGlyph(if (mediaState.playing) MediaGlyphKind.PAUSE else MediaGlyphKind.PLAY, cs.onPrimary, 28.dp)
                        }
                        IconButtonBox({ media.next() }) { MediaGlyph(MediaGlyphKind.NEXT, cs.onSurface, 26.dp) }
                    }
                }

                if (shown.isEmpty()) {
                    Text("表示するアプリがありません。「アプリを選ぶ」で追加してください。", color = cs.outline)
                }
                shown.forEach { app ->
                    val list = notifs[app.packageName].orEmpty().sortedByDescending { it.time }.take(4)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().clickable { onLaunch(app) }, verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app, 32)
                            Text(app.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                            val idx = pkgs.indexOf(app.packageName)
                            if (editing) TextButton(
                                enabled = idx > 0,
                                onClick = { onChange(pkgs.toMutableList().also { java.util.Collections.swap(it, idx, idx - 1) }.joinToString(",")) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                            ) { Text("↑") }
                            if (editing) TextButton(
                                enabled = idx in 0 until pkgs.size - 1,
                                onClick = { onChange(pkgs.toMutableList().also { java.util.Collections.swap(it, idx, idx + 1) }.joinToString(",")) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                            ) { Text("↓") }
                            if (list.isNotEmpty()) {
                                TextButton(onClick = { BadgeStore.clearHistory(app.packageName) }) { Text("クリア") }
                            }
                            if (editing) TextButton(onClick = { onChange(pkgs.filter { it != app.packageName }.joinToString(",")) }) { Text("外す") }
                        }
                        if (list.isEmpty()) {
                            Text("新しい通知はありません", color = cs.outline, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        list.forEach { n ->
                            var replying by remember(n.key) { mutableStateOf(false) }
                            var replyText by remember(n.key) { mutableStateOf("") }
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    // 通知の中身(ツイートやDMなど)を直接開く。開けなければアプリを開く
                                    val ok = runCatching { n.intent?.send() }.isSuccess && n.intent != null
                                    if (!ok) onLaunch(app)
                                    onClose()
                                }.padding(top = 8.dp),
                            ) {
                                if (n.title.isNotBlank()) Text(n.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (n.text.isNotBlank()) Text(n.text, fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (n.reply != null) {
                                if (!replying) {
                                    TextButton(onClick = { replying = true }) { Text("返信") }
                                } else {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.OutlinedTextField(
                                            value = replyText,
                                            onValueChange = { replyText = it },
                                            placeholder = { Text("返信を入力") },
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = {
                                            if (replyText.isNotBlank()) {
                                                val ok = BadgeStore.reply(context, n, replyText)
                                                toast(context, if (ok) "返信しました" else "返信できませんでした(通知が消えている可能性)")
                                                if (ok) {
                                                    replyText = ""
                                                    replying = false
                                                }
                                            }
                                        }) { Text("送信") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (notifs.isNotEmpty() && shown.any { notifs[it.packageName].isNullOrEmpty().not() }) {
                    Text("通知は、元の通知を消してもここに残ります(クリアで消去)。", color = cs.outline, fontSize = 12.sp)
                }
                if (editing) OutlinedButton(onClick = { picking = true }) { Text("アプリを追加") }
                Text(
                    "通知アクセスを許可すると、通知の内容がここに出ます。アカウントのログイン連携ではなく、端末に届いた通知を表示する仕組みです。",
                    color = cs.outline,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            apps = apps.sortedBy { it.label },
            onPick = {
                picking = false
                if (it.packageName !in pkgs) onChange((pkgs + it.packageName).joinToString(","))
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun IconButtonBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
