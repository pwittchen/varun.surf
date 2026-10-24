package surf.varun.feature.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import surf.varun.core.model.AlertTrigger
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Keeps the alert workers in line with the alerts that exist: a live work every
 * 15 minutes only while a live alert is enabled, a forecast work every 3 hours
 * only while a forecast alert is - so a user without alerts pays no battery.
 * Called on app start and whenever alerts change.
 */
object AlertsWork {
    private const val LIVE_WORK = "wind-alerts-live"
    private const val FORECAST_WORK = "wind-alerts-forecast"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val repository = EntryPointAccessors.fromApplication(app, AlertsEntryPoint::class.java).alertsRepository()
                val enabled = repository.all().filter { it.enabled }
                apply(app, live = enabled.any { it.trigger == AlertTrigger.LIVE }, forecast = enabled.any { it.trigger == AlertTrigger.FORECAST })
            }.onFailure { Timber.w(it, "Scheduling alert workers failed") }
        }
    }

    private fun apply(context: Context, live: Boolean, forecast: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (live) enqueue(workManager, LIVE_WORK, AlertTrigger.LIVE, 15, TimeUnit.MINUTES) else workManager.cancelUniqueWork(LIVE_WORK)
        if (forecast) enqueue(workManager, FORECAST_WORK, AlertTrigger.FORECAST, 3, TimeUnit.HOURS) else workManager.cancelUniqueWork(FORECAST_WORK)
        if (live || forecast) AlertNotifier.ensureChannel(context)
    }

    private fun enqueue(workManager: WorkManager, name: String, trigger: AlertTrigger, interval: Long, unit: TimeUnit) {
        val request = PeriodicWorkRequestBuilder<AlertsWorker>(interval, unit)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(AlertsWorker.KEY_TRIGGER to trigger.name))
            .build()
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
