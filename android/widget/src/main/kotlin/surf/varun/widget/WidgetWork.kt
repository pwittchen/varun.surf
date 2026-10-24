package surf.varun.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules [WidgetRefreshWorker]: every 30 minutes while widgets exist, and on demand. */
object WidgetWork {
    private const val PERIODIC = "widget-refresh"
    private const val ONE_OFF = "widget-refresh-now"

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Called on app start; a no-op (cancels) when no widget is placed. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        if (WidgetPresence.any(app)) {
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).setConstraints(constraints).build(),
            )
        } else {
            WorkManager.getInstance(app).cancelUniqueWork(PERIODIC)
        }
    }

    /** The refresh button, a freshly configured widget. */
    fun refreshNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONE_OFF,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setConstraints(constraints).build(),
        )
    }
}
