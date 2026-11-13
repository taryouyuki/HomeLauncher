@file:OptIn(ExperimentalFoundationApi::class)

package jp.achi.homelauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.HomeItem
import jp.achi.homelauncher.data.AppSorter
import jp.achi.homelauncher.data.Folder
import jp.achi.homelauncher.data.SortMode

/** ホーム/ドック上のフォルダアイコン。やわらかい半透明の角丸の中に、中のアプリを3×3まで並べて表示する。 */
@Composable
fun FolderTile(
    folder: Folder,
    appMap: Map<String, AppInfo>,
    iconSizeDp: Int,
    showLabel: Boolean,
    labelSizeSp: Int,
    iconAlpha: Float,
    onWallpaper: Boolean,
    modifier: Modifier = Modifier,
    labelLines: Int = 1,
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
        val corner = (iconSizeDp * 0.30f).dp
        Box(
            Modifier
                .size(iconSizeDp.dp)
                .clip(RoundedCornerShape(corner))
                .background(Color.White.copy(alpha = 0.32f * iconAlpha))
                .border(1.dp, Color.White.copy(alpha = 0.45f * iconAlpha), RoundedCornerShape(corner))
                .padding((iconSizeDp * 0.09f).dp),
            contentAlignment = Alignment.Center,
        ) {
            val mini = (iconSizeDp * 0.25f).toInt().coerceAtLeast(10)
            val shown = folder.apps.filter { it.startsWith("sc:") || appMap[it] != null }.take(9)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in 0 until 3) {
                            val e = shown.getOrNull(r * 3 + c)
                            val app = e?.let { appMap[it] }
                            if (app != null) AppIcon(app, mini, Modifier.alpha(iconAlpha))
                            else if (e != null && e.startsWith("sc:")) PackageIcon(e.removePrefix("sc:").substringBefore('|'), mini)
                            else Spacer(Modifier.size(mini.dp))
                        }
                    }
                }
            }
        }
        if (showLabel) {
            Text(
                text = folder.name,
                color = if (onWallpaper) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = labelSizeSp.sp,
                maxLines = labelLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = LocalAppFont.current, shadow = if (onWallpaper) Shadow(Color.Black, blurRadius = 6f) else null),
                modifier = Modifier.padding(top = 2.dp).heightIn(min = with(androidx.compose.ui.platform.LocalDensity.current) { ((labelSizeSp + 2).sp * labelLines).toDp() }),
            )
        }
    }
}

/**
 * フォルダを開いた画面。画面中央の大きな丸い枠に、フォルダ名と、大きめのアイコンを4列で表示する。
 * 名前をタップで変更、アイコン長押しで「ホームに出す/外す」、下のボタンでアプリ追加・フォルダ削除。
 */
@Composable
fun FolderDialog(
    folder: Folder,
    appMap: Map<String, AppInfo>,
    allApps: List<AppInfo>,
    iconSizeDp: Int,
    labelSizeSp: Int,
    labelLines: Int = 2,
    cols: Int = 4,
    bgColor: Color? = null,
    bgAlpha: Float = 0.9f,
    onLaunch: (AppInfo) -> Unit,
    onPutOnHome: (AppInfo) -> Unit,
    onRemoveFromFolder: (AppInfo) -> Unit,
    onLaunchShortcut: (pkg: String, id: String) -> Unit = { _, _ -> },
    onRemoveEntry: (String) -> Unit = {},
    dtapMap: Map<String, String> = emptyMap(),
    onDtap: (String) -> Unit = {},
    onSetDtap: (String) -> Unit = {},
    onAddApp: (AppInfo) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    val entries = folder.apps.filter { it.startsWith("sc:") || appMap[it] != null }
    var menuSc by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val baseBg = bgColor ?: androidx.compose.ui.graphics.lerp(cs.primary, Color.Black, 0.68f)
    val panel = baseBg.copy(alpha = bgAlpha)
    val pill = androidx.compose.ui.graphics.lerp(baseBg, Color.Black, 0.35f).copy(alpha = (bgAlpha + 0.05f).coerceAtMost(1f))
    var menuOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxWidth(0.9f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 参考画像のような、角が大きく丸い半透明のパネル(右下だけさらに大きく丸い)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 72.dp, bottomStart = 36.dp))
                    .background(panel)
                    .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 22.dp),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        "アプリがありません。下の「⋮」→「アプリを追加」から入れてください。",
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols.coerceIn(3, 5)),
                        modifier = Modifier.heightIn(max = 400.dp),
                    ) {
                        items(entries, key = { it }) { e ->
                            val app = appMap[e]
                            if (app == null) {
                                val parts = e.removePrefix("sc:").split('|', limit = 3)
                                val pkg = parts.getOrElse(0) { "" }
                                val sid = parts.getOrElse(1) { "" }
                                val label = parts.getOrElse(2) { "" }
                                ShortcutCell(
                                    item = HomeItem(type = HomeItem.SHORTCUT, page = 0, x = 0, y = 0, pkg = pkg, cls = sid, ref = label),
                                    iconSizeDp = minOf(iconSizeDp, if (cols >= 5) 48 else if (cols == 4) 56 else 72),
                                    showLabel = true,
                                    labelSizeSp = labelSizeSp,
                                    iconAlpha = 1f,
                                    labelLines = labelLines,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    onClick = { onLaunchShortcut(pkg, sid) },
                                    onLongClick = { menuSc = e },
                                    onDoubleClick = dtapMap["fd:$e"]?.takeIf { it.isNotEmpty() }?.let { code -> { onDtap(code) } },
                                )
                            } else AppCell(
                                app = app,
                                iconSizeDp = minOf(iconSizeDp, if (cols >= 5) 48 else if (cols == 4) 56 else 72),
                                showLabel = true,
                                labelSizeSp = labelSizeSp,
                                onWallpaper = true,
                                labelLines = labelLines,
                                modifier = Modifier.padding(vertical = 8.dp),
                                onClick = { onLaunch(app) },
                                onLongClick = { menuApp = app },
                                onDoubleClick = dtapMap["fd:${app.key}"]?.takeIf { it.isNotEmpty() }?.let { code -> { onDtap(code) } },
                            )
                        }
                    }
                }
            }
            // フォルダ名のピル(タップで名前変更)+ ⋮ メニュー
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(pill),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    folder.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showRename = true }
                        .padding(start = 40.dp, top = 14.dp, bottom = 14.dp),
                )
                Box {
                    Text(
                        "⋮",
                        color = Color.White,
                        fontSize = 22.sp,
                        modifier = Modifier.clickable { menuOpen = true }.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                    androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("名前を変更") }, onClick = { menuOpen = false; showRename = true })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("アプリを追加") }, onClick = { menuOpen = false; showPicker = true })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("フォルダ削除") }, onClick = { menuOpen = false; onDelete() })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("閉じる") }, onClick = { menuOpen = false; onDismiss() })
                    }
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            apps = AppSorter.sort(allApps, SortMode.NAME, emptyMap()),
            onPick = {
                showPicker = false
                onAddApp(it)
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showRename) {
        TextInputDialog(
            title = "フォルダ名",
            initial = folder.name,
            onConfirm = {
                showRename = false
                onRename(it)
            },
            onDismiss = { showRename = false },
        )
    }

    menuSc?.let { e ->
        MenuDialog(
            title = e.removePrefix("sc:").split('|', limit = 3).getOrElse(2) { "ショートカット" },
            entries = listOf(
                "ダブルタップ設定" + (if (dtapMap["fd:$e"].isNullOrEmpty()) "" else "(設定済)") to {
                    menuSc = null
                    onSetDtap(e)
                },
                "フォルダから外す" to {
                    menuSc = null
                    onRemoveEntry(e)
                },
            ),
            onDismiss = { menuSc = null },
        )
    }

    menuApp?.let { app ->
        MenuDialog(
            title = app.label,
            entries = listOf(
                "ダブルタップ設定" + (if (dtapMap["fd:${app.key}"].isNullOrEmpty()) "" else "(設定済)") to {
                    menuApp = null
                    onSetDtap(app.key)
                },
                "ホーム画面に出す" to {
                    menuApp = null
                    onPutOnHome(app)
                },
                "フォルダから外す" to {
                    menuApp = null
                    onRemoveFromFolder(app)
                },
            ),
            onDismiss = { menuApp = null },
        )
    }
}

/** アプリを入れるフォルダを選ぶ。 */
@Composable
fun FolderPickDialog(folders: List<Folder>, onPick: (Folder) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text("フォルダを選択") },
        text = {
            if (folders.isEmpty()) {
                Text("フォルダがありません。ホーム画面の空き地を長押しして「フォルダを作成」してください。")
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(folders, key = { it.id }) { f ->
                        Text(
                            "${f.name}(${f.apps.size})",
                            modifier = Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
    )
}

/** 1行テキスト入力ダイアログ。 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
