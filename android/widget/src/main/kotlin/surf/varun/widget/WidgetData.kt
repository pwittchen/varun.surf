package surf.varun.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import surf.varun.core.model.TemperatureUnit
import surf.varun.core.model.WindUnit
import java.time.ZonedDateTime

/** Glance state keys, per widget instance. */
internal object WidgetKeys {
    /** The spot a spot widget shows; absent = the first favourite. */
    val SPOT_ID = intPreferencesKey("wgId")

    /** Bumped by the refresh worker so a running widget session reloads its data. */
    val REFRESHED_AT = longPreferencesKey("refreshedAt")
}

internal data class SpotWidgetData(
    val model: SpotWidgetModel?,
    val windUnit: WindUnit,
    val temperatureUnit: TemperatureUnit,
)

internal data class FavouritesWidgetData(
    val spots: List<SpotWidgetModel>,
    val windUnit: WindUnit,
)

internal object WidgetData {
    suspend fun spot(context: Context, configured: Int?): SpotWidgetData {
        val entryPoint = context.widgetEntryPoint()
        val settings = entryPoint.settingsRepository().settings.first()
        val wgId = configured ?: entryPoint.favouritesRepository().favouriteIds.first().firstOrNull()
        val spots = entryPoint.spotsRepository()
        val spot = wgId?.let { spots.spots(listOf(it)).firstOrNull() }
        val grid = if (spot != null) spots.observeGridForecast(spot.wgId).first() else emptyList()
        // Freshly placed: nothing cached for it yet, so fetch in the background
        if (wgId != null && (spot == null || grid.isEmpty())) WidgetWork.refreshNow(context)
        return SpotWidgetData(
            model = spot?.let { WidgetModels.spot(it, grid, ZonedDateTime.now()) },
            windUnit = settings.windUnit,
            temperatureUnit = settings.temperatureUnit,
        )
    }

    suspend fun favourites(context: Context): FavouritesWidgetData {
        val entryPoint = context.widgetEntryPoint()
        val ids = entryPoint.favouritesRepository().favouriteIds.first()
        val byId = entryPoint.spotsRepository().spots(ids).associateBy { it.wgId }
        val ordered = ids.mapNotNull { byId[it] }
        return FavouritesWidgetData(
            spots = WidgetModels.favourites(ordered, ZonedDateTime.now()),
            windUnit = entryPoint.settingsRepository().settings.first().windUnit,
        )
    }

    /** `varun://spot/{id}`, aimed at the app's launcher activity. */
    fun spotIntent(context: Context, wgId: Int?): Intent {
        val uri = if (wgId != null) "varun://spot/$wgId".toUri() else "varun://favourites".toUri()
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        return Intent(Intent.ACTION_VIEW, uri).apply {
            component = launcher
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}
