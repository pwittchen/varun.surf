package surf.varun.feature.spot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import surf.varun.core.model.Spot
import surf.varun.core.ui.Intents
import surf.varun.core.ui.localizedInfo
import java.util.Locale
import surf.varun.core.ui.R as CoreUiR

/** Section 5: `spotInfo` / `spotInfoPL` in the app language. */
@Composable
internal fun AboutSection(spot: Spot) {
    val info = spot.localizedInfo() ?: return
    SectionCard(stringResource(R.string.section_about)) {
        info.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
        InfoRow(Icons.Rounded.Waves, R.string.info_type, info.type)
        InfoRow(Icons.Rounded.Air, R.string.info_best_wind, info.bestWind)
        InfoRow(Icons.Rounded.Thermostat, R.string.info_water_temp, info.waterTemp)
        InfoRow(Icons.Rounded.School, R.string.info_experience, info.experience)
        InfoRow(Icons.Rounded.BeachAccess, R.string.info_launch, info.launch)
        InfoRow(Icons.Rounded.Warning, R.string.info_hazards, info.hazards)
        InfoRow(Icons.Rounded.CalendarMonth, R.string.info_season, info.season)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: Int, value: String?) {
    value ?: return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Section 6: static map, copyable coordinates, "Navigate". */
@Composable
internal fun LocationSection(spot: Spot) {
    val coordinates = spot.coordinates ?: return
    val context = LocalContext.current
    val text = String.format(Locale.US, "%.5f, %.5f", coordinates.lat, coordinates.lon)
    SectionCard(stringResource(R.string.section_location)) {
        MiniMap(spot)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.coordinates), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { copy(context, text) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy_coordinates))
            }
        }
        Button(onClick = { Intents.navigate(context, spot) }) {
            Icon(Icons.Rounded.Directions, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(CoreUiR.string.navigate))
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
    // Android 13+ shows its own confirmation
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.coordinates_copied, Toast.LENGTH_SHORT).show()
    }
}

/** Section 7: Windguru, Windfinder, ICM meteogram, webcam in Custom Tabs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LinksSection(spot: Spot) {
    val context = LocalContext.current
    val links = listOfNotNull(
        spot.links.windguru?.let { R.string.link_windguru to it },
        spot.links.windfinder?.let { R.string.link_windfinder to it },
        spot.links.icm?.let { R.string.link_icm to it },
        spot.links.webcam?.let { R.string.link_webcam to it },
        spot.links.location?.let { R.string.link_location to it },
    )
    if (links.isEmpty()) return
    SectionCard(stringResource(R.string.section_links)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { (label, url) ->
                OutlinedButton(onClick = { Intents.openUrl(context, url) }) {
                    Icon(if (label == R.string.link_webcam) Icons.Rounded.Videocam else Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(label))
                }
            }
        }
    }
}

/** Section 8: the spot's own sponsors. */
@Composable
internal fun SponsorsSection(spot: Spot) {
    val context = LocalContext.current
    SectionCard(stringResource(R.string.section_sponsors)) {
        spot.sponsors.forEach { sponsor ->
            if (sponsor.link != null) {
                TextButton(onClick = { Intents.openUrl(context, sponsor.link!!) }) { Text(sponsor.name) }
            } else {
                Text(sponsor.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

/** Footer: when the server last refreshed the spot, and a way out to the web. */
@Composable
internal fun Footer(spot: Spot) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        spot.lastUpdated?.let {
            Text(
                stringResource(R.string.data_updated, it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { Intents.openUrl(context, Intents.spotWebUrl(spot.wgId)) }) {
            Text(stringResource(CoreUiR.string.open_on_web))
        }
    }
}
