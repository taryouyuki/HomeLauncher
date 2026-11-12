package jp.achi.homelauncher.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import jp.achi.homelauncher.data.HomeItem
import jp.achi.homelauncher.data.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * 子(ウィジェットのAndroidViewなど)の入力を消費せずに、長押しだけを検出する。
 * 初期パスで監視するので、ウィジェットの上でも動作する。
 */
fun Modifier.detectLongPress(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var finished = false
            while (!finished) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                finished = change == null || !change.pressed ||
                    (change.position - down.position).getDistance() > slop
            }
            true
        }
        if (ended == null) {
            onLongPress()
            // 指を離すまでの入力を消費して、下のウィジェットに「タップ」や「スクロール」として伝わらないようにする
            // (長押しで編集モードに入った直後に、指を離した拍子にウィジェットが開くのを防ぐ)
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                pressed = event.changes.any { it.pressed }
            }
        }
    }
}

/**
 * ウィジェット(AppWidgetHostView)を入れる枠。縦方向のドラッグが始まったら、親(Compose側)に
 * 横取りされないよう要求し、メモ帳などウィジェット内のスクロールを優先する。
 * 横方向のドラッグは従来どおりページ切り替え・スタックの切り替えに使える。
 */
class WidgetHostFrame(context: android.content.Context) : android.widget.FrameLayout(context) {
    private var downX = 0f
    private var downY = 0f
    private var decided = false
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                decided = false
            }
            android.view.MotionEvent.ACTION_MOVE -> if (!decided) {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > slop || dy > slop) {
                    decided = true
                    if (dy > dx) parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

/** ウィジェット上でタッチ中かどうか。ウィジェット内のスクロールを優先し、アプリ一覧の上スワイプを抑止するために使う。 */
object SwipeGuard {
    @Volatile
    var widgetTouched: Boolean = false
}

/**
 * ウィジェットに触れたら SwipeGuard を立てる(入力は消費しない)。
 * フラグは次のタッチの開始時(ホーム側)に下ろされるので、指を離す時点まで有効。
 */
fun Modifier.blockDrawerSwipe(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        SwipeGuard.widgetTouched = true
    }
}

/**
 * スタック内のウィジェットを左右スワイプで切り替える。
 * 初期パスで横方向のドラッグを奪うので、クリックを処理するウィジェットの上でもスワイプできる。
 */
private fun Modifier.stackSwipe(state: PagerState, scope: CoroutineScope): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startPage = state.currentPage
        val slop = viewConfiguration.touchSlop
        var totalX = 0f
        var totalY = 0f
        var swiping = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val d = change.positionChange()
            totalX += d.x
            totalY += d.y
            if (!swiping) {
                if (abs(totalX) > slop && abs(totalX) > abs(totalY) * 1.5f) {
                    swiping = true
                } else if (abs(totalY) > slop * 2) {
                    break
                }
            }
            if (swiping) {
                change.consume()
                state.dispatchRawDelta(-d.x)
            }
        }
        if (swiping) {
            val threshold = size.width * 0.15f
            val last = (state.pageCount - 1).coerceAtLeast(0)
            // 最後まで行ったら最初に戻る(最初から戻ると最後へ)
            val target = when {
                totalX < -threshold -> if (startPage >= last) 0 else startPage + 1
                totalX > threshold -> if (startPage <= 0) last else startPage - 1
                else -> startPage
            }.coerceIn(0, last)
            scope.launch { state.animateScrollToPage(target) }
        }
    }
}

@Composable
fun PackageIcon(pkg: String, sizeDp: Int) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, pkg) {
        value = withContext(Dispatchers.Default) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
    } else {
        Spacer(Modifier.size(sizeDp.dp))
    }
}

/** ホームに置かれた1つのウィジェット。長押しで編集モード、右上の「⋮」でも編集モードに入る。 */
@Composable
fun WidgetBox(
    item: HomeItem,
    widgets: WidgetController,
    alphaValue: Float,
    widthDp: Int,
    heightDp: Int,
    locked: Boolean,
    onSelect: () -> Unit,
) {
    val currentSelect by rememberUpdatedState(onSelect)
    Box(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .blockDrawerSwipe()
            .detectLongPress { currentSelect() },
    ) {
        AndroidView(
            factory = { ctx ->
                WidgetHostFrame(ctx).also { frame ->
                    frame.addView(
                        widgets.createView(ctx, item.widgetId) ?: View(ctx),
                        android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            },
            update = { frame ->
                val v = frame.getChildAt(0)
                if (v is AppWidgetHostView) {
                    runCatching { v.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp) }
                }
            },
            modifier = Modifier.fillMaxSize().alpha(alphaValue),
        )
        // 編集モード中はウィジェットを操作させない(タップで開いてしまうのを防ぐ)。タップでこの項目を選択する。
        if (locked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { currentSelect() }) },
            )
        }
    }
}

/** ウィジェットスタック。この領域を左右にスワイプすると、登録したウィジェットが切り替わる。 */
@Composable
fun StackBox(
    children: List<HomeItem>,
    widgets: WidgetController,
    settings: LauncherSettings,
    widthDp: Int,
    heightDp: Int,
    locked: Boolean,
    onSelect: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { children.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    val currentSelect by rememberUpdatedState(onSelect)

    Box(
        Modifier
            .fillMaxSize()
            .blockDrawerSwipe()
            .stackSwipe(pagerState, scope)
            .detectLongPress { currentSelect() },
    ) {
        if (children.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "ウィジェットスタック\n長押し →「スタック管理」で追加",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val child = children.getOrNull(page)
                if (child != null) {
                    WidgetBox(
                        item = child,
                        widgets = widgets,
                        alphaValue = if (child.alpha >= 0f) child.alpha else settings.widgetGlobalAlpha,
                        widthDp = widthDp,
                        heightDp = heightDp,
                        locked = locked,
                        onSelect = onSelect,
                    )
                }
            }
            if (children.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(children.size) { i ->
                        Box(
                            Modifier
                                .padding(2.dp)
                                .size(if (i == pagerState.currentPage) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (i == pagerState.currentPage) 0.95f else 0.5f)),
                        )
                    }
                }
            }
        }
    }
}

private class WidgetRow(val info: AppWidgetProviderInfo, val label: String, val appLabel: String)

/** ウィジェット選択。アプリのアイコンを表示し、ウィジェット名・アプリ名の両方で検索できる。 */
@Composable
fun WidgetPickerDialog(
    widgets: WidgetController,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember {
        widgets.providers()
            .map { WidgetRow(it, widgets.labelOf(it), widgets.appLabelOf(it)) }
            .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(rows, query) {
        val q = query.trim()
        if (q.isEmpty()) rows
        else rows.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.appLabel.contains(q, ignoreCase = true) ||
                it.info.provider.packageName.contains(q, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text("ウィジェットを選択") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("アプリ名・ウィジェット名で検索") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.info.provider.flattenToString() }) { row ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(row.info) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PackageIcon(row.info.provider.packageName, 40)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    row.appLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

/** スタックに入っているウィジェットの一覧。追加・削除ができる。 */
@Composable
fun StackManageDialog(
    children: List<HomeItem>,
    widgets: WidgetController,
    onAdd: () -> Unit,
    onRemove: (HomeItem) -> Unit,
    onMove: (HomeItem, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("スタックのウィジェット") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        text = {
            Column {
                if (children.isEmpty()) {
                    Text("まだウィジェットがありません。", modifier = Modifier.padding(vertical = 8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(children, key = { it.id }) { child ->
                        val info = widgets.findProvider(child.provider)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PackageIcon(info?.provider?.packageName ?: child.provider.substringBefore('/'), 32)
                            Text(
                                info?.let { widgets.labelOf(it) } ?: "ウィジェット",
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { onMove(child, -1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { Text("↑") }
                            TextButton(onClick = { onMove(child, 1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) { Text("↓") }
                            TextButton(onClick = { onRemove(child) }) { Text("削除") }
                        }
                    }
                }
                OutlinedButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) { Text("ウィジェットを追加") }
            }
        },
    )
}
