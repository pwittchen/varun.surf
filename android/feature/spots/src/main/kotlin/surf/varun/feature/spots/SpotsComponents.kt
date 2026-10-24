package surf.varun.feature.spots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.domain.Countries
import surf.varun.core.domain.SpotSearch
import surf.varun.core.model.Sponsor
import surf.varun.core.ui.Intents
import surf.varun.core.ui.LocalUserSettings
import surf.varun.core.ui.SpotCardModel
import surf.varun.core.ui.countryName
import surf.varun.core.ui.shareConditions
import surf.varun.core.ui.R as UiR

/** The pinned search field at the top of the list; opens the search destination. */
@Composable
fun SearchField(onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            )
            trailing?.invoke()
        }
    }
}

/** Searchable country picker with flags and spot counts (spec 2.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySheet(
    countries: ImmutableList<CountryCount>,
    selected: String?,
    total: Int,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val filtered = remember(countries, query) {
        val q = SpotSearch.fold(query.trim())
        countries
            .map { it to surf.varun.core.ui.Format.countryName(context, it.name) }
            .filter { (country, localized) -> q.isEmpty() || SpotSearch.fold("${country.name} $localized").contains(q) }
            .sortedBy { it.second }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Text(
            stringResource(R.string.country_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.country_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            if (query.isBlank()) {
                item {
                    CountryRow("🌍", stringResource(R.string.country_all), total, selected == null) { onSelect(null) }
                }
            }
            items(filtered, key = { it.first.name }) { (country, localized) ->
                CountryRow(Countries.of(country.name).flag, localized, country.count, selected == country.name) { onSelect(country.name) }
            }
        }
    }
}

@Composable
private fun CountryRow(flag: String, name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        colors = transparentListItemColors(),
        leadingContent = { Text(flag.ifEmpty { "🏳" }, style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(name) },
        supportingContent = { Text(pluralStringResource(R.plurals.spots_count, count, count)) },
        trailingContent = { if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Long-press actions on a card (spec 5.2): favourite, share, navigate, create alert. */
@Composable
fun SpotContextMenu(
    model: SpotCardModel,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onToggleFavourite: () -> Unit,
    onCreateAlert: () -> Unit,
) {
    val context = LocalContext.current
    val unit = LocalUserSettings.current.windUnit
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(if (model.favourite) UiR.string.remove_from_favourites else UiR.string.add_to_favourites)) },
            leadingIcon = { Icon(if (model.favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
            onClick = {
                onDismiss()
                onToggleFavourite()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(UiR.string.share)) },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = {
                onDismiss()
                Intents.share(context, model.spot, shareConditions(context, model.reading, unit))
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(UiR.string.navigate)) },
            leadingIcon = { Icon(Icons.Rounded.Directions, null) },
            onClick = {
                onDismiss()
                Intents.navigate(context, model.spot)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(UiR.string.create_alert)) },
            leadingIcon = { Icon(Icons.Rounded.AddAlert, null) },
            onClick = {
                onDismiss()
                onCreateAlert()
            },
        )
    }
}

/** Main sponsors, small and unobtrusive at the end of the list. */
@Composable
fun SponsorsRow(sponsors: ImmutableList<Sponsor>, modifier: Modifier = Modifier) {
    if (sponsors.isEmpty()) return
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.sponsors_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sponsors.forEach { sponsor ->
                TextButton(onClick = { sponsor.link?.let { Intents.openUrl(context, it) } }, enabled = sponsor.link != null) {
                    Text(sponsor.name)
                }
            }
        }
    }
}
