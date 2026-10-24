package surf.varun.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import surf.varun.core.designsystem.R
import surf.varun.core.model.Direction

/**
 * Arrow pointing where the wind blows *to* - a north wind points down, as on the
 * web (`getWindArrow`: N = ↓). Rotation animates when the direction changes.
 */
@Composable
fun WindArrow(
    direction: Direction?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = Color.Unspecified,
    animate: Boolean = true,
    describe: Boolean = true,
) {
    val context = LocalContext.current
    val target = direction?.degrees?.toFloat() ?: 0f
    val rotation by animateFloatAsState(target, if (animate) tween(450) else tween(0), label = "windArrow")
    val description = remember(direction) { directionDescription(context, direction) }
    val tint = if (color == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else color
    Canvas(
        modifier
            .size(size)
            .then(if (describe) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        if (direction == null) {
            drawCircle(tint, radius = this.size.minDimension / 8)
            return@Canvas
        }
        val w = this.size.width
        val h = this.size.height
        rotate(rotation) {
            val path = Path().apply {
                // Downward arrow: tip at the bottom centre
                moveTo(w / 2, h * 0.94f)
                lineTo(w * 0.80f, h * 0.50f)
                lineTo(w * 0.58f, h * 0.56f)
                lineTo(w * 0.58f, h * 0.06f)
                lineTo(w * 0.42f, h * 0.06f)
                lineTo(w * 0.42f, h * 0.56f)
                lineTo(w * 0.20f, h * 0.50f)
                close()
            }
            drawPath(path, tint)
        }
    }
}

/** "wind from south-west", localized - for TalkBack. */
fun directionDescription(context: android.content.Context, direction: Direction?): String {
    if (direction == null) return context.getString(R.string.direction_unknown)
    return context.getString(R.string.wind_from, context.getString(directionNameRes(direction)))
}

fun directionNameRes(direction: Direction): Int = when (direction) {
    Direction.N -> R.string.direction_n
    Direction.NE -> R.string.direction_ne
    Direction.E -> R.string.direction_e
    Direction.SE -> R.string.direction_se
    Direction.S -> R.string.direction_s
    Direction.SW -> R.string.direction_sw
    Direction.W -> R.string.direction_w
    Direction.NW -> R.string.direction_nw
}
