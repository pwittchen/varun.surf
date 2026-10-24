package surf.varun.feature.spots

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.FavouritesRoute
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.SpotsRoute

/** Registers the Spots and Favourites tabs. */
fun EntryProviderScope<Route>.spotsEntries() {
    entry<SpotsRoute> { route ->
        SpotsScreen(hiltViewModel<SpotsViewModel, SpotsViewModel.Factory>(creationCallback = { it.create(route) }))
    }
    entry<FavouritesRoute> {
        FavouritesScreen(hiltViewModel())
    }
}
