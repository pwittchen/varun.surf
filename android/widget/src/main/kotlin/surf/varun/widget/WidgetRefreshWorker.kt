package surf.varun.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.SpotsRepository
import java.time.Duration

/**
 * Refreshes what the widgets show: the spots list within the shared 15 minute
 * background budget (spec 6.6), the grid forecast of every shown spot (1 hour),
 * then redraws every widget.
 */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val spots: SpotsRepository,
    private val favourites: FavouritesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(applicationContext)
        val spotWidgets = manager.getGlanceIds(SpotWidget::class.java)
        val favouriteWidgets = manager.getGlanceIds(FavouritesWidget::class.java)
        if (spotWidgets.isEmpty() && favouriteWidgets.isEmpty()) return Result.success()

        val listResult = spots.refreshSpots(maxAge = BACKGROUND_LIST_BUDGET)
        val firstFavourite = favourites.favouriteIds.first().firstOrNull()
        val shown = spotWidgets.mapNotNull { id ->
            SpotWidget().getAppWidgetState<androidx.datastore.preferences.core.Preferences>(applicationContext, id)[WidgetKeys.SPOT_ID]
                ?: firstFavourite
        }.toSet()
        shown.forEach { spots.refreshGridForecast(it, maxAge = GRID_MAX_AGE) }

        val now = System.currentTimeMillis()
        redraw(SpotWidget(), spotWidgets, now)
        redraw(FavouritesWidget(), favouriteWidgets, now)
        return if (listResult.isFailure && runAttemptCount < 2) Result.retry() else Result.success()
    }

    private suspend fun redraw(widget: GlanceAppWidget, ids: List<androidx.glance.GlanceId>, now: Long) {
        ids.forEach { id ->
            updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply { this[WidgetKeys.REFRESHED_AT] = now }
            }
            widget.update(applicationContext, id)
        }
    }

    private companion object {
        val BACKGROUND_LIST_BUDGET: Duration = Duration.ofMinutes(15)
        val GRID_MAX_AGE: Duration = Duration.ofHours(1)
    }
}
