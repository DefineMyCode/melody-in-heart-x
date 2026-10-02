package cn.com.dcsgo.mihx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cn.com.dcsgo.mihx.core.model.ThemeVariant

private val LightErrorPalette = ErrorPalette(
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkErrorPalette = ErrorPalette(
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private data class ErrorPalette(
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

private fun tokensToColorScheme(tokens: ThemeTokens, isDark: Boolean): ColorScheme {
    val error = if (isDark) DarkErrorPalette else LightErrorPalette
    val inverseSurface = if (isDark) Color(tokens.text1) else Color(tokens.bg0)
    val inverseOnSurface = if (isDark) Color(tokens.bg0) else Color(tokens.text1)
    return if (isDark) {
        darkColorScheme(
            primary = Color(tokens.accent),
            onPrimary = Color(tokens.onAccent),
            primaryContainer = Color(tokens.bg4),
            onPrimaryContainer = Color(tokens.text1),
            inversePrimary = Color(tokens.accent2),
            secondary = Color(tokens.accent2),
            onSecondary = Color(tokens.onAccent2),
            secondaryContainer = Color(tokens.bg3),
            onSecondaryContainer = Color(tokens.text1),
            tertiary = Color(tokens.accent2),
            onTertiary = Color(tokens.onAccent2),
            tertiaryContainer = Color(tokens.bg4),
            onTertiaryContainer = Color(tokens.text1),
            background = Color(tokens.bg0),
            onBackground = Color(tokens.text1),
            surface = Color(tokens.bg0),
            onSurface = Color(tokens.text1),
            surfaceVariant = Color(tokens.bg3),
            onSurfaceVariant = Color(tokens.text2),
            surfaceTint = Color(tokens.accent),
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error.error,
            onError = error.onError,
            errorContainer = error.errorContainer,
            onErrorContainer = error.onErrorContainer,
            outline = Color(tokens.out2),
            outlineVariant = Color(tokens.out1),
            scrim = Color(0xFF000000),
            surfaceBright = Color(tokens.bg1),
            surfaceDim = Color(tokens.bg4),
            surfaceContainerLowest = Color(tokens.bg1),
            surfaceContainerLow = Color(tokens.bg2),
            surfaceContainer = Color(tokens.bg3),
            surfaceContainerHigh = Color(tokens.bg4),
            surfaceContainerHighest = Color(tokens.bg4),
        )
    } else {
        lightColorScheme(
            primary = Color(tokens.accent),
            onPrimary = Color(tokens.onAccent),
            primaryContainer = Color(tokens.bg4),
            onPrimaryContainer = Color(tokens.text1),
            inversePrimary = Color(tokens.accent2),
            secondary = Color(tokens.accent2),
            onSecondary = Color(tokens.onAccent2),
            secondaryContainer = Color(tokens.bg3),
            onSecondaryContainer = Color(tokens.text1),
            tertiary = Color(tokens.accent2),
            onTertiary = Color(tokens.onAccent2),
            tertiaryContainer = Color(tokens.bg4),
            onTertiaryContainer = Color(tokens.text1),
            background = Color(tokens.bg0),
            onBackground = Color(tokens.text1),
            surface = Color(tokens.bg0),
            onSurface = Color(tokens.text1),
            surfaceVariant = Color(tokens.bg3),
            onSurfaceVariant = Color(tokens.text2),
            surfaceTint = Color(tokens.accent),
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error.error,
            onError = error.onError,
            errorContainer = error.errorContainer,
            onErrorContainer = error.onErrorContainer,
            outline = Color(tokens.out2),
            outlineVariant = Color(tokens.out1),
            scrim = Color(0xFF000000),
            surfaceBright = Color(tokens.bg1),
            surfaceDim = Color(tokens.bg4),
            surfaceContainerLowest = Color(tokens.bg1),
            surfaceContainerLow = Color(tokens.bg2),
            surfaceContainer = Color(tokens.bg3),
            surfaceContainerHigh = Color(tokens.bg4),
            surfaceContainerHighest = Color(tokens.bg4),
        )
    }
}

/**
 * 主题令牌来源：显式传入的 tokens 优先（调试面板 / 未来的外观包），否则取内置预设。
 */
@Composable
private fun resolveTokens(
    darkTheme: Boolean,
    variant: ThemeVariant,
    tokens: ThemeTokens?,
): ThemeTokens = tokens ?: ThemeTokens.builtin(variant, darkTheme)

@Composable
fun MusicplayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    variant: ThemeVariant = ThemeVariant.MONO,
    // 显式令牌覆盖（调试面板实时调色 / 未来的外观包）。null = 用 variant 对应的内置预设。
    tokens: ThemeTokens? = null,
    // Dynamic color is available on Android 12+，默认关闭以使用品牌色
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> tokensToColorScheme(tokens = resolveTokens(darkTheme, variant, tokens), isDark = darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        // Material3 的 MaterialTheme 不提供自适应 LocalContentColor（非 Surface 的
        // 无颜色 Text 会回落到黑色，深色背景下不可见），这里显式提供 onSurface，
        // 保证所有未指定 color 的文字随明暗主题自适应。
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onSurface,
        ) {
            content()
        }
    }
}
