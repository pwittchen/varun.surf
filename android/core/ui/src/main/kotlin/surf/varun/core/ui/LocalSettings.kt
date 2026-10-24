package surf.varun.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import surf.varun.core.model.UserSettings

/** The user's settings, provided once at the root so every screen formats alike. */
val LocalUserSettings = staticCompositionLocalOf { UserSettings() }

/** True when the UI runs in Polish - decides `spotInfoPL`, `aiAnalysisPl` and country names. */
@Composable
@ReadOnlyComposable
fun isPolish(): Boolean = LocalConfiguration.current.locales[0]?.language == "pl"

fun android.content.Context.isPolish(): Boolean = resources.configuration.locales[0]?.language == "pl"
