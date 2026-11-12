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
            val shown = folder.apps.mapNotNull { appMap[it] }.take(9)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in 0 until 3) {
                            val app = shown.getOrNull(r * 3 + c)
                            if (app != null) AppIcon(app, mini, Modifier.alpha(iconAlpha))
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = if (onWallpaper) Shadow(Color.Black, blurRadius = 6f) else null),
                modifier = Modifier.padding(top = 2.dp),
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
    onLaunch: (AppInfo) -> Unit,
    onPutOnHome: (AppInfo) -> Unit,
    onRemoveFromFolder: (AppInfo) -> Unit,
    onAddApp: (AppInfo) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    val members = folder.apps.mapNotNull { appMap[it] }
    val ink = MaterialTheme.colorScheme.onSurface

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                folder.name,
                color = ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showRename = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            if (members.isEmpty()) {
                Text(
                    "アプリがありません。下の「アプリを追加」から入れてください。",
                    color = ink,
                    modifier = Modifier.padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 380.dp).padding(top = 10.dp),
                ) {
                    items(members, key = { it.key }) { app ->
                        AppCell(
                            app = app,
                            iconSizeDp = iconSizeDp,
                            showLabel = true,
                            labelSizeSp = labelSizeSp,
                            onWallpaper = false,
                            modifier = Modifier.padding(vertical = 8.dp),
                            onClick = { onLaunch(app) },
                            onLongClick = { menuApp = app },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { showPicker = true }) { Text("アプリを追加") }
                TextButton(onClick = onDelete) { Text("フォルダ削除") }
                TextButton(onClick = onDismiss) { Text("閉じる") }
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

    menuApp?.let { app ->
        MenuDialog(
            title = app.label,
            entries = listOf(
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
