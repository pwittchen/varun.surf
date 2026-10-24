package surf.varun.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/* Brand palette, from the web's styles.css (dark is the web's default theme). */

internal object DarkPalette {
    val background = Color(0xFF08090A)
    val surface = Color(0xFF0F1113)
    val surfaceVariant = Color(0xFF17191C)
    val surfaceHigh = Color(0xFF1C1E21)
    val onSurface = Color(0xFFF2F3F4)
    val onSurfaceVariant = Color(0xFF8A8F98)
    val outline = Color(0xFF26292E)
    val outlineVariant = Color(0xFF1C1E21)
    val accent = Color(0xFF22C3E6)
    val accentContainer = Color(0xFF0E5D70)
    val onAccentContainer = Color(0xFFD7F6FD)
    val error = Color(0xFFEF4444)
}

internal object LightPalette {
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFF7F7F5)
    val surfaceHigh = Color(0xFFEFEFEC)
    val onSurface = Color(0xFF37352F)
    val onSurfaceVariant = Color(0xFF6B6A64)
    val outline = Color(0xFFE0E0DC)
    val outlineVariant = Color(0xFFEBEBE8)
    val accent = Color(0xFF0891B2)
    val accentContainer = Color(0xFFCFF3FA)
    val onAccentContainer = Color(0xFF0B5F78)
    val error = Color(0xFFDC2626)
}

/**
 * Wind class colours (`--wind-*` on the web), tuned per theme for contrast. The
 * map's calm/light pair splits the web's grey "weak" range.
 */
data class WindColors(
    val calm: Color,
    val light: Color,
    val weak: Color,
    val moderate: Color,
    val strong: Color,
    val extreme: Color,
    val weakFill: Color,
    val moderateFill: Color,
    val strongFill: Color,
    val extremeFill: Color,
    val stale: Color,
    val favourite: Color,
    val tempWarm: Color,
    val tempCold: Color,
    val rain: Color,
    val pressure: Color,
    val waveSmall: Color,
    val waveModerate: Color,
    val waveLarge: Color,
)

val DarkWindColors = WindColors(
    calm = Color(0xFF64748B),
    light = Color(0xFF38BDF8),
    weak = Color(0xFF8A94A6),
    moderate = Color(0xFF2EE66D),
    strong = Color(0xFFF59E0B),
    extreme = Color(0xFFF5484A),
    weakFill = Color(0x29646474),
    moderateFill = Color(0x262EE66D),
    strongFill = Color(0x29F59E0B),
    extremeFill = Color(0x29F5484A),
    stale = Color(0xFFFBBF24),
    favourite = Color(0xFFFBBF24),
    tempWarm = Color(0xFFF97316),
    tempCold = Color(0xFF06B6D4),
    rain = Color(0xFF3B82F6),
    pressure = Color(0xFFA78BFA),
    waveSmall = Color(0xFF22C55E),
    waveModerate = Color(0xFFF59E0B),
    waveLarge = Color(0xFFEF4444),
)

val LightWindColors = WindColors(
    calm = Color(0xFF6B7280),
    light = Color(0xFF0284C7),
    weak = Color(0xFF6B7280),
    moderate = Color(0xFF059669),
    strong = Color(0xFFEA580C),
    extreme = Color(0xFFDC2626),
    weakFill = Color(0x1F6B7280),
    moderateFill = Color(0x24059669),
    strongFill = Color(0x24EA580C),
    extremeFill = Color(0x24DC2626),
    stale = Color(0xFFB45309),
    favourite = Color(0xFFD97706),
    tempWarm = Color(0xFFEA580C),
    tempCold = Color(0xFF0891B2),
    rain = Color(0xFF2563EB),
    pressure = Color(0xFF7C3AED),
    waveSmall = Color(0xFF16A34A),
    waveModerate = Color(0xFFD97706),
    waveLarge = Color(0xFFDC2626),
)
