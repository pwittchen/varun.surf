package surf.varun.feature.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import surf.varun.core.domain.AlertEvaluator
import surf.varun.core.model.WindUnit

/** Posts wind alert notifications (channel "wind_alerts"). */
object AlertNotifier {
    const val CHANNEL_ID = "wind_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.alert_channel_description) }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** `varun://spot/{id}?focus=<spot-local ISO time>` aimed at the app's launcher activity. */
    fun spotIntent(context: Context, wgId: Int, focus: String?): Intent {
        val uri = buildString {
            append("varun://spot/").append(wgId)
            if (focus != null) append("?focus=").append(focus)
        }.toUri()
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        return Intent(Intent.ACTION_VIEW, uri).apply {
            component = launcher
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    fun post(context: Context, hit: AlertHit, unit: WindUnit) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val alertId = hit.alert.id
        val focus = (hit.match as? AlertEvaluator.Match.Window)?.start?.toString()
        val open = PendingIntent.getActivity(
            context,
            alertId.toInt(),
            spotIntent(context, hit.alert.wgId, focus),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mute = PendingIntent.getBroadcast(
            context,
            alertId.toInt(),
            Intent(context, MuteAlertReceiver::class.java)
                .setAction(MuteAlertReceiver.ACTION_MUTE)
                .putExtra(MuteAlertReceiver.EXTRA_ALERT_ID, alertId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = AlertMessages.body(context, hit, unit)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wind)
            .setContentTitle(AlertMessages.title(context, hit))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .addAction(0, context.getString(R.string.alert_mute_today), mute)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(alertId), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post
        }
    }

    fun cancel(context: Context, alertId: Long) = NotificationManagerCompat.from(context).cancel(notificationId(alertId))

    private fun notificationId(alertId: Long) = 10_000 + alertId.toInt()
}
