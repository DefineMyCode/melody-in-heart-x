package cn.com.dcsgo.mihx.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Monochrome neutral brand palette (UI 设计定稿：黑白灰中性设计).
 *
 * Design logic:
 * - `primary` is the *strongest foreground* — near-black on light, near-white on dark — so it
 *   doubles as both brand color and high-contrast text/active color.
 * - `surface` family is a layered gray scale (Light: #FFFFFF → #E3E3E3; Dark: #09090A →
 *   #343435) giving cards / rows / dividers natural depth without hue.
 * - `error` keeps the Material semantic red as the single chromatic exception.
 */
private val LightPrimary = Color(0xFF1D1B20)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFE4E4E4)
private val LightOnPrimaryContainer = Color(0xFF1D1B20)
private val LightSecondary = Color(0xFF444746)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFE1E2E1)
private val LightOnSecondaryContainer = Color(0xFF1D1B20)
private val LightTertiary = Color(0xFF444746)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFE1E2E1)
private val LightOnTertiaryContainer = Color(0xFF1D1B20)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFF9DEDC)
private val LightOnErrorContainer = Color(0xFF410002)
private val LightBackground = Color(0xFFFBFBFB)
private val LightOnBackground = Color(0xFF1D1B20)
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF1D1B20)
private val LightSurfaceVariant = Color(0xFFE1E2E1)
private val LightOnSurfaceVariant = Color(0xFF444746)
private val LightSurfaceTint = Color(0xFF1D1B20)
private val LightInverseSurface = Color(0xFF2D2D2D)
private val LightInverseOnSurface = Color(0xFFF1F0EF)
private val LightInversePrimary = Color(0xFFE0E0E0)
private val LightOutline = Color(0xFF767575)
private val LightOutlineVariant = Color(0xFFC4C4C4)

private val DarkPrimary = Color(0xFFE0E0E0)
private val DarkOnPrimary = Color(0xFF1D1B20)
private val DarkPrimaryContainer = Color(0xFF444746)
private val DarkOnPrimaryContainer = Color(0xFFE3E1E1)
private val DarkSecondary = Color(0xFFC4C7C5)
private val DarkOnSecondary = Color(0xFF1D1B20)
private val DarkSecondaryContainer = Color(0xFF454746)
private val DarkOnSecondaryContainer = Color(0xFFE3E1E1)
private val DarkTertiary = Color(0xFFC4C7C5)
private val DarkOnTertiary = Color(0xFF1D1B20)
private val DarkTertiaryContainer = Color(0xFF454746)
private val DarkOnTertiaryContainer = Color(0xFFE3E1E1)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)
private val DarkBackground = Color(0xFF131314)
private val DarkOnBackground = Color(0xFFE3E1E1)
private val DarkSurface = Color(0xFF0E0E0F)
private val DarkOnSurface = Color(0xFFE3E1E1)
private val DarkSurfaceVariant = Color(0xFF454746)
private val DarkOnSurfaceVariant = Color(0xFFC4C7C5)
private val DarkSurfaceTint = Color(0xFFE0E0E0)
private val DarkInverseSurface = Color(0xFFE3E1E1)
private val DarkInverseOnSurface = Color(0xFF1D1B20)
private val DarkInversePrimary = Color(0xFF1D1B20)
private val DarkOutline = Color(0xFF909493)
private val DarkOutlineVariant = Color(0xFF444746)

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightSurfaceTint,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)
