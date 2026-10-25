package surf.varun.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True for a window under 480 dp tall - Material's compact height class, which is a
 * phone held sideways. Such a window has width to spare and no height: the navigation
 * floats over the screen, headers shrink and lists spread into columns.
 */
@Composable
fun isCompactHeight(): Boolean {
    val height = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { height.toDp() } < COMPACT_HEIGHT
}

private val COMPACT_HEIGHT = 480.dp

/**
 * How far the floating navigation of a sideways phone reaches in from the start edge,
 * as seen by a screen drawn under it (the map), which keeps what it puts on that edge
 * clear of it. Zero everywhere else: other screens are laid out beside it.
 */
val LocalFloatingNavigationInset = compositionLocalOf<Dp> { 0.dp }

/**
 * The strip the collapsed floating navigation takes along the start edge, past the safe
 * area. A screen drawn under it moves only its top bar this far in; everything below
 * runs under it, and the opened navigation floats over that. Zero without it.
 */
val LocalFloatingNavigationGutter = compositionLocalOf<Dp> { 0.dp }
