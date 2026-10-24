package surf.varun.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import surf.varun.core.domain.WindClass
import surf.varun.core.model.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = Color(0xFF00222B),
    primaryContainer = DarkPalette.accentContainer,
    onPrimaryContainer = DarkPalette.onAccentContainer,
    secondary = DarkPalette.accent,
    secondaryContainer = DarkPalette.surfaceHigh,
    onSecondaryContainer = DarkPalette.onSurface,
    tertiary = Color(0xFFFBBF24),
    background = DarkPalette.background,
    onBackground = DarkPalette.onSurface,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.onSurface,
    surfaceVariant = DarkPalette.surfaceVariant,
    onSurfaceVariant = DarkPalette.onSurfaceVariant,
    surfaceContainerLowest = DarkPalette.background,
    surfaceContainerLow = DarkPalette.surface,
    surfaceContainer = DarkPalette.surface,
    surfaceContainerHigh = DarkPalette.surfaceVariant,
    surfaceContainerHighest = DarkPalette.surfaceHigh,
    outline = DarkPalette.outline,
    outlineVariant = DarkPalette.outlineVariant,
    error = DarkPalette.error,
)

private val LightScheme = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = Color.White,
    primaryContainer = LightPalette.accentContainer,
    onPrimaryContainer = LightPalette.onAccentContainer,
    secondary = LightPalette.accent,
    secondaryContainer = LightPalette.surfaceHigh,
    onSecondaryContainer = LightPalette.onSurface,
    tertiary = Color(0xFFD97706),
    background = LightPalette.background,
    onBackground = LightPalette.onSurface,
    surface = LightPalette.surface,
    onSurface = LightPalette.onSurface,
    surfaceVariant = LightPalette.surfaceVariant,
    onSurfaceVariant = LightPalette.onSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightPalette.surfaceVariant,
    surfaceContainer = LightPalette.surfaceVariant,
    surfaceContainerHigh = LightPalette.surfaceHigh,
    surfaceContainerHighest = Color(0xFFE6E6E2),
    outline = LightPalette.outline,
    outlineVariant = LightPalette.outlineVariant,
    error = LightPalette.error,
)

@Immutable
data class VarunExtras(val wind: WindColors, val isDark: Boolean)

val LocalVarunExtras = staticCompositionLocalOf { VarunExtras(DarkWindColors, isDark = true) }

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun VarunTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalVarunExtras provides VarunExtras(if (dark) DarkWindColors else LightWindColors, dark)) {
        MaterialTheme(colorScheme = scheme, typography = VarunTypography, shapes = VarunShapes, content = content)
    }
}

object VarunTheme {
    val wind: WindColors
        @Composable @ReadOnlyComposable
        get() = LocalVarunExtras.current.wind

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalVarunExtras.current.isDark
}

/** Colour of a wind class, for text and markers. */
fun WindColors.forClass(windClass: WindClass): Color = when (windClass) {
    WindClass.CALM -> calm
    WindClass.LIGHT -> light
    WindClass.WEAK -> weak
    WindClass.MODERATE -> moderate
    WindClass.STRONG -> strong
    WindClass.EXTREME -> extreme
}

/** Soft background tint of a wind class, for rows and cards. */
fun WindColors.fillForClass(windClass: WindClass): Color = when (windClass) {
    WindClass.CALM, WindClass.LIGHT, WindClass.WEAK -> weakFill
    WindClass.MODERATE -> moderateFill
    WindClass.STRONG -> strongFill
    WindClass.EXTREME -> extremeFill
}
