package surf.varun.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import surf.varun.app.R
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.navigation.AboutRoute
import surf.varun.core.navigation.AlertsRoute
import surf.varun.core.navigation.CalculatorRoute
import surf.varun.core.navigation.LocalNavigator
import surf.varun.core.navigation.SearchRoute
import surf.varun.core.navigation.SettingsRoute

/** The More tab: tools and app pages (spec 5.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen() {
    val navigator = LocalNavigator.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_more)) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { MoreItem(Icons.Rounded.Calculate, R.string.more_calculator, R.string.more_calculator_summary) { navigator.navigate(CalculatorRoute()) } }
            item { MoreItem(Icons.Rounded.NotificationsActive, R.string.more_alerts, R.string.more_alerts_summary) { navigator.navigate(AlertsRoute) } }
            item { MoreItem(Icons.Rounded.Search, R.string.more_search, R.string.more_search_summary) { navigator.navigate(SearchRoute) } }
            item { MoreItem(Icons.Rounded.Settings, R.string.more_settings, R.string.more_settings_summary) { navigator.navigate(SettingsRoute) } }
            item { MoreItem(Icons.Rounded.Info, R.string.more_about, R.string.more_about_summary) { navigator.navigate(AboutRoute) } }
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: Int, summary: Int, onClick: () -> Unit) {
    ListItem(
        colors = transparentListItemColors(),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

