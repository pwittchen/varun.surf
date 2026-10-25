package surf.varun.core.ui

import android.provider.Settings
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** True when the user turned animations off ("Remove animations"): no particles, no shared elements. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * True once this screen has finished coming in and while it is not on its way out:
 * the moment for work that would stutter the transition, or that can't take part in
 * it (a SurfaceView ignores the fade). Only valid inside a NavDisplay entry.
 */
@Composable
fun rememberScreenSettled(): Boolean {
    val transition = LocalNavAnimatedContentScope.current.transition
    val settled by remember(transition) {
        derivedStateOf {
            transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
        }
    }
    return settled
}

/** Provided by the app around its NavDisplay so cards and detail screens can share elements. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Shares bounds between screens under [key] (e.g. "spot-name-500760") - the card
 * to detail transition of spec 9.3. A no-op without a scope or with reduced motion.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    if (LocalReduceMotion.current) return this
    // Only valid inside a NavDisplay entry, which is the only place screens are composed
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(scope) {
        this@sharedElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedScope,
        )
    }
}
