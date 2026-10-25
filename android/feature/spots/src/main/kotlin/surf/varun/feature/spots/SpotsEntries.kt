package surf.varun.feature.spots

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.FULL_BLEED
import surf.varun.core.navigation.FavouritesRoute
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.SpotsRoute

/** Registers the Spots and Favourites tabs. */
fun EntryProviderScope<Route>.spotsEntries() {
    // Edge to edge under the floating navigation of a sideways phone: only its top bar steps aside
    entry<SpotsRoute>(metadata = mapOf(FULL_BLEED to true)) { route ->
        SpotsScreen(hiltViewModel<SpotsViewModel, SpotsViewModel.Factory>(creationCallback = { it.create(route) }))
    }
    entry<FavouritesRoute> {
        FavouritesScreen(hiltViewModel())
    }
}
