package surf.varun.feature.search

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.SearchRoute

/** Registers the global search destination. */
fun EntryProviderScope<Route>.searchEntries() {
    entry<SearchRoute> { SearchScreen(hiltViewModel()) }
}
