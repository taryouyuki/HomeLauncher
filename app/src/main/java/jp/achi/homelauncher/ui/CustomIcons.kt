package jp.achi.homelauncher.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** アプリごとに差し替えた自作アイコン(appKey → 画像ファイルのパス)。 */
object CustomIcons {
    var map by mutableStateOf<Map<String, String>>(emptyMap())

    fun decode(path: String): ImageBitmap? =
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
}
