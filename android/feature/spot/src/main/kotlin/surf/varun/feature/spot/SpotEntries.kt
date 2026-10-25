package surf.varun.feature.spot

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.FULL_BLEED
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.SpotRoute

/** Registers this feature's destinations. */
fun EntryProviderScope<Route>.spotEntries() {
    // Edge to edge under the floating navigation of a sideways phone: only its top bar steps aside
    entry<SpotRoute>(metadata = mapOf(FULL_BLEED to true)) { route ->
        val viewModel = hiltViewModel<SpotViewModel, SpotViewModel.Factory>(
            key = "spot-${route.wgId}",
            creationCallback = { it.create(route) },
        )
        SpotScreen(viewModel)
    }
}
