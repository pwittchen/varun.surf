package surf.varun.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val base = Typography()

/**
 * System font (Roboto Flex on modern devices), never thin weights for data so it
 * stays legible in sunlight. Numbers use tabular figures via [Numbers].
 */
val VarunTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Text styles for wind numbers - the hero of every screen. */
object Numbers {
    private const val TABULAR = "tnum"

    val hero = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR)
    val large = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR)
    val medium = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)
    val small = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR)
    val tiny = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR)
}

val VarunShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
