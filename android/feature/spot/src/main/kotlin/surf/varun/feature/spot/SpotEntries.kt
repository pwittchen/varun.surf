package surf.varun.feature.spot

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.SpotRoute

/** Registers this feature's destinations. */
fun EntryProviderScope<Route>.spotEntries() {
    entry<SpotRoute> { route ->
        val viewModel = hiltViewModel<SpotViewModel, SpotViewModel.Factory>(
            key = "spot-${route.wgId}",
            creationCallback = { it.create(route) },
        )
        SpotScreen(viewModel)
    }
}
