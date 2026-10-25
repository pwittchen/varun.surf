package surf.varun.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import surf.varun.app.R
import surf.varun.core.navigation.TopLevel

/**
 * The navigation of a sideways phone: a card floating over the start edge instead of
 * a rail docked beside the screen, so the map runs under it. Collapsed it is only the
 * button that opens it again, which leaves the map almost the whole screen.
 */
@Composable
internal fun FloatingNavigation(
    current: TopLevel,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top))
            .padding(start = MARGIN, top = TOP_MARGIN, end = GAP),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(PADDING),
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (expanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu,
                    contentDescription = stringResource(if (expanded) R.string.navigation_collapse else R.string.navigation_expand),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandIn(expandFrom = Alignment.TopCenter),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.TopCenter),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
                    TopLevel.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = current == tab,
                            onClick = { onSelect(tab) },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            label = { Text(stringResource(tab.label())) },
                        )
                    }
                }
            }
        }
    }
}

private val MARGIN = 12.dp

// Centres the collapsed button on a 64 dp top app bar under the status bar
private val TOP_MARGIN = 4.dp
private val PADDING = 4.dp
private val GAP = 8.dp

/** Collapsed, the card is the menu button alone: this is how far it reaches past the safe area. */
internal val FloatingNavigationCollapsedWidth = MARGIN + PADDING + 48.dp + PADDING + GAP
