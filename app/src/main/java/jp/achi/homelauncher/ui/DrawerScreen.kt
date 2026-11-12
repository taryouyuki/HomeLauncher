package jp.achi.homelauncher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.achi.homelauncher.LauncherViewModel
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppSorter
import jp.achi.homelauncher.data.PlaceResult
import jp.achi.homelauncher.data.SortMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 下から上スワイプで開くアプリ一覧。5種類の並び替えと部分一致検索に対応。 */
@Composable
fun DrawerScreen(vm: LauncherViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val lastLaunch by vm.lastLaunch.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var folderPickApp by remember { mutableStateOf<AppInfo?>(null) }
    val dateFmt = remember { SimpleDateFormat("yy/MM/dd", Locale.JAPAN) }
    val dateTimeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }

    val list = remember(apps, query, settings.drawerSort, counts, lastLaunch) {
        AppSorter.sort(AppSorter.filter(apps, query), settings.drawerSort, counts, lastLaunch)
    }

    val custom = settings.drawerBgMode == "custom"
    val baseColor = if (custom) Color(settings.drawerBgColor) else MaterialTheme.colorScheme.surface
    val bg = baseColor.copy(alpha = settings.drawerBgAlpha)
    // 好きな色の背景では、明るさに応じてアプリ名の色を切り替える
    val labelColor: Color? = if (custom) {
        if (baseColor.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White
    } else null

    Surface(modifier = Modifier.fillMaxSize(), color = bg) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            // 上部のつまみ: 下へドラッグで閉じる
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onVerticalDrag = { _, dy -> total += dy },
                            onDragEnd = { if (total > 100f) onClose() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline),
                )
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("アプリ名で検索") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    // 1件だけ一致していればそのまま起動
                    list.singleOrNull()?.let {
                        vm.launch(context, it)
                        onClose()
                    }
                }),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortChip("名前順", settings.drawerSort == SortMode.NAME) { vm.setDrawerSort(SortMode.NAME) }
                SortChip("最終起動順", settings.drawerSort == SortMode.LAST_LAUNCH) { vm.setDrawerSort(SortMode.LAST_LAUNCH) }
                SortChip("ダウンロード順", settings.drawerSort == SortMode.INSTALL) { vm.setDrawerSort(SortMode.INSTALL) }
                SortChip("よく使う順", settings.drawerSort == SortMode.FREQUENT) { vm.setDrawerSort(SortMode.FREQUENT) }
                SortChip("更新順", settings.drawerSort == SortMode.UPDATED) { vm.setDrawerSort(SortMode.UPDATED) }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.drawerCols),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            ) {
                items(list, key = { it.key }) { app ->
                    AppCell(
                        app = app,
                        iconSizeDp = settings.drawerIconSizeDp,
                        showLabel = true,
                        labelSizeSp = settings.labelSizeSp,
                        onWallpaper = false,
                        labelColor = labelColor,
                        subLabel = when (settings.drawerSort) {
                            SortMode.NAME -> null
                            SortMode.LAST_LAUNCH -> lastLaunch[app.key]?.let { dateTimeFmt.format(Date(it)) } ?: "未起動"
                            SortMode.INSTALL -> dateFmt.format(Date(app.installedAt))
                            SortMode.FREQUENT -> "${counts[app.key] ?: 0}回"
                            SortMode.UPDATED -> dateFmt.format(Date(app.updatedAt))
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                        onClick = {
                            vm.launch(context, app)
                            onClose()
                        },
                        onLongClick = {
                            if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuApp = app
                        },
                    )
                }
            }
        }
    }

    folderPickApp?.let { app ->
        FolderPickDialog(
            folders = folders,
            onPick = { folder ->
                folderPickApp = null
                vm.addToFolder(folder.id, app.key)
                toast(context, "「${folder.name}」に追加しました")
            },
            onDismiss = { folderPickApp = null },
        )
    }

    menuApp?.let { app ->
        MenuDialog(
            title = app.label,
            entries = listOf(
                "ホーム画面に追加" to {
                    menuApp = null
                    if (vm.addAppAuto(app) != PlaceResult.FAILED) {
                        toast(context, "ホーム画面に追加しました")
                        onClose()
                    } else {
                        toast(context, "ホーム画面に空きがありません")
                    }
                },
                "ドックに追加" to {
                    menuApp = null
                    vm.setDockEntry(vm.dock.value.size, app.key)
                    toast(context, "ドックに追加しました")
                },
                "フォルダに追加" to {
                    menuApp = null
                    folderPickApp = app
                },
                "アプリ情報" to {
                    menuApp = null
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                "アンインストール" to {
                    menuApp = null
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { toast(context, "アンインストールできません") }
                },
            ),
            onDismiss = { menuApp = null },
        )
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
