package surf.varun.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import surf.varun.core.designsystem.theme.Numbers
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.designsystem.theme.fillForClass
import surf.varun.core.designsystem.theme.forClass
import surf.varun.core.domain.WindClass

/**
 * A wind number coloured by its class. Colour is never the only signal (spec 8.2):
 * rideable classes are bold, extreme is also underlined.
 */
@Composable
fun WindNumber(
    text: String,
    knots: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = Numbers.medium,
    windClass: WindClass = WindClass.of(knots),
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontWeight = if (windClass.isRideable) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (windClass == WindClass.EXTREME) TextDecoration.Underline else null,
        ),
        color = VarunTheme.wind.forClass(windClass),
        maxLines = 1,
    )
}

/** Wind number on a soft tint of its class, for table cells and strips. */
@Composable
fun WindCell(
    text: String,
    knots: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = Numbers.small,
) {
    val windClass = WindClass.of(knots)
    Box(
        modifier
            .background(VarunTheme.wind.fillForClass(windClass), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        WindNumber(text, knots, style = style, windClass = windClass)
    }
}
