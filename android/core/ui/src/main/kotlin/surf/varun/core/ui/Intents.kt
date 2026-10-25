package surf.varun.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import surf.varun.core.model.Spot

/** Links out of the app: Custom Tabs, maps, the share sheet, email. */
object Intents {
    const val WEB_BASE = "https://varun.surf"
    const val CONTACT_EMAIL = "hello@varun.surf"

    fun spotWebUrl(wgId: Int) = "$WEB_BASE/spot/$wgId"

    fun openUrl(context: Context, url: String) {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Hand the spot to any maps / navigation app (Google Maps, Waze, OsmAnd...). */
    fun navigate(context: Context, spot: Spot) {
        val coordinates = spot.coordinates
        val uri: Uri = when {
            coordinates != null -> "geo:${coordinates.lat},${coordinates.lon}?q=${coordinates.lat},${coordinates.lon}(${Uri.encode(spot.name)})".toUri()
            spot.links.location != null -> spot.links.location!!.toUri()
            else -> "geo:0,0?q=${Uri.encode(spot.name)}".toUri()
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            spot.links.location?.let { openUrl(context, it) }
                ?: Toast.makeText(context, R.string.no_maps_app, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * A prefilled email to [CONTACT_EMAIL], sent by the user's own mail app. Reporting AI output
     * this way keeps the app from collecting anything itself (Play's AI-generated content policy).
     * Subject and body ride in the mailto: URI as well as the extras, since mail apps honour
     * one or the other.
     */
    fun email(context: Context, subject: String, body: String) {
        val uri = "mailto:$CONTACT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}".toUri()
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.no_email_app, CONTACT_EMAIL), Toast.LENGTH_LONG).show()
        }
    }

    /** "Jastarnia: 18 kts SW, gusts 23 - varun.surf" plus the link that opens the app or the web. */
    fun share(context: Context, spot: Spot, conditions: String?) {
        val text = buildString {
            append(context.getString(R.string.share_text, spot.name, conditions ?: Format.countryName(context, spot.country)))
            append("\n")
            append(spotWebUrl(spot.wgId))
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, spot.name)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, spot.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
