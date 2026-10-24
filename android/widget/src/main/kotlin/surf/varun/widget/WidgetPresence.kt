package surf.varun.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

internal object WidgetPresence {
    fun any(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return listOf(SpotWidgetReceiver::class.java, FavouritesWidgetReceiver::class.java).any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }
}
