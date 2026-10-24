package surf.varun.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import surf.varun.core.designsystem.component.InfoChip
import surf.varun.core.designsystem.component.WindArrow
import surf.varun.core.designsystem.component.WindNumber
import surf.varun.core.designsystem.component.directionDescription
import surf.varun.core.designsystem.theme.Numbers
import surf.varun.core.designsystem.theme.VarunTheme
import surf.varun.core.designsystem.theme.forClass
import surf.varun.core.domain.Conditions
import surf.varun.core.domain.Countries
import surf.varun.core.domain.WindClass
import surf.varun.core.domain.WindReading
import surf.varun.core.model.CardDensity
import surf.varun.core.model.DayLabel
import surf.varun.core.model.ForecastEntry
import surf.varun.core.model.Spot
import java.time.ZonedDateTime

/**
 * Everything a spot card shows, computed once off the composition (stable and
 * immutable so the list skips unchanged cards).
 */
@Immutable
data class SpotCardModel(
    val spot: Spot,
    val reading: WindReading?,
    val stale: Boolean,
    val ageMinutes: Long?,
    val favourite: Boolean,
    val distanceKm: Double?,
    /** The next days of the daily forecast, "Today" excluded (the readout covers it). */
    val days: List<ForecastEntry>,
) {
    companion object {
        fun of(spot: Spot, favourite: Boolean, now: ZonedDateTime, distanceKm: Double? = null): SpotCardModel {
            val zone = Conditions.zoneOf(spot)
            val live = spot.live
            return SpotCardModel(
                spot = spot,
                reading = Conditions.current(spot, now),
                stale = live != null && Conditions.isStale(live, zone, now),
                ageMinutes = live?.let { Conditions.ageOf(it, zone, now)?.toMinutes() },
                favourite = favourite,
                distanceKm = distanceKm,
                days = spot.daily.filter { it.day != DayLabel.Today }.take(3),
            )
        }
    }
}

/** Wind and gust numbers on a card: between [Numbers.medium] and [Numbers.large]. */
private val CardWind = Numbers.large.copy(fontSize = 20.sp, lineHeight = 24.sp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotCard(
    model: SpotCardModel,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    density: CardDensity = LocalUserSettings.current.density,
    trailing: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val spot = model.spot
    val reading = model.reading
    val windClass = reading?.windClass ?: WindClass.WEAK
    val country = Countries.of(spot.country)
    val unit = windUnitLabel()
    val readingText = reading?.let {
        stringResource(
            R.string.conditions_description,
            formatWind(it.wind),
            unit,
            formatWind(it.gusts),
            directionDescription(context, it.direction),
        )
    } ?: stringResource(R.string.no_data)
    val ageText = model.ageMinutes?.let { Format.age(context, java.time.Duration.ofMinutes(it)) }
    val favouriteText = stringResource(if (model.favourite) R.string.remove_from_favourites else R.string.add_to_favourites)
    // TalkBack reads the card as one node: "Jastarnia, Poland, 18 kts gusting 23, wind from south-west, 3 min ago"
    val description = listOfNotNull(
        stringResource(R.string.spot_card_description, spot.name, countryName(spot.country), readingText),
        ageText,
    ).joinToString(", ")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = if (density == CardDensity.COMPACT) 8.dp else 12.dp, bottom = if (density == CardDensity.COMPACT) 8.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    spot.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(
                            "${country.flag} ${countryName(spot.country)}".trim(),
                            model.distanceKm?.let { Format.distanceKm(context, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Only a live reading is badged; a forecast reading is the default and needs no label
                if (reading?.isLive == true) {
                    Spacer(Modifier.height(6.dp))
                    InfoChip(
                        text = ageText ?: stringResource(R.string.live_label),
                        tint = if (model.stale) VarunTheme.wind.stale else MaterialTheme.colorScheme.primary,
                        icon = Icons.Rounded.Sensors,
                    )
                }
            }
            if (reading != null) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.clearAndSetSemantics { }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        // Wind and gusts share one size; the gusts stay muted so the wind still leads
                        WindNumber(
                            formatWind(reading.wind),
                            reading.wind,
                            style = CardWind,
                            windClass = windClass,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(" / ", style = CardWind.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
                        Text(
                            formatWind(reading.gusts),
                            style = CardWind.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(" $unit", style = Numbers.small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WindArrow(reading.direction, size = 16.dp, color = VarunTheme.wind.forClass(windClass), describe = false)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            listOfNotNull(reading.direction?.name, reading.temp?.let { formatTemperature(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                IconButton(onClick = onToggleFavourite) {
                    Icon(
                        if (model.favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = favouriteText,
                        tint = if (model.favourite) VarunTheme.wind.favourite else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (model.days.isNotEmpty() && density == CardDensity.COMFORTABLE) {
            ForecastStrip(model.days, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
        }
    }
}

/** Next days at a glance: label, wind (coloured by class) and direction (spec 5.2). */
@Composable
fun ForecastStrip(days: List<ForecastEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { day ->
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        Format.dayLabel(context, day.day, day.rawDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    WindNumber(formatWind(day.wind), day.wind, style = Numbers.tiny, windClass = WindClass.ofAverage(day.wind, day.gusts))
                    WindArrow(day.direction, size = 12.dp, animate = false)
                }
            }
        }
    }
}

/** Star toggle used outside cards (top bars, sheets). */
@Composable
fun FavouriteButton(favourite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = stringResource(if (favourite) R.string.remove_from_favourites else R.string.add_to_favourites),
            tint = if (favourite) VarunTheme.wind.favourite else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** "18 kts SW, gusts 23" for share texts and notifications. */
fun shareConditions(context: android.content.Context, reading: WindReading?, unit: surf.varun.core.model.WindUnit): String? {
    reading ?: return null
    return context.getString(
        R.string.share_text_conditions,
        Format.wind(reading.wind, unit),
        Format.windUnitLabel(context, unit),
        reading.direction?.name.orEmpty(),
        Format.wind(reading.gusts, unit),
    ).replace("  ", " ")
}
