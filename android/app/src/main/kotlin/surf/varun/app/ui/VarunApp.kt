package surf.varun.app.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import surf.varun.app.MainUiState
import surf.varun.app.MainViewModel
import surf.varun.app.R
import surf.varun.app.navigation.DeepLink
import surf.varun.app.navigation.rememberAppNavState
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.designsystem.theme.isDarkTheme
import surf.varun.core.model.UserSettings
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.navigation.MoreRoute
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.TopLevel
import surf.varun.core.ui.LocalReduceMotion
import surf.varun.core.ui.LocalSharedTransitionScope
import surf.varun.core.ui.LocalUserSettings
import surf.varun.core.ui.rememberReduceMotion
import surf.varun.feature.about.aboutEntries
import surf.varun.feature.alerts.alertsEntries
import surf.varun.feature.calculator.calculatorEntries
import surf.varun.feature.map.mapEntries
import surf.varun.feature.search.searchEntries
import surf.varun.feature.settings.settingsEntries
import surf.varun.feature.spot.spotEntries
import surf.varun.feature.spots.spotsEntries

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VarunApp(
    viewModel: MainViewModel,
    startTab: TopLevel,
    pendingLink: DeepLink?,
    onLinkHandled: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = (uiState as? MainUiState.Ready)?.settings ?: UserSettings()
    val navState = rememberAppNavState(startTab)

    LaunchedEffect(pendingLink) {
        pendingLink?.let {
            navState.handle(it)
            onLinkHandled()
        }
    }

    // System bar icons follow the app's theme, which can differ from the system's
    val dark = isDarkTheme(settings.theme)
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(dark, activity) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    VarunTheme(themeMode = settings.theme, dynamicColor = settings.dynamicColor) {
        CompositionLocalProvider(
            LocalUserSettings provides settings,
            LocalNavigator provides navState,
            LocalReduceMotion provides rememberReduceMotion(),
        ) {
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    TopLevel.entries.forEach { tab ->
                        item(
                            selected = navState.currentTab == tab,
                            onClick = { navState.switchTo(tab) },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            label = { Text(stringResource(tab.label())) },
                        )
                    }
                },
            ) {
                val entryProvider = entryProvider<Route> {
                    spotsEntries()
                    spotEntries()
                    mapEntries()
                    searchEntries()
                    calculatorEntries()
                    alertsEntries()
                    settingsEntries()
                    aboutEntries()
                    entry<MoreRoute> { MoreScreen() }
                }
                // Each tab's stack decorated on its own: an entry's ViewModels and saved state
                // (scroll, map camera) are dropped when it is popped from its tab, not every
                // time another tab is shown and its entries leave the displayed list
                val entriesByTab = TopLevel.entries.associateWith { tab ->
                    key(tab) {
                        rememberDecoratedNavEntries(
                            backStack = navState.stack(tab),
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            entryProvider = entryProvider,
                        )
                    }
                }
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavDisplay(
                            entries = navState.visibleTabs.flatMap { entriesByTab.getValue(it) },
                            onBack = { navState.back() },
                            sharedTransitionScope = this,
                            transitionSpec = {
                                if (navState.lastChangeSwitchedTab) tabSwitchTransition() else defaultTransitionSpec<Route>()()
                            },
                            popTransitionSpec = {
                                if (navState.lastChangeSwitchedTab) tabSwitchTransition() else defaultPopTransitionSpec<Route>()()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Material's fade through for top-level destinations: the old tab fades out quickly,
 * then the new one fades in. Short, so two tabs - one may be the animated map - are
 * not drawn on top of each other for long.
 */
private fun tabSwitchTransition(): ContentTransform =
    fadeIn(tween(TAB_FADE_IN_MS, delayMillis = TAB_FADE_OUT_MS)) togetherWith fadeOut(tween(TAB_FADE_OUT_MS))

private const val TAB_FADE_OUT_MS = 90
private const val TAB_FADE_IN_MS = 210

@Composable
private fun TopLevel.icon(): Painter = when (this) {
    TopLevel.SPOTS -> painterResource(R.drawable.ic_tab_spots)
    TopLevel.FAVOURITES -> rememberVectorPainter(Icons.Rounded.Star)
    TopLevel.MAP -> rememberVectorPainter(Icons.Rounded.Map)
    TopLevel.MORE -> rememberVectorPainter(Icons.Rounded.MoreHoriz)
}

private fun TopLevel.label() = when (this) {
    TopLevel.SPOTS -> R.string.tab_spots
    TopLevel.FAVOURITES -> R.string.tab_favourites
    TopLevel.MAP -> R.string.tab_map
    TopLevel.MORE -> R.string.tab_more
}
