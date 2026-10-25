package surf.varun.feature.map

import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.FULL_BLEED
import surf.varun.core.navigation.MapRoute
import surf.varun.core.navigation.Route

/** Registers the wind map destination. */
fun EntryProviderScope<Route>.mapEntries() {
    entry<MapRoute>(metadata = mapOf(FULL_BLEED to true)) { route -> MapScreen(route) }
}
