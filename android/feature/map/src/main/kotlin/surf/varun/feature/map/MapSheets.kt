package surf.varun.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import surf.varun.core.designsystem.component.InfoChip
import surf.varun.core.designsystem.component.WindArrow
import surf.varun.core.designsystem.component.WindNumber
import surf.varun.core.designsystem.component.transparentListItemColors
import surf.varun.core.designsystem.theme.Numbers
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.domain.Conditions
import surf.varun.core.domain.Countries
import surf.varun.core.domain.SpotSearch
import surf.varun.core.domain.WindReading
import surf.varun.core.model.DayLabel
import surf.varun.core.model.Spot
import surf.varun.core.ui.FavouriteButton
import surf.varun.core.ui.ForecastStrip
import surf.varun.core.ui.Format
import surf.varun.core.ui.countryName
import surf.varun.core.ui.formatTemperature
import surf.varun.core.ui.formatWind
import surf.varun.core.ui.windUnitLabel
import java.time.ZonedDateTime

/**
 * The tapped spot (spec 5.4): name, wind for the selected hour, the live reading,
 * the next days, and the ways out - open the spot, favourite, navigate.
 */
@Composable
fun SpotPanel(
    spot: Spot,
    reading: WindReading?,
    step: Int,
    favourite: Boolean,
    now: ZonedDateTime,
    onOpen: () -> Unit,
    onToggleFavourite: () -> Unit,
    onNavigate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val country = Countries.of(spot.country)
    val unit = windUnitLabel()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spot.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${country.flag} ${countryName(spot.country)}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FavouriteButton(favourite, onToggleFavourite)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.map_sheet_close)) }
        }
        Spacer(Modifier.height(8.dp))
        if (reading == null) {
            Text(stringResource(R.string.map_sheet_no_wind), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WindNumber(formatWind(reading.wind), reading.wind, style = Numbers.medium, windClass = reading.windClass)
                Text(
                    "/ ${if (reading.gusts.isNaN()) "–" else formatWind(reading.gusts)} $unit",
                    style = Numbers.medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WindArrow(reading.direction, size = 22.dp)
                Text(reading.direction?.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                reading.temp?.let { Text(formatTemperature(it), style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.weight(1f))
                if (reading.isLive) {
                    InfoChip(stringResource(R.string.map_sheet_live_now), tint = MaterialTheme.colorScheme.primary, icon = Icons.Rounded.Sensors)
                }
            }
        }
        // Away from "now", the live reading still matters: it's what is happening at the spot
        val live = spot.live
        if (step > 0 && live != null) {
            val age = Conditions.ageOf(live, Conditions.zoneOf(spot), now)
            val stale = Conditions.isStale(live, Conditions.zoneOf(spot), now)
            Spacer(Modifier.height(4.dp))
            InfoChip(
                text = listOfNotNull(
                    stringResource(R.string.map_sheet_live_now),
                    stringResource(R.string.map_wind_gusts, formatWind(live.wind.toDouble()), formatWind(live.gusts.toDouble()), unit),
                    live.direction?.name,
                    age?.let { Format.age(context, it) },
                ).joinToString(" · "),
                tint = if (stale) VarunTheme.wind.stale else MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Sensors,
            )
        }
        val days = spot.daily.filter { it.day != DayLabel.Today }.take(3)
        if (days.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ForecastStrip(days, Conditions.now(spot, now).toLocalDate())
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.map_sheet_open_spot)) }
            OutlinedButton(onClick = onNavigate) {
                Icon(Icons.Rounded.Directions, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(surf.varun.core.ui.R.string.navigate))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Countries with their spot counts and flags, searchable (spec 2.1 country filter). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySheet(
    spots: List<Spot>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val counts = remember(spots) { spots.groupingBy { it.country }.eachCount() }
    val context = LocalContext.current
    val rows = remember(counts, query) {
        val folded = SpotSearch.fold(query)
        counts.entries
            .map { (country, count) -> Triple(country, Format.countryName(context, country), count) }
            .filter { folded.isBlank() || SpotSearch.fold("${it.first} ${it.second}").contains(folded) }
            .sortedBy { it.second }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.map_country_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.map_country_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn {
            item {
                ListItem(
                    colors = countryRowColors(selected == null),
                    headlineContent = { Text(stringResource(R.string.map_filter_country_all)) },
                    trailingContent = { Text(pluralStringResource(R.plurals.map_spots_count, spots.size, spots.size)) },
                    modifier = Modifier.clickable { onSelect(null) },
                )
            }
            items(rows, key = { it.first }) { (country, name, count) ->
                ListItem(
                    colors = countryRowColors(selected == country),
                    headlineContent = { Text("${Countries.of(country).flag} $name".trim()) },
                    trailingContent = { Text(pluralStringResource(R.plurals.map_spots_count, count, count)) },
                    modifier = Modifier.clickable { onSelect(country) },
                )
            }
        }
    }
}

/**
 * The map's own spot search: the spots the map is showing, matched as the global
 * search matches them. A pick hands the spot back to the map, which flies to it
 * and opens its panel - opening the spot page from here would leave the map,
 * which is not what searching on the map is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotSearchSheet(
    spots: List<Spot>,
    onPick: (Spot) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val index = remember(spots) { SpotSearch.index(spots) }
    val results = remember(index, query) { SpotSearch.search(index, query, limit = MAX_SEARCH_RESULTS) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.map_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(onPick) }),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.map_search_clear))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
        )
        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                stringResource(R.string.map_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyColumn {
            items(results, key = { it.wgId }) { spot ->
                ListItem(
                    colors = transparentListItemColors(),
                    headlineContent = { Text(spot.name) },
                    supportingContent = { Text("${Countries.of(spot.country).flag} ${countryName(spot.country)}".trim()) },
                    leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    modifier = Modifier.clickable { onPick(spot) },
                )
            }
        }
    }
}

/** Enough to find the spot meant without the sheet turning into the spots list. */
private const val MAX_SEARCH_RESULTS = 30

/** The picked country stands out against the sheet; the rest take the sheet's colour. */
@Composable
private fun countryRowColors(selected: Boolean) =
    if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) else transparentListItemColors()
