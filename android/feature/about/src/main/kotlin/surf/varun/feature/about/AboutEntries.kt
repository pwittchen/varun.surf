package surf.varun.feature.about

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.AboutRoute
import surf.varun.core.navigation.LicencesRoute
import surf.varun.core.navigation.Route

/** Registers About and the licences page. */
fun EntryProviderScope<Route>.aboutEntries() {
    entry<AboutRoute> { AboutScreen(hiltViewModel()) }
    entry<LicencesRoute> { LicencesScreen() }
}
