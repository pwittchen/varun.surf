package surf.varun.app.navigation

import android.content.Intent
import android.net.Uri
import surf.varun.core.navigation.MapRoute
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.SearchRoute
import surf.varun.core.navigation.SpotRoute
import surf.varun.core.navigation.SpotsRoute
import surf.varun.core.navigation.TopLevel

/** Where an incoming link should take the user. */
data class DeepLink(val tab: TopLevel, val route: Route?, val replaceRoot: Boolean = false)

/**
 * Parses the links the app answers to (spec 6.7):
 * - verified App Links: `https://varun.surf/spot/{id}`, `/country/{name}`, `/map`
 * - the internal scheme widgets, notifications and shortcuts use:
 *   `varun://spot/{id}?focus=<spot-local ISO time>`, `varun://map`, `varun://search`
 */
object DeepLinks {
    fun parse(intent: Intent?): DeepLink? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return parse(intent.data ?: return null)
    }

    fun parse(uri: Uri): DeepLink? {
        val segments = when (uri.scheme) {
            "https", "http" -> if (uri.host?.removePrefix("www.") == "varun.surf") uri.pathSegments else return null
            "varun" -> listOfNotNull(uri.host) + uri.pathSegments
            else -> return null
        }
        return when (segments.firstOrNull()) {
            "spot" -> segments.getOrNull(1)?.toIntOrNull()?.let {
                DeepLink(TopLevel.SPOTS, SpotRoute(it, focusTime = uri.getQueryParameter("focus")))
            }
            "country" -> segments.getOrNull(1)?.let { DeepLink(TopLevel.SPOTS, SpotsRoute(country = it), replaceRoot = true) }
            "map" -> DeepLink(TopLevel.MAP, null)
            "search" -> DeepLink(TopLevel.SPOTS, SearchRoute)
            "starred", "favourites" -> DeepLink(TopLevel.FAVOURITES, null)
            null -> DeepLink(TopLevel.SPOTS, null)
            else -> null
        }
    }
}
