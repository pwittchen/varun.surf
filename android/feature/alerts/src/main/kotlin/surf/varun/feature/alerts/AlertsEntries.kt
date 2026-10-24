package surf.varun.feature.alerts

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.AlertEditorRoute
import surf.varun.core.navigation.AlertsRoute
import surf.varun.core.navigation.Route

/** Registers this feature's destinations. */
fun EntryProviderScope<Route>.alertsEntries() {
    entry<AlertsRoute> { AlertsListScreen() }
    entry<AlertEditorRoute> { route ->
        AlertEditorScreen(
            viewModel = hiltViewModel<AlertEditorViewModel, AlertEditorViewModel.Factory>(
                key = route.toString(),
                creationCallback = { it.create(route) },
            ),
        )
    }
}
