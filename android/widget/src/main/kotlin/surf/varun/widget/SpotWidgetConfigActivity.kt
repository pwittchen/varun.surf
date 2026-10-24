package surf.varun.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.domain.SpotSearch
import surf.varun.core.model.Spot
import surf.varun.core.model.UserSettings
import surf.varun.core.ui.LocalUserSettings
import surf.varun.core.ui.countryName
import javax.inject.Inject

/**
 * Picks the spot a spot widget shows. Favourites come first; without a choice the
 * widget follows the first favourite (the provider is `configuration_optional`).
 */
@AndroidEntryPoint
class SpotWidgetConfigActivity : ComponentActivity() {
    @Inject lateinit var spotsRepository: SpotsRepository

    @Inject lateinit var favouritesRepository: FavouritesRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget as it was
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(UserSettings())
            val spots by spotsRepository.spots.collectAsStateWithLifecycle(emptyList())
            val favouriteIds by favouritesRepository.favouriteIds.collectAsStateWithLifecycle(emptyList())
            var query by rememberSaveable { mutableStateOf("") }
            val index = remember(spots) { SpotSearch.index(spots) }
            val shown = remember(spots, favouriteIds, query) {
                if (query.isBlank()) {
                    val byId = spots.associateBy { it.wgId }
                    favouriteIds.mapNotNull { byId[it] } + spots.filter { it.wgId !in favouriteIds }
                } else {
                    SpotSearch.search(index, query, limit = 50)
                }
            }

            VarunTheme(themeMode = settings.theme, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalUserSettings provides settings) {
                    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) }) { padding ->
                        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                            item {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                    placeholder = { Text(stringResource(R.string.widget_config_search)) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            items(shown, key = { it.wgId }) { spot ->
                                SpotRow(spot, spot.wgId in favouriteIds) { choose(spot) }
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SpotRow(spot: Spot, favourite: Boolean, onClick: () -> Unit) {
        ListItem(
            headlineContent = { Text(spot.name) },
            supportingContent = { Text(countryName(spot.country), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = { if (favourite) Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.tertiary) },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }

    private fun choose(spot: Spot) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@SpotWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@SpotWidgetConfigActivity, glanceId) { it[WidgetKeys.SPOT_ID] = spot.wgId }
            SpotWidget().update(this@SpotWidgetConfigActivity, glanceId)
            WidgetWork.schedule(this@SpotWidgetConfigActivity)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
