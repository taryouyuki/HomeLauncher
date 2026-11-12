package jp.achi.homelauncher.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import jp.achi.homelauncher.data.LauncherSettings
import java.io.File

/** 設定で追加したフォント(アプリ内に保存したファイル)。無い・壊れている場合は null(標準)。 */
@Composable
fun rememberCustomFont(settings: LauncherSettings): FontFamily? {
    val context = LocalContext.current
    return remember(settings.fontFile) {
        if (settings.fontFile.isEmpty()) null
        else runCatching {
            val f = File(File(context.filesDir, "fonts"), settings.fontFile)
            if (f.exists()) FontFamily(Font(f)) else null
        }.getOrNull()
    }
}

fun Typography.withFont(family: FontFamily?): Typography {
    if (family == null) return this
    return Typography(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}

@Composable
fun HomeLauncherTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = when (settings.darkTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val font = rememberCustomFont(settings)
    MaterialTheme(colorScheme = scheme, typography = Typography().withFont(font), content = content)
}

/**
 * ホーム画面とアプリ一覧に使う配色。
 * 「好きな色」を選んだ場合は、設定画面(システム/Dynamic Color)とは別に、選んだ色から作った配色を使う。
 */
@Composable
fun HomeColorTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    if (settings.homeColorMode != "custom") {
        CompositionLocalProvider(LocalIconShape provides settings.iconShape) { content() }
        return
    }
    val dark = when (settings.homeDarkTheme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val font = rememberCustomFont(settings)
    MaterialTheme(
        colorScheme = customColorScheme(Color(settings.homeColor), dark),
        typography = Typography().withFont(font),
    ) {
        CompositionLocalProvider(LocalIconShape provides settings.iconShape) { content() }
    }
}

/** 1色(シード)から、ライト/ダークの配色を簡易的に作る。 */
fun customColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val white = Color.White
    val black = Color.Black
    fun on(c: Color) = if (c.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White
    return if (!dark) {
        val secondary = lerp(seed, Color(0xFF777777), 0.5f)
        val surface = lerp(seed, white, 0.93f)
        val primaryContainer = lerp(seed, white, 0.78f)
        lightColorScheme(
            primary = seed,
            onPrimary = on(seed),
            primaryContainer = primaryContainer,
            onPrimaryContainer = lerp(seed, black, 0.65f),
            secondary = secondary,
            onSecondary = on(secondary),
            secondaryContainer = lerp(secondary, white, 0.8f),
            onSecondaryContainer = lerp(secondary, black, 0.6f),
            background = surface,
            onBackground = lerp(seed, black, 0.88f),
            surface = surface,
            onSurface = lerp(seed, black, 0.88f),
            surfaceVariant = lerp(seed, white, 0.85f),
            onSurfaceVariant = lerp(seed, black, 0.6f),
            outline = lerp(seed, black, 0.4f),
            surfaceContainer = lerp(seed, white, 0.9f),
            surfaceContainerHigh = lerp(seed, white, 0.86f),
            surfaceContainerHighest = lerp(seed, white, 0.82f),
        )
    } else {
        val primary = lerp(seed, white, 0.35f)
        val secondary = lerp(seed, Color(0xFF999999), 0.5f)
        val surface = lerp(seed, black, 0.86f)
        darkColorScheme(
            primary = primary,
            onPrimary = lerp(seed, black, 0.8f),
            primaryContainer = lerp(seed, black, 0.55f),
            onPrimaryContainer = lerp(seed, white, 0.8f),
            secondary = secondary,
            onSecondary = lerp(secondary, black, 0.8f),
            secondaryContainer = lerp(secondary, black, 0.6f),
            onSecondaryContainer = lerp(secondary, white, 0.8f),
            background = surface,
            onBackground = lerp(seed, white, 0.88f),
            surface = surface,
            onSurface = lerp(seed, white, 0.88f),
            surfaceVariant = lerp(seed, black, 0.7f),
            onSurfaceVariant = lerp(seed, white, 0.7f),
            outline = lerp(seed, white, 0.45f),
            surfaceContainer = lerp(seed, black, 0.8f),
            surfaceContainerHigh = lerp(seed, black, 0.76f),
            surfaceContainerHighest = lerp(seed, black, 0.7f),
        )
    }
}
