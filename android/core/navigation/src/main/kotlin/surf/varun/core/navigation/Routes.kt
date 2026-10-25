package surf.varun.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination of the app. Features never import each other; they navigate
 * by handing one of these to the [Navigator].
 */
@Serializable
sealed interface Route : NavKey

/** The four bottom-bar destinations (spec 5.1). */
enum class TopLevel { SPOTS, FAVOURITES, MAP, MORE }

@Serializable
data class SpotsRoute(val country: String? = null) : Route

@Serializable
data object FavouritesRoute : Route

@Serializable
data class MapRoute(val focusWgId: Int? = null) : Route

@Serializable
data object MoreRoute : Route

/**
 * @param focusTime spot-local ISO time ("2026-10-24T13:00") the forecast should be
 *   scrolled to, e.g. from a wind alert
 */
@Serializable
data class SpotRoute(val wgId: Int, val focusTime: String? = null) : Route

@Serializable
data object SearchRoute : Route

@Serializable
data class CalculatorRoute(val windKnots: Double? = null) : Route

@Serializable
data object AlertsRoute : Route

/** Create ([alertId] null, optionally for [wgId]) or edit an alert. */
@Serializable
data class AlertEditorRoute(val alertId: Long? = null, val wgId: Int? = null) : Route

@Serializable
data object SettingsRoute : Route

@Serializable
data object AboutRoute : Route

@Serializable
data object LicencesRoute : Route

val TopLevel.root: Route
    get() = when (this) {
        TopLevel.SPOTS -> SpotsRoute()
        TopLevel.FAVOURITES -> FavouritesRoute
        TopLevel.MAP -> MapRoute()
        TopLevel.MORE -> MoreRoute
    }

/**
 * Entry metadata of a screen drawn edge to edge under the floating navigation of a
 * sideways phone (the map), rather than laid out beside it like the others.
 */
const val FULL_BLEED = "varun.fullBleed"

interface Navigator {
    /** Push [route] on top of the current tab. */
    fun navigate(route: Route)

    /** Switch to [tab]; with [route], replace the tab's root with it (e.g. a country filter). */
    fun switchTo(tab: TopLevel, route: Route? = null)

    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    object : Navigator {
        override fun navigate(route: Route) = Unit
        override fun switchTo(tab: TopLevel, route: Route?) = Unit
        override fun back() = Unit
    }
}
