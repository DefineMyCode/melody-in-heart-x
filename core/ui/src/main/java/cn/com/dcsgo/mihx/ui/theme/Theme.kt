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

private fun paletteToColorScheme(palette: ThemePalette, isDark: Boolean): ColorScheme {
    val error = if (isDark) DarkErrorPalette else LightErrorPalette
    val inverseSurface = if (isDark) palette.text1 else palette.bg0
    val inverseOnSurface = if (isDark) palette.bg0 else palette.text1
    return if (isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.bg4,
            onPrimaryContainer = palette.text1,
            inversePrimary = palette.accent2,
            secondary = palette.accent2,
            onSecondary = palette.onAccent2,
            secondaryContainer = palette.bg3,
            onSecondaryContainer = palette.text1,
            tertiary = palette.accent2,
            onTertiary = palette.onAccent2,
            tertiaryContainer = palette.bg4,
            onTertiaryContainer = palette.text1,
            background = palette.bg0,
            onBackground = palette.text1,
            surface = palette.bg0,
            onSurface = palette.text1,
            surfaceVariant = palette.bg3,
            onSurfaceVariant = palette.text2,
            surfaceTint = palette.accent,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error.error,
            onError = error.onError,
            errorContainer = error.errorContainer,
            onErrorContainer = error.onErrorContainer,
            outline = palette.out2,
            outlineVariant = palette.out1,
            scrim = Color(0xFF000000),
            surfaceBright = palette.bg1,
            surfaceDim = palette.bg4,
            surfaceContainerLowest = palette.bg1,
            surfaceContainerLow = palette.bg2,
            surfaceContainer = palette.bg3,
            surfaceContainerHigh = palette.bg4,
            surfaceContainerHighest = palette.bg4,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.bg4,
            onPrimaryContainer = palette.text1,
            inversePrimary = palette.accent2,
            secondary = palette.accent2,
            onSecondary = palette.onAccent2,
            secondaryContainer = palette.bg3,
            onSecondaryContainer = palette.text1,
            tertiary = palette.accent2,
            onTertiary = palette.onAccent2,
            tertiaryContainer = palette.bg4,
            onTertiaryContainer = palette.text1,
            background = palette.bg0,
            onBackground = palette.text1,
            surface = palette.bg0,
            onSurface = palette.text1,
            surfaceVariant = palette.bg3,
            onSurfaceVariant = palette.text2,
            surfaceTint = palette.accent,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            error = error.error,
            onError = error.onError,
            errorContainer = error.errorContainer,
            onErrorContainer = error.onErrorContainer,
            outline = palette.out2,
            outlineVariant = palette.out1,
            scrim = Color(0xFF000000),
            surfaceBright = palette.bg1,
            surfaceDim = palette.bg4,
            surfaceContainerLowest = palette.bg1,
            surfaceContainerLow = palette.bg2,
            surfaceContainer = palette.bg3,
            surfaceContainerHigh = palette.bg4,
            surfaceContainerHighest = palette.bg4,
        )
    }
}

@Composable
private fun paletteFor(darkTheme: Boolean, variant: ThemeVariant): ThemePalette = when (variant) {
    ThemeVariant.MONO -> if (darkTheme) MonoDarkColors else MonoLightColors
    ThemeVariant.VERMILION -> if (darkTheme) VermilionNightColors else VermilionDayColors
}

@Composable
fun MusicplayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    variant: ThemeVariant = ThemeVariant.MONO,
    // Dynamic color is available on Android 12+，默认关闭以使用品牌色
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> paletteToColorScheme(palette = paletteFor(darkTheme, variant), isDark = darkTheme)
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
