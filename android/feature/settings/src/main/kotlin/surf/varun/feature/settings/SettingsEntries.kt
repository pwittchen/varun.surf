package surf.varun.feature.settings

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import surf.varun.core.navigation.Route
import surf.varun.core.navigation.SettingsRoute

/** Registers the settings screen. */
fun EntryProviderScope<Route>.settingsEntries() {
    entry<SettingsRoute> { SettingsScreen(hiltViewModel()) }
}
