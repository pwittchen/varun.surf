package surf.varun.feature.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** "Mute today" on an alert notification: silences the alert until local midnight. */
class MuteAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MUTE) return
        val id = intent.getLongExtra(EXTRA_ALERT_ID, -1)
        if (id < 0) return
        AlertNotifier.cancel(context, id)
        val pending = goAsync()
        val repository = EntryPointAccessors.fromApplication(context.applicationContext, AlertsEntryPoint::class.java)
            .alertsRepository()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val zone = ZoneId.systemDefault()
                repository.mute(id, LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MUTE = "surf.varun.feature.alerts.MUTE"
        const val EXTRA_ALERT_ID = "alert_id"
    }
}
