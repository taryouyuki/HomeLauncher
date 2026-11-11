package jp.achi.homelauncher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.achi.homelauncher.LauncherViewModel
import jp.achi.homelauncher.data.AppInfo
import jp.achi.homelauncher.data.AppSorter
import jp.achi.homelauncher.data.SortMode

/** 下から上スワイプで開くアプリ一覧。名前/更新/起動順の並び替えと部分一致検索に対応。 */
@Composable
fun DrawerScreen(vm: LauncherViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }

    val list = remember(apps, query, settings.drawerSort, counts) {
        AppSorter.sort(AppSorter.filter(apps, query), settings.drawerSort, counts)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    ) {
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
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SortChip("名前順", settings.drawerSort == SortMode.NAME) { vm.setDrawerSort(SortMode.NAME) }
                Box(Modifier.width(8.dp))
                SortChip("更新順", settings.drawerSort == SortMode.UPDATED) { vm.setDrawerSort(SortMode.UPDATED) }
                Box(Modifier.width(8.dp))
                SortChip("起動順", settings.drawerSort == SortMode.LAUNCH) { vm.setDrawerSort(SortMode.LAUNCH) }
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

    menuApp?.let { app ->
        MenuDialog(
            title = app.label,
            entries = listOf(
                "ホーム画面に追加" to {
                    menuApp = null
                    if (vm.addAppAuto(app)) {
                        toast(context, "ホーム画面に追加しました")
                        onClose()
                    } else {
                        toast(context, "ホーム画面に空きがありません")
                    }
                },
                "ドックに追加" to {
                    menuApp = null
                    val s = vm.settings.value
                    val dockNow = vm.dock.value
                    if (dockNow.size >= s.dockCount && app.key !in dockNow) {
                        toast(context, "ドックに空きがありません")
                    } else {
                        vm.setDockApp(dockNow.size, app.key)
                        toast(context, "ドックに追加しました")
                    }
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
