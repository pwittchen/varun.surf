package surf.varun.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** The widgets' refresh button: one-off refresh work, which redraws when done. */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetWork.refreshNow(context)
    }
}
