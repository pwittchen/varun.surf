package surf.varun.feature.calculator

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.CalculatorRoute
import surf.varun.core.navigation.Route

/** Registers the kite & board calculator. */
fun EntryProviderScope<Route>.calculatorEntries() {
    entry<CalculatorRoute> { route ->
        CalculatorScreen(hiltViewModel<CalculatorViewModel, CalculatorViewModel.Factory>(creationCallback = { it.create(route) }))
    }
}
