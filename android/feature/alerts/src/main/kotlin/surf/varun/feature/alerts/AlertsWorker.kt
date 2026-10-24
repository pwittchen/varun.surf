package surf.varun.feature.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.ForecastEntry
import timber.log.Timber
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Evaluates wind alerts on the device (spec 6.6). One class, two periodic works:
 * live alerts every 15 minutes and forecast alerts every 3 hours ([KEY_TRIGGER]).
 * Reads only the cheap endpoints - the spots list, within the shared 15 minute
 * background budget, and the grid forecast - and never the paid ones.
 */
@HiltWorker
class AlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val alerts: AlertsRepository,
    private val spots: SpotsRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val trigger = inputData.getString(KEY_TRIGGER)?.let { runCatching { AlertTrigger.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val enabled = alerts.all().filter { it.enabled && it.trigger == trigger }
        if (enabled.isEmpty()) return Result.success()

        val now = ZonedDateTime.now()
        val ids = enabled.map { it.wgId }.toSet()
        val hits = when (trigger) {
            AlertTrigger.LIVE -> {
                spots.refreshSpots(maxAge = BACKGROUND_LIST_BUDGET)
                AlertChecker.live(enabled, spots.spots(ids).associateBy { it.wgId }, now)
            }
            AlertTrigger.FORECAST -> {
                val hours = mutableMapOf<Int, List<ForecastEntry>>()
                ids.forEach { id ->
                    spots.refreshGridForecast(id, maxAge = GRID_MAX_AGE)
                        .onSuccess { hours[id] = it }
                        .onFailure { Timber.w(it, "Grid forecast for %d failed", id) }
                }
                AlertChecker.forecast(enabled, hours, spots.spots(ids).associateBy { it.wgId }, now)
            }
        }
        if (hits.isEmpty()) return Result.success()

        val unit = settings.settings.first().windUnit
        hits.forEach { hit ->
            AlertNotifier.post(applicationContext, hit, unit)
            alerts.markFired(hit.alert.id, now.toInstant())
        }
        return Result.success()
    }

    companion object {
        const val KEY_TRIGGER = "trigger"

        /** At most one `/api/v1/spots` per 15 minutes from background work, across all workers. */
        val BACKGROUND_LIST_BUDGET: Duration = Duration.ofMinutes(15)
        val GRID_MAX_AGE: Duration = Duration.ofHours(1)
    }
}
